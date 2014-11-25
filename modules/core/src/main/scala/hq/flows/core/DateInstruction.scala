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

package hq.flows.core

import java.util.Locale

import agent.controller.flow.Tools._
import com.typesafe.scalalogging.StrictLogging
import common.ToolExt.configHelper
import common.{Fail, JsonFrame}
import hq.flows.core.Builder.{SimpleInstructionType, InstructionType}
import org.joda.time.DateTimeZone
import org.joda.time.format.DateTimeFormat
import play.api.libs.json.{JsNumber, JsString, JsValue, Json}

import scala.annotation.tailrec
import scala.util.Try
import scala.util.matching.Regex
import scalaz.Scalaz._
import scalaz._

/**
 *
 * Symbol  Meaning                      Presentation  Examples
 * ------  -------                      ------------  -------
 * G       era                          text          AD
 * C       century of era (>=0)         number        20
 * Y       year of era (>=0)            year          1996
 *
 * x       weekyear                     year          1996
 * w       week of weekyear             number        27
 * e       day of week                  number        2
 * E       day of week                  text          Tuesday; Tue
 *
 * y       year                         year          1996
 * D       day of year                  number        189
 * M       month of year                month         July; Jul; 07
 * d       day of month                 number        10
 *
 * a       halfday of day               text          PM
 * K       hour of halfday (0~11)       number        0
 * h       clockhour of halfday (1~12)  number        12
 *
 * H       hour of day (0~23)           number        0
 * k       clockhour of day (1~24)      number        24
 * m       minute of hour               number        30
 * s       second of minute             number        55
 * S       fraction of second           number        978
 *
 * z       time zone                    text          Pacific Standard Time; PST
 * Z       time zone offset/id          zone          -0800; -08:00; America/Los_Angeles
 *
 * '       escape for text              delimiter
 * double'      single quote                 literal       '
 *
 */

private[core] object DateInstruction extends SimpleInstructionBuilder {
  val configId = "date"

  val default = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZZ")
  val default_targetFmtField = "date_fmt"
  val default_targetTsField = "date_ts"

  override def simpleInstruction(props: JsValue): \/[Fail, SimpleInstructionType] =
    for (
      id <- props ~> 'id \/> Fail(s"Invalid date instruction. Missing 'id' value. Contents: ${Json.stringify(props)}");
      source <- props ~> 'source \/> Fail(s"Invalid date instruction. Missing 'source' value. Contents: ${Json.stringify(props)}");
      pattern <- (props ~> 'pattern).map(DateTimeFormat.forPattern) \/> Fail(s"Invalid date instruction. Missing 'pattern' value. Contents: ${Json.stringify(props)}")
    ) yield {

      val zone = props ~> 'sourceZone
      val targetZone = props ~> 'targetZone
      var targetPattern = Try((props ~> 'targetPattern).map(DateTimeFormat.forPattern)).getOrElse(Some(default)) | default
      val sourcePattern = zone match {
        case Some(l) => pattern.withZone(DateTimeZone.forID(l))
        case None => pattern
      }
      targetPattern = targetZone match {
        case Some(l) => targetPattern.withZone(DateTimeZone.forID(l))
        case None => targetPattern
      }
      val targetFmtField = props ~> 'targetFmtField | default_targetFmtField
      val targetTsField = props ~> 'targetTSField | default_targetTsField


      fr: JsonFrame => {

        val sourceField = macroReplacement(fr, JsString(source))

        val sourceValue = locateFieldValue(fr, sourceField).asOpt[String].getOrElse("")

        Try(sourcePattern.parseDateTime(sourceValue)).map { dt =>
          List(JsonFrame(
            setValue("n", JsNumber(dt.getMillis), toPath(targetTsField),
              setValue("s", JsString(dt.toString(targetPattern)), toPath(targetFmtField), fr.event)), fr.ctx))
        }.getOrElse(List(fr))

      }
    }


}