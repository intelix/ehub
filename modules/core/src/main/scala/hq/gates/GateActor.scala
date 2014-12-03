/*
 * Copyright 2014 Intelix Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package hq.gates

import java.util.UUID

import agent.flavors.files.{Cursor, ProducedMessage}
import agent.shared._
import akka.actor._
import akka.util.ByteString
import common.ToolExt.configHelper
import common._
import common.actors._
import hq._
import play.api.libs.json.extensions._
import play.api.libs.json.{JsNumber, JsValue, Json, _}

import scala.collection.mutable
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scalaz.Scalaz._

object GateActor {
  def props(id: String) = Props(new GateActor(id))

  def start(id: String)(implicit f: ActorRefFactory) = f.actorOf(props(id), ActorTools.actorFriendlyId(id))
}


case class RegisterSink(sinkRef: ActorRef)

private case class InflightMessage(originalCorrelationId: Long, originator: ActorRef, retentionPending: Boolean, deliveryPending: Boolean)


class GateActor(id: String)
  extends PipelineWithStatesActor
  with AtLeastOnceDeliveryActor[JsonFrame]
  with ActorWithConfigStore
  with SingleComponentActor {


  private val correlationToOrigin: mutable.Map[Long, InflightMessage] = mutable.Map()
  var name = "default"
  var address = uuid.toString
  var initialState = "Closed"
  var retentionStorageKey = "default"
  var created = prettyTimeFormat(now)
  var maxInFlight = 10
  var retentionPolicy: RetentionPolicy = new RetentionPolicyNone()
  var overflowPolicy: OverflowPolicy = new OverflowPolicyBackpressure()
  private var sinks: Set[ActorRef] = Set()
  private var forwarderId: Option[String] = None
  private var forwarderActor: Option[ActorRef] = None

  override def configUnacknowledgedMessagesResendInterval: FiniteDuration = 10.seconds

  override def key = ComponentKey(id)


  override def storageKey: Option[String] = Some(id)

  override def preStart(): Unit = {

    if (isPipelineActive)
      switchToCustomBehavior(flowMessagesHandlerForOpenGate)
    else
      switchToCustomBehavior(flowMessagesHandlerForClosedGate)

    super.preStart()

  }


  override def postStop(): Unit = {
    forwarderActor.foreach(context.system.stop)
    super.postStop()
  }

  override def onInitialConfigApplied(): Unit = context.parent ! GateAvailable(key)


  override def commonBehavior: Actor.Receive = messageHandler orElse super.commonBehavior

  override def becomeActive(): Unit = {
    topicUpdate(T_INFO, info)
    switchToCustomBehavior(flowMessagesHandlerForOpenGate)
  }

  override def becomePassive(): Unit = {
    topicUpdate(T_INFO, info)
    switchToCustomBehavior(flowMessagesHandlerForClosedGate)
  }

  def info = propsConfig.map { cfg =>
    Json.obj(
      "name" -> name,
      "address" -> address,
      "initial" -> initialState,
      "overflow" -> overflowPolicy.info,
      "retention" -> retentionPolicy.info,
      "sinceStateChange" -> prettyTimeSinceStateChange,
      "created" -> created,
      "state" -> (if (isPipelineActive) "active" else "passive")
    )
  }

  override def processTopicSubscribe(ref: ActorRef, topic: TopicKey) = topic match {
    case T_INFO => topicUpdate(T_INFO, info, singleTarget = Some(ref))
    case T_PROPS => topicUpdate(T_PROPS, propsConfig, singleTarget = Some(ref))
    case TopicKey(x) => logger.debug(s"Unknown topic $x")
  }

  override def processTopicCommand(ref: ActorRef, topic: TopicKey, replyToSubj: Option[Any], maybeData: Option[JsValue]) = topic match {
    case T_STOP =>
      lastRequestedState match {
        case Some(Active()) =>
          logger.info("Stopping the gate")
          self ! BecomePassive()
          OK().right
        case _ =>
          logger.info("Already stopped")
          Fail(message = Some("Already stopped")).left
      }
    case T_START =>
      lastRequestedState match {
        case Some(Active()) =>
          logger.info("Already started")
          Fail(message = Some("Already started")).left
        case _ =>
          logger.info("Starting the gate " + self.toString())
          self ! BecomeActive()
          OK().right
      }
    case T_KILL =>
      removeConfig()
      self ! PoisonPill
      OK().right
    case T_UPDATE_PROPS =>
      for (
        data <- maybeData \/> Fail("Invalid request");
        result <- updateConfigProps(data)
      ) yield result
  }

  override def canDeliverDownstreamRightNow: Boolean = isPipelineActive

  override def fullyAcknowledged(correlationId: Long, msg: JsonFrame): Unit = {
    logger.info(s"Delivered to all active sinks $correlationId ")
    correlationToOrigin.get(correlationId).foreach { origin =>
      correlationToOrigin += correlationId -> origin.copy(deliveryPending = false)
      checkCompleteness(correlationId)
    }
  }

  override def getSetOfActiveEndpoints: Set[ActorRef] = sinks


  def convertInboundPayload(id: Long, message: Any): Option[JsonFrame] = message match {
    case m@ProducedMessage(MessageWithAttachments(bs: ByteString, attachments), c: Cursor) =>
      logger.debug(s"Original message at the gate: ${bs.utf8String}")
      val json = attachments.set(__ \ "value" -> JsString(bs.utf8String)).set(__ \ "id" -> JsNumber(id))
      Some(JsonFrame(json,
        ctx = Map[String, JsValue]("source.id" -> JsNumber(id))))
    case m: JsonFrame =>
      // TODO add route slip to the context
      Some(m)
    case x =>
      logger.warn(s"Unsupported message type at the gate  $id: $x")
      None
  }

  override def applyConfig(key: String, props: JsValue, maybeState: Option[JsValue]): Unit = {
    name = propsConfig ~> 'name | "default"
    initialState = propsConfig ~> 'initialState | "Closed"
    maxInFlight = propsConfig +> 'maxInFlight | 10
    address = props ~> 'address | key
    retentionStorageKey = props ~> 'storageKey | "default"
    created = prettyTimeFormat(props ++> 'created | now)
    overflowPolicy = OverflowPolicyBuilder(propsConfig #> 'overflowPolicy)
    retentionPolicy = RetentionPolicyBuilder(propsConfig #> 'retentionPolicy)

    val newForwarderId = ActorTools.actorFriendlyId(address)
    forwarderId match {
      case None => reopenForwarder(newForwarderId)
      case Some(x) if x != newForwarderId => reopenForwarder(newForwarderId)
      case x => ()
    }

  }

  override def afterApplyConfig(): Unit = {
    topicUpdate(T_INFO, info)
    topicUpdate(T_PROPS, propsConfig)
  }

  private def reopenForwarder(newForwarderId: String) = {
    forwarderId = Some(newForwarderId)
    forwarderActor.foreach(context.system.stop)
    forwarderActor = Some(context.system.actorOf(Props(new Forwarder(self)), newForwarderId))
    logger.info(s"Gate forwarder started for $newForwarderId, actor $forwarderActor")
  }

  private def flowMessagesHandlerForClosedGate: Receive = {
    case m: Acknowledgeable[_] =>
      forwarderActor.foreach(_ ! RouteTo(sender(), GateStateUpdate(GateClosed())))
  }

  private def canAcceptAnotherMessage = correlationToOrigin.size < maxInFlight

  private def isDup(m: Acknowledgeable[_], sender: ActorRef) = correlationToOrigin.exists {
    case (_, InflightMessage(originalCorrelationId, ref, _, _)) =>
      originalCorrelationId == m.id && ref == sender
  }

  private def flowMessagesHandlerForOpenGate: Receive = {
    case m: Acknowledgeable[_] =>
      if (canAcceptAnotherMessage) {
        logger.info(s"New message arrived at the gate $id ... ${m.id}")
        if (!isDup(m, sender())) {
          convertInboundPayload(m.id, m.msg) foreach { msg =>
            val correlationId = deliverMessage(msg)
            correlationToOrigin += correlationId ->
              InflightMessage(
                m.id,
                sender(),
                retentionPending = retentionPolicy.scheduleRetention(
                  correlationId, msg.event ~> 'id | shortUUID, retentionStorageKey, msg.event),
                deliveryPending = true)
          }
        } else {
          logger.info(s"Received duplicate message $id ${m.id}")
        }
      } else {
        logger.debug(s"Unable to accept another message, in flight count $inFlightCount")
      }
  }

  private def checkCompleteness(correlationId: Long) = correlationToOrigin.get(correlationId).foreach { m =>
    if (!m.deliveryPending && !m.retentionPending) {
      logger.info(s"Fully acknowledged $correlationId ")
      logger.info(s"Ack ${m.originalCorrelationId} with tap at ${m.originator}")
      forwarderActor.foreach(_ ! RouteTo(m.originator, Acknowledge(m.originalCorrelationId)))
      correlationToOrigin -= correlationId
    }
  }

  private def messageHandler: Receive = {
    case GateStateCheck(ref) =>
      logger.debug(s"Received state check from $ref, our state: $isPipelineActive")
      if (isPipelineActive) {
        forwarderActor.foreach(_ ! RouteTo(ref, GateStateUpdate(GateOpen())))
      } else {
        forwarderActor.foreach(_ ! RouteTo(ref, GateStateUpdate(GateClosed())))
      }
    case Terminated(ref) if sinks.contains(ref) =>
      logger.info(s"Sink is gone: $ref")
      sinks -= ref
    case RegisterSink(sinkRef) =>
      sinks += sender()
      context.watch(sinkRef)
      logger.info(s"New sink: ${sender()}")
    case MessageStored(correlationId) =>
      logger.debug(s"Message $correlationId stored")
      correlationToOrigin.get(correlationId).foreach { m =>
        correlationToOrigin += correlationId -> m.copy(retentionPending = false)
        checkCompleteness(correlationId)
      }
  }


  trait RetentionPolicy {
    def scheduleRetention(correlationId: Long, id: String, key: String, m: JsValue): Boolean

    def info: String
  }

  trait OverflowPolicy {
    def info: String
  }

  class RetentionPolicyNone extends RetentionPolicy {
    override def info: String = "None"

    def scheduleRetention(correlationId: Long, id: String, key: String, m: JsValue): Boolean = false

  }

  class RetentionPolicyDays(val count: Int) extends RetentionPolicy {
    override def info: String = s"$count day(s)"

    def scheduleRetention(correlationId: Long, id: String, key: String, m: JsValue): Boolean = {
      RetentionManagerActor.path ! ScheduleStorage(self, correlationId, key, id, m)
      true
    }
  }

  class RetentionPolicyCount(val count: Int) extends RetentionPolicy {
    override def info: String = s"$count event(s)"

    def scheduleRetention(correlationId: Long, id: String, key: String, m: JsValue): Boolean = {
      RetentionManagerActor.path ! ScheduleStorage(self, correlationId, key, id, m)
      true
    }
  }

  class OverflowPolicyBackpressure extends OverflowPolicy {
    override def info: String = "Backpressure"
  }

  class OverflowPolicyDrop(val dropOldest: Boolean) extends OverflowPolicy {
    override def info: String = "Drop"
  }

  object RetentionPolicyBuilder {
    def apply(j: Option[JsValue]) = j ~> 'type match {
      case Some("days") => new RetentionPolicyDays(j +> 'count | 1)
      case Some("count") => new RetentionPolicyCount(j +> 'count | 1000)
      case _ => new RetentionPolicyNone()
    }
  }

  object OverflowPolicyBuilder {
    def apply(j: Option[JsValue]) = j ~> 'type match {
      case Some("drop") => j ~> 'policy match {
        case Some(x) if x.toLowerCase == "latest" => new OverflowPolicyDrop(false)
        case _ => new OverflowPolicyDrop(true)
      }
      case _ => new OverflowPolicyBackpressure()
    }
  }


}

case class RouteTo(ref: ActorRef, msg: Any)

class Forwarder(forwardTo: ActorRef) extends ActorWithComposableBehavior {
  override def commonBehavior: Receive = {
    case RouteTo(ref, msg) => ref ! msg
    case x => forwardTo.forward(x)
  }
}

