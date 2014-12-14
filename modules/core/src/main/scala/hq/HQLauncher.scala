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

package hq

import akka.actor.ActorSystem
import akka.cluster.Cluster
import com.typesafe.config.ConfigFactory
import common.storage.ConfigStorageActor
import hq.agents.AgentsManagerActor
import hq.cluster.ClusterManagerActor
import hq.flows.FlowManagerActor
import hq.gates.{RetentionManagerActor, GateManagerActor}
import hq.plugins.SignalSubscriptionManagerActor
import hq.routing.MessageRouterActor
import play.api.libs.json._
import play.api.libs.json.extensions._

import scala.io.Source

/**
 * Created by maks on 18/09/14.
 */
object HQLauncher extends App {

  implicit val config = ConfigFactory.load(System.getProperty("config", "hq.conf"))



  implicit val system = ActorSystem("ehub", config)

  implicit val cluster = Cluster(system)

  ClusterManagerActor.start
  ConfigStorageActor.start
  MessageRouterActor.start
  GateManagerActor.start
  AgentsManagerActor.start
  FlowManagerActor.start

  RetentionManagerActor.start

  SignalSubscriptionManagerActor.start

}
