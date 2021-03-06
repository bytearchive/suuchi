package in.ashwanthkumar.suuchi.partitioner

import in.ashwanthkumar.suuchi.cluster.MemberAddress
import org.scalatest.FlatSpec
import org.scalatest.Matchers.{be, contain, convertToAnyShouldWrapper, have}

class ConsistentHashPartitionerTest extends FlatSpec {

  "ConsistentHashPartitioner" should "not return anything when underlying CHR has 0 nodes" in {
    val partitioner = ConsistentHashPartitioner(Nil, 3)
    partitioner.find("1".getBytes) should be(Nil)
  }

  it should "return a node when underlying CHR has a node" in {
    val partitioner = ConsistentHashPartitioner(ConsistentHashRing(List(MemberAddress("host1", 1)), 1))
    partitioner.find("1".getBytes) should be(List(MemberAddress("host1", 1)))
  }

  it should "always return unique set of nodes for replication" in {
    val members = (1 to 5).map { index => MemberAddress(s"host$index", index) }.toList
    val partitioner = ConsistentHashPartitioner(ConsistentHashRing(members, 3))
    val nodes = partitioner.find("1".getBytes, 3)
    nodes should have size 3
    nodes should contain(MemberAddress("host3", 3))
    nodes should contain(MemberAddress("host4", 4))
    nodes should contain(MemberAddress("host1", 1))
  }

}
