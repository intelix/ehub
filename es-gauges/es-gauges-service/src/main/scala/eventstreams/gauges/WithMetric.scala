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

package eventstreams.gauges

import scala.util.Random

trait WithMetric[T] extends MetricAccounting {

  var m: Option[T] = None
  private val seed = Random.nextInt()

  private def metricName = signalKey.toMetricName + ":" + seed
  def createMetric(metricName: String): T

  @throws[Exception](classOf[Exception]) override
  def preStart(): Unit = {
    metricRegistry.remove(metricName)
    m = Some(createMetric(metricName))
    super.preStart()
  }

  @throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    metricRegistry.remove(metricName)
    super.postStop()
  }



}
