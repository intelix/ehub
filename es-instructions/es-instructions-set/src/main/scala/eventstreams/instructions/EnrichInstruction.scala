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

import core.sysevents.SyseventOps.symbolToSyseventOps
import core.sysevents.WithSyseventPublisher
import core.sysevents.ref.ComponentWithBaseSysevents
import eventstreams.Tools.{configHelper, _}
import eventstreams.instructions.Types.SimpleInstructionType
import eventstreams.{EventFrame, Fail, UUIDTools}
import play.api.libs.json.{JsString, JsValue, Json}

import scalaz.Scalaz._
import scalaz._


trait EnrichInstructionSysevents extends ComponentWithBaseSysevents {

  val Built = 'Built.trace
  val Enriched = 'Enriched.trace

  override def componentId: String = "Instruction.Enrich"
}

trait EnrichInstructionConstants extends InstructionConstants {
  val CfgFFieldToEnrich = "fieldToEnrich"
  val CfgFTargetValueTemplate = "targetValueTemplate"
  val CfgFTargetType = "targetType"
}

class EnrichInstruction extends SimpleInstructionBuilder with EnrichInstructionConstants with EnrichInstructionSysevents with WithSyseventPublisher {
  val configId = "enrich"

  override def simpleInstruction(props: JsValue, id: Option[String] = None): \/[Fail, SimpleInstructionType] =
    for (
      fieldName <- props ~> CfgFFieldToEnrich orFail s"Invalid $configId instruction. Missing '$CfgFFieldToEnrich' value. Contents: ${Json.stringify(props)}"
    ) yield {
      val fieldValue = props #> CfgFTargetValueTemplate | JsString("")
      val fieldType = props ~> CfgFTargetType | "s"

      val fieldNames = fieldName.split(",").map(_.trim)
      val fieldValues = fieldValue.split(",").map(_.trim)
      val fieldTypes = fieldType.split(",").map(_.trim)

      val configs = (fieldNames.toList, fieldValues.toList, fieldTypes.toList).zipped.toList

      val uuid = UUIDTools.generateShortUUID

      Built >>('Field -> fieldName, 'Value -> fieldValue, 'Type -> fieldType, 'InstructionInstanceId -> uuid)

      frame: EventFrame => {

        val eventId = frame.eventIdOrNA

        val value = configs.foldLeft[EventFrame](frame) {
          case (f, (n,v,s)) =>
            val keyPath = macroReplacement(f, n)

            val replacement: String = macroReplacement(f, v)

            val interim = setValue(s, replacement, keyPath, f)
            Enriched >>('Path -> keyPath, 'Replacement -> replacement, 'NewValue -> interim, 'EventId -> eventId, 'InstructionInstanceId -> uuid)

            interim
        }



        List(value)

      }
    }


}
