package in.ashwanthkumar.suuchi.cluster.atomix

import java.nio.file.Files

import in.ashwanthkumar.suuchi.cluster.{MemberAddress, InMemorySeedProvider}
import org.apache.commons.io.FileUtils
import org.scalatest.{BeforeAndAfter, FlatSpec}
import org.scalatest.Matchers.{convertToAnyShouldWrapper, have}

class AtomixMembershipSpec extends FlatSpec with BeforeAndAfter {

  val BASE_PORT = 60000
  val raftDir = Files.createTempDirectory("suuchi-membership-it")

  var members: List[AtomixCluster] = List()

  after {
    members.foreach(_.stop())
    FileUtils.deleteDirectory(raftDir.toFile)
  }

  "Membership" should "launch 5 nodes and say they have 5 nodes" in {
    val bootstrapper = InMemorySeedProvider(List(MemberAddress("localhost", BASE_PORT + 1)))
    (1 to 5).foreach { i =>
      val memberPort = BASE_PORT + i
      val member = new AtomixCluster("localhost", memberPort, raftDir.toString, "succhi-test-group")
      if (i > 1) {
        members = members ++ List(member.start(bootstrapper))
      } else {
        members = members ++ List(member.start(InMemorySeedProvider(List())))
      }
    }
    members.map(m => (m.nodes, m.whoami)).foreach(println)
    val totalNodes = members.head.nodes
    totalNodes should have size 5
  }

}
