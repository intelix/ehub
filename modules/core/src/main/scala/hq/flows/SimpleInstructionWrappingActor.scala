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

import akka.actor.{ActorRefFactory, Props}
import akka.stream.actor.{MaxInFlightRequestStrategy, RequestStrategy}
import common.JsonFrame
import common.actors.SubscribingPublisherActor
import hq.flows.core.Builder._

object SimpleInstructionWrappingActor {

  def props(instruction: SimpleInstructionType, maxInFlight: Int): Props = Props(new SimpleInstructionWrappingActor(instruction, maxInFlight))

  def start(f: ActorRefFactory, instruction: SimpleInstructionType, maxInFlight: Int = 96) =
    f.actorOf(props(instruction, maxInFlight))

}


class SimpleInstructionWrappingActor(instruction: SimpleInstructionType, maxInFlight: Int) extends SubscribingPublisherActor {

  override protected def requestStrategy: RequestStrategy = new MaxInFlightRequestStrategy(maxInFlight) {
    override def inFlightInternally: Int = pendingToDownstreamCount
  }

  override def execute(value: JsonFrame) =  Some(instruction(value))

}