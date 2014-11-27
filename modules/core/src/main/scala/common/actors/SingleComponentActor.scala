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

package common.actors

import akka.actor.ActorRef

import common.{Fail, OK}
import hq.routing.MessageRouterActor
import hq._
import play.api.libs.json.{JsString, Json, JsValue}

import scala.util.{Success, Failure, Try}
import scalaz.{-\/, \/-, \/}

trait SingleComponentActor
  extends ActorWithLocalSubscribers {

  val T_ADD = TopicKey("add")
  val T_EDIT = TopicKey("edit")
  val T_LIST = TopicKey("list")
  val T_INFO = TopicKey("info")
  val T_PROPS = TopicKey("props")
  val T_START = TopicKey("start")
  val T_STOP = TopicKey("stop")
  val T_KILL = TopicKey("kill")
  val T_RESET = TopicKey("reset")
  val T_UPDATE_PROPS = TopicKey("update_props")


  def key: ComponentKey

  override def preStart(): Unit = {
    MessageRouterActor.path ! RegisterComponent(key, self)
    super.preStart()
  }

  def topicUpdate(topic: TopicKey, data: Option[JsValue], singleTarget: Option[ActorRef] = None) =
    singleTarget match {
      case Some(ref) => updateTo(LocalSubj(key, topic), ref, data)
      case None => updateToAll(LocalSubj(key, topic), data)
    }

  def genericCommandError(cmdTopicKey: TopicKey, replyToSubj: Option[Any], errorMessage: String, singleTarget: ActorRef = sender()) = {
    logger.info(s"Command failed $cmdTopicKey, msg: $errorMessage")
    replyToSubj.foreach(
      cmdErrTo(_, singleTarget, Json.obj(
        "error" -> Json.obj(
          "key" -> cmdTopicKey.key,
          "msg" -> errorMessage
        ))))
  }

  def genericCommandSuccess(cmdTopicKey: TopicKey, replyToSubj: Option[Any], message: Option[String], singleTarget: ActorRef = sender()) = {
    logger.info(s"Command executed successfully $cmdTopicKey, msg: $message")
    replyToSubj.foreach(
      cmdOkTo(_, singleTarget, Json.obj(
        "ok" -> Json.obj(
          "key" -> cmdTopicKey.key,
          "msg" -> JsString(message.getOrElse(""))
        ))))
  }

  def processTopicSubscribe(sourceRef: ActorRef, topic: TopicKey): Unit = {}

  def processTopicUnsubscribe(sourceRef: ActorRef, topic: TopicKey): Unit = {}

  def processTopicCommand(sourceRef: ActorRef, topic: TopicKey, replyToSubj: Option[Any], maybeData: Option[JsValue]): \/[Fail, OK] = \/-(OK())

  override def processSubscribeRequest(sourceRef: ActorRef, subject: LocalSubj): Unit = Try(processTopicSubscribe(sourceRef, subject.topic)) match {
    case Failure(failure) =>
      logger.error(s"Error while subscribing to $subject", failure)
    case Success(_) => ()
  }

  override def processUnsubscribeRequest(sourceRef: ActorRef, subject: LocalSubj): Unit = Try(processTopicUnsubscribe(sourceRef, subject.topic)) match {
    case Failure(failure) =>
      logger.error(s"Error while unsubscribing from $subject", failure)
    case Success(_) => ()
  }

  override def processCommand(sourceRef: ActorRef, subject: LocalSubj, replyToSubj: Option[Any], maybeData: Option[JsValue]): Unit = {
    logger.info(s"!>>>> received command for ${subject.topic} from $sourceRef reply to $replyToSubj")

    Try(processTopicCommand(sourceRef, subject.topic, replyToSubj, maybeData)) match {
      case Failure(failure) =>
        logger.error(s"Error while executing command $subject with $maybeData", failure)
        genericCommandError(subject.topic, replyToSubj, "Invalid operation")
      case Success(result) => result match {
        case -\/(fail) =>
          fail.message.foreach { msg =>
            genericCommandError(subject.topic, replyToSubj, msg)
          }
          logger.debug(s"Command ${subject.topic} failed: $fail")
        case \/-(ok) =>
          ok.message.foreach { msg =>
            genericCommandSuccess(subject.topic, replyToSubj, Some(msg))
          }
          logger.debug(s"Command ${subject.topic} succeeded: $ok")
      }
    }
  }
}
