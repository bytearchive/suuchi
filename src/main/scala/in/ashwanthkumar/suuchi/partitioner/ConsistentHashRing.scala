package in.ashwanthkumar.suuchi.partitioner

import java.util

import in.ashwanthkumar.suuchi.membership.MemberAddress

import scala.collection.mutable

// Ref - https://git.io/vPOP5
class ConsistentHashRing(hashFn: Hash, vnodeFactor: Int = 3) {
  val sortedMap = new util.TreeMap[Integer, VNode]()

  // when looking for n unique nodes, give up after a streak of MAX_DUPES
  // duplicates
  val MAX_DUPES = 10

  def init(nodes: List[MemberAddress]) = {
    nodes.foreach(add)
    this
  }

  private def hash(vnode: VNode): Int = hashFn.hash(vnode.key.getBytes)

  def add(node: MemberAddress) = {
    (1 to vnodeFactor).map(i => VNode(node, i)).foreach { vnode =>
      sortedMap.put(hash(vnode), vnode)
    }
    this
  }

  def remove(node: MemberAddress) = {
    (1 to vnodeFactor).map(i => VNode(node, i)).foreach { vnode =>
      sortedMap.remove(hash(vnode))
    }
    this
  }

  def find(key: Array[Byte]): Option[MemberAddress] = {
    find(key, 1).headOption
  }

  /**
   * This returns the closest n nodes in order for the object. There may be
   * duplicates.
   */
  def find(key: Array[Byte], n: Int) = {
    if (sortedMap.isEmpty) Nil
    else {
      val (_, nodes) = (0 until n).foldLeft((hashFn.hash(key), List.empty[MemberAddress])){ case ((hash, members), idx) =>
        val (newHash, candidate) = findCandidate(hash)
        (newHash+1, candidate :: members)
      }
      nodes.reverse
    }
  }

  /**
   * This returns the closest n nodes in order for the object. There is extra
   * code that forces the node values to be unique.
   *
   * This will return a list that has all the nodes (and is smaller than n) if n
   * > number of nodes.
   */
  def findUnique(key: Array[Byte], n: Int) = {
    if (sortedMap.isEmpty) Nil
    else {
      var duped = 0
      var hashIdx = hashFn.hash(key)
      val uniqueNodes = mutable.MutableList[MemberAddress]()
      var index = 0
      while (index < n) {
        val (newHash, candidate) = findCandidate(hashIdx)
        hashIdx = newHash
        if (!uniqueNodes.contains(candidate)) {
          duped = 0
          uniqueNodes += candidate
        } else {
          duped += 1
          index -= 1 // try again
          if (duped > MAX_DUPES) {
            index += 1; // we've been duped too many times, just skip to next, returning
            // fewer than n
          }
        }

        // was a hit so we increment and loop to find the next node in the circle
        hashIdx += 1
        index += 1
      }
      uniqueNodes.toList
    }
  }

  private[partitioner] def findCandidate(hash: Integer) = {
    if (sortedMap.containsKey(hash)) {
      hash -> sortedMap.get(hash).node
    } else {
      val tailMap = sortedMap.tailMap(hash)
      val newHash = if (tailMap.isEmpty) sortedMap.firstKey() else tailMap.firstKey()
      newHash -> sortedMap.get(newHash).node
    }
  }

  // USED ONLY FOR TESTS
  private[partitioner] def nodes = sortedMap.values()
}

object ConsistentHashRing {
  def apply(hashFn: Hash): ConsistentHashRing = new ConsistentHashRing(hashFn)

  def apply(): ConsistentHashRing = apply(SuuchiHash)

  def apply(nodes: List[MemberAddress]): ConsistentHashRing = apply(SuuchiHash).init(nodes)

  def apply(replication: Int): ConsistentHashRing = apply(SuuchiHash, replication)

  def apply(hashFn: Hash, replication: Int): ConsistentHashRing = new ConsistentHashRing(hashFn, replication)
}
