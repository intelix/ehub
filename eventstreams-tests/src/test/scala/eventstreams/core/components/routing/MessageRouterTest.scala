package eventstreams.core.components.routing

import eventstreams.core.components.cluster.ClusterManagerActor._
import eventstreams.engine.agents.AgentsManagerActor
import eventstreams.support.{SharedActorSystem, MultiNodeTestingSupport}
import org.scalatest.FlatSpec

class MessageRouterTest
  extends FlatSpec with MultiNodeTestingSupport with SharedActorSystem {


  val expectedPeersListInitial = "engine1"
  val expectedPeersListComplete = "engine1,engine2,worker1,worker2,worker3"

  "Cluster" should "start with 5 nodes and all peers should be discovered" in new WithEngineNode1
    with WithEngineNode2 with WithWorkerNode1 with WithWorkerNode2 with WithWorkerNode3 {
    expectSomeEventsWithTimeout(30000, ClusterStateChanged, 'Peers -> expectedPeersListInitial, 'Node -> "engine1")
    expectSomeEventsWithTimeout(30000, ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "engine1")
    expectSomeEvents(ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "engine2")
    expectSomeEvents(ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "worker1")
    expectSomeEvents(ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "worker2")
    expectSomeEvents(ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "worker3")
  }

  it should "consistently" in new WithEngineNode1
    with WithEngineNode2 with WithWorkerNode1 with WithWorkerNode2 with WithWorkerNode3 {
    expectSomeEventsWithTimeout(30000, ClusterStateChanged, 'Peers -> expectedPeersListInitial, 'Node -> "engine1")
    expectSomeEventsWithTimeout(30000, ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "engine1")
    expectSomeEvents(ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "engine2")
    expectSomeEvents(ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "worker1")
    expectSomeEvents(ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "worker2")
    expectSomeEvents(ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "worker3")
  }

  it should "and again (testing SharedActorSystem)" in new WithEngineNode1
    with WithEngineNode2 with WithWorkerNode1 with WithWorkerNode2 with WithWorkerNode3 {
    expectSomeEventsWithTimeout(30000, ClusterStateChanged, 'Peers -> expectedPeersListInitial, 'Node -> "engine1")
    expectSomeEventsWithTimeout(30000, ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "engine1")
    expectSomeEvents(ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "engine2")
    expectSomeEvents(ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "worker1")
    expectSomeEvents(ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "worker2")
    expectSomeEvents(ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "worker3")
  }

  it should "and again ... (testing SharedActorSystem)" in new WithEngineNode1
    with WithEngineNode2 with WithWorkerNode1 with WithWorkerNode2 with WithWorkerNode3 {
    expectSomeEventsWithTimeout(30000, ClusterStateChanged, 'Peers -> expectedPeersListInitial, 'Node -> "engine1")
    expectSomeEventsWithTimeout(30000, ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "engine1")
    expectSomeEvents(ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "engine2")
    expectSomeEvents(ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "worker1")
    expectSomeEvents(ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "worker2")
    expectSomeEvents(ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "worker3")
  }

}