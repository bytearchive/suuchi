package in.ashwanthkumar.suuchi.router

import in.ashwanthkumar.suuchi.cluster.MemberAddress
import io.grpc.ServerCall.Listener
import io.grpc.testing.TestMethodDescriptors
import io.grpc.{Metadata, MethodDescriptor, ServerCall, ServerCallHandler}
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.FlatSpec

class NoReplicator(nrOfReplicas: Int, self: MemberAddress) extends ReplicationRouter(nrOfReplicas, self) {
  override def replicate[ReqT, RespT](eligibleNodes: scala.List[MemberAddress], serverCall: ServerCall[ReqT, RespT], headers: Metadata, incomingRequest: ReqT, delegate: ServerCall.Listener[ReqT]): Unit = {}
  override def doReplication[ReqT, RespT](eligibleNodes: scala.List[_root_.in.ashwanthkumar.suuchi.cluster.MemberAddress], serverCall: _root_.io.grpc.ServerCall[ReqT, RespT], headers: _root_.io.grpc.Metadata, incomingRequest: ReqT, delegate: _root_.io.grpc.ServerCall.Listener[ReqT]): Unit = ???
}

class MockReplicator(nrOfReplicas: Int, self: MemberAddress, mock: ReplicationRouter) extends ReplicationRouter(nrOfReplicas, self) {
  /**
   * @inheritdoc
   */
  override def replicate[ReqT, RespT](eligibleNodes: scala.List[_root_.in.ashwanthkumar.suuchi.cluster.MemberAddress], serverCall: _root_.io.grpc.ServerCall[ReqT, RespT], headers: _root_.io.grpc.Metadata, incomingRequest: ReqT, delegate: _root_.io.grpc.ServerCall.Listener[ReqT]): Unit = {
    mock.replicate(eligibleNodes, serverCall, headers, incomingRequest, delegate)
  }
  override def doReplication[ReqT, RespT](eligibleNodes: scala.List[_root_.in.ashwanthkumar.suuchi.cluster.MemberAddress], serverCall: _root_.io.grpc.ServerCall[ReqT, RespT], headers: _root_.io.grpc.Metadata, incomingRequest: ReqT, delegate: _root_.io.grpc.ServerCall.Listener[ReqT]): Unit = ???
}

class ReplicationRouterSpec extends FlatSpec {
  "ReplicationRouter" should "delegate the message to the local node if it's a REPLICATION_REQUEST" in {
    val whoami = MemberAddress("host1", 1)
    val replicator = new NoReplicator(1, whoami)

    setupAndVerify { (serverCall: ServerCall[Int, Int], delegate: ServerCall.Listener[Int], next: ServerCallHandler[Int, Int]) =>
      when(next.startCall(any(classOf[ServerCall[Int, Int]]), any(classOf[Metadata]))).thenReturn(delegate)
      val headers = new Metadata()
      headers.put(Headers.REPLICATION_REQUEST_KEY, whoami.toString)

      val listener = replicator.interceptCall(serverCall, headers, next)
      listener.onReady()
      listener.onMessage(1)
      listener.onHalfClose()
      listener.onComplete()
      listener.onCancel()

      verify(delegate, times(1)).onReady()
      verify(delegate, times(1)).onMessage(1)
    }
  }

  it should "not do anything if no required headers are present" in {
    val whoami = MemberAddress("host1", 1)
    val replicator = new NoReplicator(1, whoami)

    setupAndVerify { (serverCall: ServerCall[Int, Int], delegate: ServerCall.Listener[Int], next: ServerCallHandler[Int, Int]) =>
      val headers = new Metadata()
      val listener = replicator.interceptCall(serverCall, headers, next)
      listener.onReady()
      listener.onMessage(1)
      listener.onHalfClose()
      listener.onComplete()
      listener.onCancel()

      verify(delegate, times(1)).onReady()
      verify(delegate, times(0)).onMessage(1)
    }
  }

  it should "replicate the request as per replication strategy" in {
    val whoami = MemberAddress("host1", 1)
    val mockReplicator = mock(classOf[ReplicationRouter])
    val replicator = new MockReplicator(1, whoami, mockReplicator)

    setupAndVerify { (serverCall: ServerCall[Int, Int], delegate: ServerCall.Listener[Int], next: ServerCallHandler[Int, Int]) =>
      val headers = new Metadata()
      headers.put(Headers.ELIGIBLE_NODES_KEY, List(whoami))
      val listener = replicator.interceptCall(serverCall, headers, next)
      listener.onReady()
      listener.onMessage(1)
      listener.onHalfClose()
      listener.onComplete()
      listener.onCancel()

      verify(delegate, times(1)).onReady()
      verify(delegate, times(0)).onMessage(1)
      verify(mockReplicator, times(1)).replicate[Int, Int](any(classOf[List[MemberAddress]]), any(classOf[ServerCall[Int, Int]]), any(classOf[Metadata]), anyInt(), any(classOf[ServerCall.Listener[Int]]))
    }
  }

  def setupAndVerify(verify: (ServerCall[Int, Int], ServerCall.Listener[Int], ServerCallHandler[Int, Int]) => Unit): Unit = {
    val serverCall = mock(classOf[ServerCall[Int, Int]])
    val serverMethodDesc = TestMethodDescriptors.noopMethod[Int, Int]()
    when(serverCall.getMethodDescriptor).thenReturn(serverMethodDesc)

    val delegate = mock(classOf[Listener[Int]])
    val next = mock(classOf[ServerCallHandler[Int, Int]])
    when(next.startCall(any(classOf[ServerCall[Int, Int]]), any(classOf[Metadata]))).thenReturn(delegate)

    verify(serverCall, delegate, next)
  }
}
