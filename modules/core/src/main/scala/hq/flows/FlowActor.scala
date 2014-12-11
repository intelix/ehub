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

package hq.flows

import akka.actor._
import akka.stream.FlowMaterializer
import akka.stream.actor.{ActorPublisher, ActorSubscriber}
import akka.stream.scaladsl._
import common.ToolExt.configHelper
import common._
import common.actors._
import hq._
import hq.flows.core.{Builder, FlowComponents}
import nl.grons.metrics.scala.MetricName
import play.api.libs.json.{JsValue, Json}

import scalaz.Scalaz._
import scalaz.{-\/, \/-}

object FlowActor {
  def props(id: String) = Props(new FlowActor(id))

  def start(id: String)(implicit f: ActorRefFactory) = f.actorOf(props(id), ActorTools.actorFriendlyId(id))
}


sealed trait FlowState {
  def details: Option[String]
}

case class FlowStateUnknown(details: Option[String] = None) extends FlowState

case class FlowStateActive(details: Option[String] = None) extends FlowState

case class FlowStatePassive(details: Option[String] = None) extends FlowState

case class FlowStateError(details: Option[String] = None) extends FlowState


class FlowActor(id: String)
  extends PipelineWithStatesActor
  with ActorWithConfigStore
  with SingleComponentActor
  with ActorWithPeriodicalBroadcasting
  with WithMetrics {

  implicit val mat = FlowMaterializer()
  implicit val dispatcher = context.system.dispatcher


  private var tapActor: Option[ActorRef] = None
  private var sinkActor: Option[ActorRef] = None
  private var flowActors: Option[Seq[ActorRef]] = None
  private var flow: Option[MaterializedMap] = None


  override lazy val metricBaseName: MetricName = MetricName("flow")

  val _inrate = metrics.meter(s"$id.source")
  val _outrate = metrics.meter(s"$id.sink")


  var name = "default"
  var initialState = "Closed"
  var created = prettyTimeFormat(now)
  var currentState: FlowState = FlowStateUnknown(Some("Initialising"))


  override def storageKey: Option[String] = Some(id)

  override def key = ComponentKey(id)

  override def onInitialConfigApplied(): Unit = context.parent ! FlowAvailable(key)

  override def commonBehavior: Actor.Receive = super.commonBehavior


  def publishInfo() = {
    T_INFO !! info
    T_STATS !! infoDynamic
  }

  def publishProps() = T_PROPS !! propsConfig

  def stateDetailsAsString = currentState.details match {
    case Some(v) => stateAsString + " - " + v
    case _ => stateAsString
  }

  def stateAsString = currentState match {
    case FlowStateUnknown(_) => "unknown"
    case FlowStateActive(_) => "active"
    case FlowStatePassive(_) => "passive"
    case FlowStateError(_) => "error"
  }


  def info = Some(Json.obj(
    "name" -> name,
    "initial" -> initialState,
    "sinceStateChange" -> prettyTimeSinceStateChange,
    "created" -> created,
    "state" -> stateAsString,
    "stateDetails" -> stateDetailsAsString
  ))

  def infoDynamic = currentState match {
    case FlowStateActive(_) => Some(Json.obj(
      "inrate" -> ("%.2f" format _inrate.oneMinuteRate),
      "outrate" -> ("%.2f" format _outrate.oneMinuteRate)
    ))
    case _ => Some(Json.obj())
  }


  override def becomeActive(): Unit = {
    openFlow()
    publishInfo()
  }

  override def becomePassive(): Unit = {
    closeFlow()
    publishInfo()
  }

  override def processTopicSubscribe(ref: ActorRef, topic: TopicKey) = topic match {
    case T_INFO => publishInfo()
    case T_PROPS => publishProps()
    case T_STATS => publishInfo()
  }

  override def autoBroadcast: List[(Key, Int, PayloadGenerator, PayloadBroadcaster)] = List(
    (T_STATS, 5, () => infoDynamic, T_STATS !! _)
  )

  override def processTopicCommand(ref: ActorRef, topic: TopicKey, replyToSubj: Option[Any], maybeData: Option[JsValue]) = topic match {
    case T_STOP =>
      lastRequestedState match {
        case Some(Active()) =>
          logger.info("Stopping the flow")
          self ! BecomePassive()
          \/-(OK())
        case _ =>
          logger.info("Already stopped")
          -\/(Fail("Already stopped"))
      }
    case T_START =>
      lastRequestedState match {
        case Some(Active()) =>
          logger.info("Already started")
          -\/(Fail("Already started"))
        case _ =>
          logger.info("Starting the flow " + self.toString())
          self ! BecomeActive()
          \/-(OK())
      }
    case T_KILL =>
      terminateFlow(Some("Flow being deleted"))
      removeConfig()
      self ! PoisonPill
      \/-(OK())
    case T_UPDATE_PROPS =>
      for (
        data <- maybeData \/> Fail("No data");
        result <- updateAndApplyConfigProps(data)
      ) yield result
  }

  def closeFlow() = {
    currentState = FlowStatePassive()

    logger.debug(s"Tap closed")
    tapActor.foreach(_ ! BecomePassive())
    flowActors.foreach(_.foreach(_ ! BecomePassive()))
  }

  override def applyConfig(key: String, config: JsValue, maybeState: Option[JsValue]): Unit = {

    name = config ~> 'name | "default"
    initialState = config ~> 'initialState | "Closed"
    created = prettyTimeFormat(config ++> 'created | now)


    terminateFlow(Some("Applying new configuration"))

    Builder()(config, context, id) match {
      case -\/(fail) =>
        currentState = FlowStateError(fail.message)

        logger.info(s"Unable to build flow $id: failed with $fail")
      case \/-(FlowComponents(tap, pipeline, sink)) =>
        resetFlowWith(tap, pipeline, sink)
    }
  }


  override def afterApplyConfig(): Unit = {
    publishProps()
    publishInfo()
  }

  private def terminateFlow(reason: Option[String]) = {
    closeFlow()
    tapActor.foreach(_ ! Stop(reason))
    tapActor = None
    sinkActor = None
    flowActors = None
    flow = None
  }

  private def openFlow() = {
    logger.debug(s"Tap opened")
    currentState = FlowStateActive(Some("ok"))

    flowActors.foreach(_.foreach(_ ! BecomeActive()))
    sinkActor.foreach(_ ! BecomeActive())
    tapActor.foreach(_ ! BecomeActive())
  }

  private def propsToActors(list: Seq[Props]) = list map context.actorOf

  private def resetFlowWith(tapProps: Props, pipeline: Seq[Props], sinkProps: Props) = {
    logger.debug(s"Resetting flow [$id]: tapProps: $tapProps, pipeline: $pipeline, sinkProps: $sinkProps")

    val tapA = context.actorOf(tapProps)
    val sinkA = context.actorOf(sinkProps)

    val pubSrc = PublisherSource[JsonFrame](ActorPublisher[JsonFrame](tapA))
    val subSink = SubscriberSink(ActorSubscriber[JsonFrame](sinkA))

    val pipelineActors = propsToActors(pipeline)




    val flowPipeline = pipelineActors.foldRight[Sink[JsonFrame]](subSink) { (actor, sink) =>
      val s = SubscriberSink(ActorSubscriber[JsonFrame](actor))
      val p = PublisherSource[JsonFrame](ActorPublisher[JsonFrame](actor))
      p.to(sink).run()
      s
    }

    flow = Some(pubSrc.to(flowPipeline).run())

    tapActor = Some(tapA)
    sinkActor = Some(sinkA)
    flowActors = Some(pipelineActors)

    if (isPipelineActive) openFlow()
  }


}

