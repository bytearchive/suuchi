package in.ashwanthkumar.suuchi.store

import java.nio.ByteBuffer

import in.ashwanthkumar.suuchi.store.PrimitivesSerDeUtils.{bytesToLong, longToBytes}
import in.ashwanthkumar.suuchi.utils.{ByteArrayUtils, DateUtils}
import org.scalatest.FlatSpec
import org.scalatest.Matchers._

trait MockDateUtils extends DateUtils {
  var cnt = 0
  override def now: Long = {
    cnt += 1
    cnt
  }
}
class ByWriteTimestampMocked extends ByWriteTimestamp with MockDateUtils
class KeyAsVersion extends VersionedBy {
  override val versionOrdering: Ordering[Long] = Ordering.Long.reverse
  override def version(key: Array[Byte], value: Array[Byte]): Long = bytesToLong(key)
}

class VersionedStoreSpec extends FlatSpec {
  "VersionedStore" should "return no version info for a key for the first time" in {
    val store = new VersionedStore(new InMemoryStore, new ByWriteTimestampMocked, 3)
    store.getVersions(Array(1.toByte)).size should be(0)
  }

  it should "return version info appropriately after every insert" in {
    val store = new VersionedStore(new InMemoryStore, new ByWriteTimestampMocked, 3)
    store.getVersions(Array(1.toByte)).size should be(0)

    store.put(Array(1.toByte), Array(100.toByte))
    store.getVersions(Array(1.toByte)) should be(List(1))

    store.put(Array(1.toByte), Array(101.toByte))
    store.getVersions(Array(1.toByte)) should be(List(2, 1))

    store.put(Array(1.toByte), Array(102.toByte))
    store.getVersions(Array(1.toByte)) should be(List(3, 2, 1))

    store.put(Array(1.toByte), Array(103.toByte))
    store.getVersions(Array(1.toByte)) should be(List(4,3,2))
  }

  it should "write data for value with an earlier version" in {
    val store = new VersionedStore(new InMemoryStore, new KeyAsVersion, 3)
    store.put(longToBytes(456), longToBytes(456))
    store.getVersions(longToBytes(456)) should be(List(456))
    store.get(longToBytes(456)).map(ByteBuffer.wrap) should be(Some(ByteBuffer.wrap(longToBytes(456))))

    store.put(longToBytes(123), longToBytes(123))
    store.getVersions(longToBytes(123)) should be(List(123))
    store.get(longToBytes(123)).map(ByteBuffer.wrap) should be(Some(ByteBuffer.wrap(longToBytes(123))))
  }

  it should "delete old versions of data for a key when we exceed numVersions" in {
    val inMemoryStore = new InMemoryStore
    val store = new VersionedStore(inMemoryStore, new ByWriteTimestampMocked, 3)
    store.getVersions(Array(1.toByte)).size should be(0)

    store.put(Array(1.toByte), Array(100.toByte))
    store.getVersions(Array(1.toByte)) should be(List(1))

    store.put(Array(1.toByte), Array(101.toByte))
    store.getVersions(Array(1.toByte)) should be(List(2, 1))

    store.put(Array(1.toByte), Array(102.toByte))
    store.getVersions(Array(1.toByte)) should be(List(3, 2, 1))

    store.put(Array(1.toByte), Array(103.toByte))
    inMemoryStore.get(VersionedStore.dkey(Array(1.toByte), 1)) should be(None)
  }

  it should "support full store scan" in {
    val store = new VersionedStore(new InMemoryStore, new ByWriteTimestampMocked, 3)
    val inputs = List(("one".getBytes, "1".getBytes), ("two".getBytes, "2".getBytes), ("three".getBytes, "3".getBytes), ("four".getBytes, "4".getBytes), ("five".getBytes, "5".getBytes))
    val fn = store.put _
    val put = fn.tupled
    inputs.foreach(put)

    val scannedResult = store.scan().toList

    scannedResult should have size 5
    scannedResult.sortBy(kv => new String(kv.value)).zip(inputs).foreach{ case (kv, (inputKey, inputValue)) =>
        ByteArrayUtils.hasPrefix(kv.key, VersionedStore.dkey(inputKey)) should be(true)
        kv.value should be(inputValue)
    }
  }

  it should "support prefix scan" in {
    val store = new VersionedStore(new InMemoryStore, new ByWriteTimestampMocked, 3)
    val inputs = List(("prefix1/one".getBytes, "1".getBytes), ("prefix1/two".getBytes, "2".getBytes), ("prefix1/three".getBytes, "3".getBytes), ("prefix2/one".getBytes, "1".getBytes), ("prefix2/two".getBytes, "2".getBytes), ("prefix2/three".getBytes, "3".getBytes))
    val fn = store.put _
    val put = fn.tupled
    inputs.foreach(put)
    val prefix = "prefix1".getBytes

    val scannedResult = store.scan(prefix).toList

    scannedResult should have size 3
    scannedResult.foreach{kv =>
      new String(kv.key) should startWith(prefixWithDkey(prefix))
    }
  }

  it should "support version scan" in {
    val store = new VersionedStore(new InMemoryStore, new ByWriteTimestampMocked, 3)
    store.put("prefix1/one".getBytes, "1".getBytes)
    store.put("prefix2/two".getBytes, "2".getBytes)
    store.put("prefix3/three".getBytes, "3".getBytes)
    store.put("prefix1/one".getBytes, "11".getBytes)
    store.put("prefix2/two".getBytes, "22".getBytes)
    store.put("prefix1/one".getBytes, "111".getBytes)

    store.scanVersions().flatMap(_.versions) should have size 6
  }

  it should "support version scan based on prefix" in {
    val store = new VersionedStore(new InMemoryStore, new ByWriteTimestampMocked, 3)
    store.put("prefix1/one".getBytes, "1".getBytes)
    store.put("prefix2/two".getBytes, "2".getBytes)
    store.put("prefix3/three".getBytes, "3".getBytes)
    store.put("prefix1/one".getBytes, "11".getBytes)
    store.put("prefix2/two".getBytes, "22".getBytes)
    store.put("prefix1/one".getBytes, "111".getBytes)

    store.scanVersions("prefix1".getBytes).flatMap(_.versions) should have size 3
    store.scanVersions("prefix2".getBytes).flatMap(_.versions) should have size 2
    store.scanVersions("prefix3".getBytes).flatMap(_.versions) should have size 1
    store.scanVersions("prefix4".getBytes).flatMap(_.versions) should have size 0
  }

  private def prefixWithDkey(prefix: Array[Byte]) = new String(VersionedStore.dkey(prefix))
}

