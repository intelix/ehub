/*
 * Copyright 2014-15 Intelix Pty Ltd
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

package eventstreams.instructions

import _root_.core.sysevents.SyseventOps.symbolToSyseventOps
import _root_.core.sysevents.WithSyseventPublisher
import _root_.core.sysevents.ref.ComponentWithBaseSysevents
import eventstreams.Tools.{configHelper, _}
import eventstreams._
import eventstreams.instructions.Types.SimpleInstructionType
import play.api.libs.json._

import scala.annotation.tailrec
import scala.util.Try
import scala.util.matching.Regex
import scalaz.Scalaz._
import scalaz._


trait SplitInstructionSysevents extends ComponentWithBaseSysevents {

  val Built = 'Built.trace
  val Split = 'Split.trace

  override def componentId: String = "Instruction.Split"
}

trait SplitInstructionConstants extends InstructionConstants with SplitInstructionSysevents {
  val CfgFSource = "source"
  val CfgFPattern = "pattern"
  val CfgFKeepOriginal = "keepOriginalEvent"
  val CfgFAdditionalTags = "additionalTags"
  val CfgFIndex = "index"
  val CfgFTable = "table"
  val CfgFTTL = "ttl"

}

object SplitInstructionConstants extends SplitInstructionConstants

class SplitInstruction extends SimpleInstructionBuilder with NowProvider with SplitInstructionConstants with WithSyseventPublisher {
  val configId = "split"

  override def simpleInstruction(props: JsValue, id: Option[String] = None): \/[Fail, SimpleInstructionType] =
    for (
      source <- props ~> CfgFSource orFail
        s"Invalid $configId instruction. Missing '$CfgFSource' value. Contents: ${Json.stringify(props)}";
      patternString <- (props ~> CfgFPattern) orFail
        s"Invalid $configId instruction. Missing '$CfgFPattern' value. Contents: ${Json.stringify(props)}";
      pattern <- Try(new Regex(patternString)).toOption orFail
        s"Invalid $configId instruction. Invalid '$CfgFPattern' value. Contents: ${Json.stringify(props)}"
    ) yield {

      var sequence: Long = 0
      var remainder: Option[String] = None

      @tailrec
      def ext(list: List[String], remainder: Option[String]): (List[String], Option[String]) =
        remainder.flatMap(pattern.findFirstMatchIn(_)) match {
          case None => (list, remainder)
          case Some(m) => ext(list :+ m.group(1), Some(m.group(2)))
        }


      val uuid = UUIDTools.generateShortUUID

      Built >>('Config -> Json.stringify(props), 'InstructionInstanceId -> uuid)

      fr: EventFrame => {

        val sourceId = fr.eventId | "!" + UUIDTools.generateShortUUID
        val eventSeq = fr ++> 'eventSeq | now

        val baseEventSeq = eventSeq << 16

        val keepOriginalEvent = props ?> CfgFKeepOriginal | false
        val additionalTags = (props ~> CfgFAdditionalTags | "").split(",").map(_.trim)

        val index = props ~> CfgFIndex | "${index}"
        val table = props ~> CfgFTable | "${table}"
        val ttl = props ~> CfgFTTL | "${_ttl}"


        val sourceField = macroReplacement(fr, JsString(source))


        sequence = sequence + 1

        val str = Some(remainder.getOrElse("") + locateFieldValue(fr, sourceField))

        val (resultList, newRemainder) = ext(List(), str)

        remainder = newRemainder

        val remainderLength = remainder match {
          case None => 0
          case Some(v) => v
        }

        var counter = 0

        val originalEventId = fr.eventIdOrNA

        Split >>> Seq(
          'Sequence -> sequence,
          'Events -> resultList.size,
          'Remainder -> remainderLength,
          'KeepOriginal -> keepOriginalEvent,
          'EventId -> originalEventId,
          'InstructionInstanceId -> uuid
          )

        val result = resultList.map(_.trim).filter(!_.isEmpty).map { value =>

          var event = setValue("s", value, sourceField, fr) +
            ("eventId" -> (sourceId + ":" + counter)) +
            ("eventSeq" -> (baseEventSeq + counter)) +
            ("splitSequence" -> sequence) +
            ("index" -> macroReplacement(fr, index)) +
            ("table" -> macroReplacement(fr, table)) +
            ("_ttl" -> macroReplacement(fr, ttl))

          additionalTags.foreach { tag =>
            event = setValue("as", tag, "tags", event)
          }

          counter = counter + 1

          event

        }

        if (keepOriginalEvent) result :+ fr else result

      }
    }


}