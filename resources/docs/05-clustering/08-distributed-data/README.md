# Distributed Data - Overview

Akka Distributed Data is useful when you need to share data between nodes in an Akka Cluster. The data is accessed with an actor providing a key-value store like API. The keys are unique identifiers with type information of the data values. The values are Conflict Free Replicated Data Types (CRDTs).

All data entries are spread to all nodes, or nodes with a certain role, in the cluster via direct replication and gossip based dissemination. You have fine grained control of the consistency level for reads and writes.

The nature CRDTs makes it possible to perform updates from any node without coordination. Concurrent updates from different nodes will automatically be resolved by the monotonic merge function, which all data types must provide. The state changes always converge. Several useful data types for counters, sets, maps and registers are provided and you can also implement your own custom data types.

It is eventually consistent and geared toward providing high read and write availability (partition tolerance), with low latency. Note that in an eventually consistent system a read may return an out-of-date value.

# Using the Replicator

The akka.cluster.ddata.Replicator actor provides the API for interacting with the data. The Replicator actor must be started on each node in the cluster, or group of nodes tagged with a specific role. It communicates with other Replicator instances with the same path (without address) that are running on other nodes . For convenience it can be used with the akka.cluster.ddata.DistributedData extension but it can also be started as an ordinary actor using the Replicator.props. If it is started as an ordinary actor it is important that it is given the same name, started on same path, on all nodes.

Cluster members with status WeaklyUp, will participate in Distributed Data. This means that the data will be replicated to the WeaklyUp nodes with the background gossip protocol. Note that it will not participate in any actions where the consistency mode is to read/write from all nodes or the majority of nodes. The WeaklyUp node is not counted as part of the cluster. So 3 nodes + 5 WeaklyUp is essentially a 3 node cluster as far as consistent actions are concerned.

Below is an example of an actor that schedules tick messages to itself and for each tick adds or removes elements from a ORSet (observed-remove set). It also subscribes to changes of this.

Scala

    import java.util.concurrent.ThreadLocalRandom
    import akka.actor.Actor
    import akka.actor.ActorLogging
    import akka.cluster.Cluster
    import akka.cluster.ddata.DistributedData
    import akka.cluster.ddata.ORSet
    import akka.cluster.ddata.ORSetKey
    import akka.cluster.ddata.Replicator
    import akka.cluster.ddata.Replicator._

    object DataBot {
      private case object Tick
    }

    class DataBot extends Actor with ActorLogging {
      import DataBot._

      val replicator = DistributedData(context.system).replicator
      implicit val node = Cluster(context.system)

      import context.dispatcher
      val tickTask = context.system.scheduler.schedule(5.seconds, 5.seconds, self, Tick)

      val DataKey = ORSetKey[String]("key")

      replicator ! Subscribe(DataKey, self)

      def receive = {
        case Tick ⇒
          val s = ThreadLocalRandom.current().nextInt(97, 123).toChar.toString
          if (ThreadLocalRandom.current().nextBoolean()) {
            // add
            log.info("Adding: {}", s)
            replicator ! Update(DataKey, ORSet.empty[String], WriteLocal)(_ + s)
          } else {
            // remove
            log.info("Removing: {}", s)
            replicator ! Update(DataKey, ORSet.empty[String], WriteLocal)(_ - s)
          }

        case _: UpdateResponse[_] ⇒ // ignore

        case c @ Changed(DataKey) ⇒
          val data = c.get(DataKey)
          log.info("Current elements: {}", data.elements)
      }

      override def postStop(): Unit = tickTask.cancel()

    }

Java


## Update

To modify and replicate a data value you send a Replicator.Update message to the local Replicator.

The current data value for the key of the Update is passed as parameter to the modify function of the Update. The function is supposed to return the new value of the data, which will then be replicated according to the given consistency level.

The modify function is called by the Replicator actor and must therefore be a pure function that only uses the data parameter and stable fields from enclosing scope. It must for example not access the sender (sender()) reference of an enclosing actor.

Update is intended to only be sent from an actor running in same local ActorSystem as : the Replicator, because the modify function is typically not serializable.

You supply a write consistency level which has the following meaning:

    WriteLocal the value will immediately only be written to the local replica, and later disseminated with gossip
    WriteTo(n) the value will immediately be written to at least n replicas, including the local replica
    WriteMajority the value will immediately be written to a majority of replicas, i.e. at least N/2 + 1 replicas, where N is the number of nodes in the cluster (or cluster role group)
    WriteAll the value will immediately be written to all nodes in the cluster (or all nodes in the cluster role group)

When you specify to write to n out of x nodes, the update will first replicate to n nodes. If there are not enough Acks after 1/5th of the timeout, the update will be replicated to n other nodes. If there are less than n nodes left all of the remaining nodes are used. Reachable nodes are preferred over unreachable nodes.

Note that WriteMajority has a minCap parameter that is useful to specify to achieve better safety for small clusters.

Scala

    implicit val node = Cluster(system)
    val replicator = DistributedData(system).replicator

    val Counter1Key = PNCounterKey("counter1")
    val Set1Key = GSetKey[String]("set1")
    val Set2Key = ORSetKey[String]("set2")
    val ActiveFlagKey = FlagKey("active")

    replicator ! Update(Counter1Key, PNCounter(), WriteLocal)(_ + 1)

    val writeTo3 = WriteTo(n = 3, timeout = 1.second)
    replicator ! Update(Set1Key, GSet.empty[String], writeTo3)(_ + "hello")

    val writeMajority = WriteMajority(timeout = 5.seconds)
    replicator ! Update(Set2Key, ORSet.empty[String], writeMajority)(_ + "hello")

    val writeAll = WriteAll(timeout = 5.seconds)
    replicator ! Update(ActiveFlagKey, Flag.Disabled, writeAll)(_.switchOn)

Java

As reply of the Update a Replicator.UpdateSuccess is sent to the sender of the Update if the value was successfully replicated according to the supplied consistency level within the supplied timeout. Otherwise a Replicator.UpdateFailure subclass is sent back. Note that a Replicator.UpdateTimeout reply does not mean that the update completely failed or was rolled back. It may still have been replicated to some nodes, and will eventually be replicated to all nodes with the gossip protocol.

Scala

    case UpdateSuccess(Counter1Key, req) ⇒ // ok

Java

Scala

    case UpdateSuccess(Set1Key, req)  ⇒ // ok
    case UpdateTimeout(Set1Key, req)  ⇒
    // write to 3 nodes failed within 1.second

Java

You will always see your own writes. For example if you send two Update messages changing the value of the same key, the modify function of the second message will see the change that was performed by the first Update message.

In the Update message you can pass an optional request context, which the Replicator does not care about, but is included in the reply messages. This is a convenient way to pass contextual information (e.g. original sender) without having to use ask or maintain local correlation data structures.

Scala

    implicit val node = Cluster(system)
    val replicator = DistributedData(system).replicator
    val writeTwo = WriteTo(n = 2, timeout = 3.second)
    val Counter1Key = PNCounterKey("counter1")

    def receive: Receive = {
      case "increment" ⇒
        // incoming command to increase the counter
        val upd = Update(Counter1Key, PNCounter(), writeTwo, request = Some(sender()))(_ + 1)
        replicator ! upd

      case UpdateSuccess(Counter1Key, Some(replyTo: ActorRef)) ⇒
        replyTo ! "ack"
      case UpdateTimeout(Counter1Key, Some(replyTo: ActorRef)) ⇒
        replyTo ! "nack"
    }

Java


## Get

To retrieve the current value of a data you send Replicator.Get message to the Replicator. You supply a consistency level which has the following meaning:

    ReadLocal the value will only be read from the local replica
    ReadFrom(n) the value will be read and merged from n replicas, including the local replica
    ReadMajority the value will be read and merged from a majority of replicas, i.e. at least N/2 + 1 replicas, where N is the number of nodes in the cluster (or cluster role group)
    ReadAll the value will be read and merged from all nodes in the cluster (or all nodes in the cluster role group)

Note that ReadMajority has a minCap parameter that is useful to specify to achieve better safety for small clusters.

Scala

    val replicator = DistributedData(system).replicator
    val Counter1Key = PNCounterKey("counter1")
    val Set1Key = GSetKey[String]("set1")
    val Set2Key = ORSetKey[String]("set2")
    val ActiveFlagKey = FlagKey("active")

    replicator ! Get(Counter1Key, ReadLocal)

    val readFrom3 = ReadFrom(n = 3, timeout = 1.second)
    replicator ! Get(Set1Key, readFrom3)

    val readMajority = ReadMajority(timeout = 5.seconds)
    replicator ! Get(Set2Key, readMajority)

    val readAll = ReadAll(timeout = 5.seconds)
    replicator ! Get(ActiveFlagKey, readAll)

Java

As reply of the Get a Replicator.GetSuccess is sent to the sender of the Get if the value was successfully retrieved according to the supplied consistency level within the supplied timeout. Otherwise a Replicator.GetFailure is sent. If the key does not exist the reply will be Replicator.NotFound.

Scala

    case g @ GetSuccess(Counter1Key, req) ⇒
      val value = g.get(Counter1Key).value
    case NotFound(Counter1Key, req) ⇒ // key counter1 does not exist

Java

Scala

    case g @ GetSuccess(Set1Key, req) ⇒
      val elements = g.get(Set1Key).elements
    case GetFailure(Set1Key, req) ⇒
    // read from 3 nodes failed within 1.second
    case NotFound(Set1Key, req)   ⇒ // key set1 does not exist

Java

You will always read your own writes. For example if you send a Update message followed by a Get of the same key the Get will retrieve the change that was performed by the preceding Update message. However, the order of the reply messages are not defined, i.e. in the previous example you may receive the GetSuccess before the UpdateSuccess.

In the Get message you can pass an optional request context in the same way as for the Update message, described above. For example the original sender can be passed and replied to after receiving and transforming GetSuccess.

Scala

    implicit val node = Cluster(system)
    val replicator = DistributedData(system).replicator
    val readTwo = ReadFrom(n = 2, timeout = 3.second)
    val Counter1Key = PNCounterKey("counter1")

    def receive: Receive = {
      case "get-count" ⇒
        // incoming request to retrieve current value of the counter
        replicator ! Get(Counter1Key, readTwo, request = Some(sender()))

      case g @ GetSuccess(Counter1Key, Some(replyTo: ActorRef)) ⇒
        val value = g.get(Counter1Key).value.longValue
        replyTo ! value
      case GetFailure(Counter1Key, Some(replyTo: ActorRef)) ⇒
        replyTo ! -1L
      case NotFound(Counter1Key, Some(replyTo: ActorRef)) ⇒
        replyTo ! 0L
    }

Java


## Consistency

The consistency level that is supplied in the Update and Get specifies per request how many replicas that must respond successfully to a write and read request.

For low latency reads you use ReadLocal with the risk of retrieving stale data, i.e. updates from other nodes might not be visible yet.

When using WriteLocal the update is only written to the local replica and then disseminated in the background with the gossip protocol, which can take few seconds to spread to all nodes.

WriteAll and ReadAll is the strongest consistency level, but also the slowest and with lowest availability. For example, it is enough that one node is unavailable for a Get request and you will not receive the value.

If consistency is important, you can ensure that a read always reflects the most recent write by using the following formula:

(nodes_written + nodes_read) > N

where N is the total number of nodes in the cluster, or the number of nodes with the role that is used for the Replicator.

For example, in a 7 node cluster this these consistency properties are achieved by writing to 4 nodes and reading from 4 nodes, or writing to 5 nodes and reading from 3 nodes.

By combining WriteMajority and ReadMajority levels a read always reflects the most recent write. The Replicator writes and reads to a majority of replicas, i.e. N / 2 + 1. For example, in a 5 node cluster it writes to 3 nodes and reads from 3 nodes. In a 6 node cluster it writes to 4 nodes and reads from 4 nodes.

You can define a minimum number of nodes for WriteMajority and ReadMajority, this will minimize the risk of reading stale data. Minimum cap is provided by minCap property of WriteMajority and ReadMajority and defines the required majority. If the minCap is higher then N / 2 + 1 the minCap will be used.

For example if the minCap is 5 the WriteMajority and ReadMajority for cluster of 3 nodes will be 3, for cluster of 6 nodes will be 5 and for cluster of 12 nodes will be 7 ( N / 2 + 1 ).

For small clusters (<7) the risk of membership changes between a WriteMajority and ReadMajority is rather high and then the nice properties of combining majority write and reads are not guaranteed. Therefore the ReadMajority and WriteMajority have a minCap parameter that is useful to specify to achieve better safety for small clusters. It means that if the cluster size is smaller than the majority size it will use the minCap number of nodes but at most the total size of the cluster.

Here is an example of using WriteMajority and ReadMajority:

Scala

    private val timeout = 3.seconds
    private val readMajority = ReadMajority(timeout)
    private val writeMajority = WriteMajority(timeout)

Java

Scala

    def receiveGetCart: Receive = {
      case GetCart ⇒
        replicator ! Get(DataKey, readMajority, Some(sender()))

      case g @ GetSuccess(DataKey, Some(replyTo: ActorRef)) ⇒
        val data = g.get(DataKey)
        val cart = Cart(data.entries.values.toSet)
        replyTo ! cart

      case NotFound(DataKey, Some(replyTo: ActorRef)) ⇒
        replyTo ! Cart(Set.empty)

      case GetFailure(DataKey, Some(replyTo: ActorRef)) ⇒
        // ReadMajority failure, try again with local read
        replicator ! Get(DataKey, ReadLocal, Some(replyTo))
    }

Java

Scala

    def receiveAddItem: Receive = {
      case cmd @ AddItem(item) ⇒
        val update = Update(DataKey, LWWMap.empty[String, LineItem], writeMajority, Some(cmd)) {
          cart ⇒ updateCart(cart, item)
        }
        replicator ! update
    }

Java

In some rare cases, when performing an Update it is needed to first try to fetch latest data from other nodes. That can be done by first sending a Get with ReadMajority and then continue with the Update when the GetSuccess, GetFailure or NotFound reply is received. This might be needed when you need to base a decision on latest information or when removing entries from ORSet or ORMap. If an entry is added to an ORSet or ORMap from one node and removed from another node the entry will only be removed if the added entry is visible on the node where the removal is performed (hence the name observed-removed set).

The following example illustrates how to do that:

Scala

    def receiveRemoveItem: Receive = {
      case cmd @ RemoveItem(productId) ⇒
        // Try to fetch latest from a majority of nodes first, since ORMap
        // remove must have seen the item to be able to remove it.
        replicator ! Get(DataKey, readMajority, Some(cmd))

      case GetSuccess(DataKey, Some(RemoveItem(productId))) ⇒
        replicator ! Update(DataKey, LWWMap(), writeMajority, None) {
          _ - productId
        }

      case GetFailure(DataKey, Some(RemoveItem(productId))) ⇒
        // ReadMajority failed, fall back to best effort local value
        replicator ! Update(DataKey, LWWMap(), writeMajority, None) {
          _ - productId
        }

      case NotFound(DataKey, Some(RemoveItem(productId))) ⇒
      // nothing to remove
    }

Java

Warning

Caveat: Even if you use WriteMajority and ReadMajority there is small risk that you may read stale data if the cluster membership has changed between the Update and the Get. For example, in cluster of 5 nodes when you Update and that change is written to 3 nodes: n1, n2, n3. Then 2 more nodes are added and a Get request is reading from 4 nodes, which happens to be n4, n5, n6, n7, i.e. the value on n1, n2, n3 is not seen in the response of the Get request.

## Subscribe

You may also register interest in change notifications by sending Replicator.Subscribe message to the Replicator. It will send Replicator.Changed messages to the registered subscriber when the data for the subscribed key is updated. Subscribers will be notified periodically with the configured notify-subscribers-interval, and it is also possible to send an explicit Replicator.FlushChanges message to the Replicator to notify the subscribers immediately.

The subscriber is automatically removed if the subscriber is terminated. A subscriber can also be deregistered with the Replicator.Unsubscribe message.

Scala

    val replicator = DistributedData(system).replicator
    val Counter1Key = PNCounterKey("counter1")
    // subscribe to changes of the Counter1Key value
    replicator ! Subscribe(Counter1Key, self)
    var currentValue = BigInt(0)

    def receive: Receive = {
      case c @ Changed(Counter1Key) ⇒
        currentValue = c.get(Counter1Key).value
      case "get-count" ⇒
        // incoming request to retrieve current value of the counter
        sender() ! currentValue
    }

Java


## Delete

A data entry can be deleted by sending a Replicator.Delete message to the local local Replicator. As reply of the Delete a Replicator.DeleteSuccess is sent to the sender of the Delete if the value was successfully deleted according to the supplied consistency level within the supplied timeout. Otherwise a Replicator.ReplicationDeleteFailure is sent. Note that ReplicationDeleteFailure does not mean that the delete completely failed or was rolled back. It may still have been replicated to some nodes, and may eventually be replicated to all nodes.

A deleted key cannot be reused again, but it is still recommended to delete unused data entries because that reduces the replication overhead when new nodes join the cluster. Subsequent Delete, Update and Get requests will be replied with Replicator.DataDeleted. Subscribers will receive Replicator.Deleted.

In the Delete message you can pass an optional request context in the same way as for the Update message, described above. For example the original sender can be passed and replied to after receiving and transforming DeleteSuccess.

Scala

    val replicator = DistributedData(system).replicator
    val Counter1Key = PNCounterKey("counter1")
    val Set2Key = ORSetKey[String]("set2")

    replicator ! Delete(Counter1Key, WriteLocal)

    val writeMajority = WriteMajority(timeout = 5.seconds)
    replicator ! Delete(Set2Key, writeMajority)

Java

Warning

As deleted keys continue to be included in the stored data on each node as well as in gossip messages, a continuous series of updates and deletes of top-level entities will result in growing memory usage until an ActorSystem runs out of memory. To use Akka Distributed Data where frequent adds and removes are required, you should use a fixed number of top-level data types that support both updates and removals, for example ORMap or ORSet.

## delta-CRDT

Delta State Replicated Data Types are supported. delta-CRDT is a way to reduce the need for sending the full state for updates. For example adding element 'c' and 'd' to set {'a', 'b'} would result in sending the delta {'c', 'd'} and merge that with the state on the receiving side, resulting in set {'a', 'b', 'c', 'd'}.

The protocol for replicating the deltas supports causal consistency if the data type is marked with RequiresCausalDeliveryOfDeltas. Otherwise it is only eventually consistent. Without causal consistency it means that if elements 'c' and 'd' are added in two separate Update operations these deltas may occasionally be propagated to nodes in different order than the causal order of the updates. For this example it can result in that set {'a', 'b', 'd'} can be seen before element ‘c’ is seen. Eventually it will be {'a', 'b', 'c', 'd'}.

Note that the full state is occasionally also replicated for delta-CRDTs, for example when new nodes are added to the cluster or when deltas could not be propagated because of network partitions or similar problems.

The the delta propagation can be disabled with configuration property:

akka.cluster.distributed-data.delta-crdt.enabled=off


# Data Types

The data types must be convergent (stateful) CRDTs and implement the ReplicatedData trait, i.e. they provide a monotonic merge function and the state changes always converge.

You can use your own custom ReplicatedData or DeltaReplicatedData types, and several types are provided by this package, such as:

    Counters: GCounter, PNCounter
    Sets: GSet, ORSet
    Maps: ORMap, ORMultiMap, LWWMap, PNCounterMap
    Registers: LWWRegister, Flag


## Counters

GCounter is a “grow only counter”. It only supports increments, no decrements.

It works in a similar way as a vector clock. It keeps track of one counter per node and the total value is the sum of these counters. The merge is implemented by taking the maximum count for each node.

If you need both increments and decrements you can use the PNCounter (positive/negative counter).

It is tracking the increments (P) separate from the decrements (N). Both P and N are represented as two internal GCounter. Merge is handled by merging the internal P and N counters. The value of the counter is the value of the P counter minus the value of the N counter.

Scala

    implicit val node = Cluster(system)
    val c0 = PNCounter.empty
    val c1 = c0 + 1
    val c2 = c1 + 7
    val c3: PNCounter = c2 - 2
    println(c3.value) // 6

Java

GCounter and PNCounter have support for delta-CRDT and don’t need causal delivery of deltas.

Several related counters can be managed in a map with the PNCounterMap data type. When the counters are placed in a PNCounterMap as opposed to placing them as separate top level values they are guaranteed to be replicated together as one unit, which is sometimes necessary for related data.

Scala

    implicit val node = Cluster(system)
    val m0 = PNCounterMap.empty[String]
    val m1 = m0.increment("a", 7)
    val m2 = m1.decrement("a", 2)
    val m3 = m2.increment("b", 1)
    println(m3.get("a")) // 5
    m3.entries.foreach { case (key, value) ⇒ println(s"$key -> $value") }

Java


## Sets

If you only need to add elements to a set and not remove elements the GSet (grow-only set) is the data type to use. The elements can be any type of values that can be serialized. Merge is simply the union of the two sets.

Scala

    val s0 = GSet.empty[String]
    val s1 = s0 + "a"
    val s2 = s1 + "b" + "c"
    if (s2.contains("a"))
      println(s2.elements) // a, b, c

Java

GSet has support for delta-CRDT and it doesn’t require causal delivery of deltas.

If you need add and remove operations you should use the ORSet (observed-remove set). Elements can be added and removed any number of times. If an element is concurrently added and removed, the add will win. You cannot remove an element that you have not seen.

The ORSet has a version vector that is incremented when an element is added to the set. The version for the node that added the element is also tracked for each element in a so called “birth dot”. The version vector and the dots are used by the merge function to track causality of the operations and resolve concurrent updates.

Scala

    implicit val node = Cluster(system)
    val s0 = ORSet.empty[String]
    val s1 = s0 + "a"
    val s2 = s1 + "b"
    val s3 = s2 - "a"
    println(s3.elements) // b

Java

ORSet has support for delta-CRDT and it requires causal delivery of deltas.

## Maps

ORMap (observed-remove map) is a map with keys of Any type and the values are ReplicatedData types themselves. It supports add, update and remove any number of times for a map entry.

If an entry is concurrently added and removed, the add will win. You cannot remove an entry that you have not seen. This is the same semantics as for the ORSet.

If an entry is concurrently updated to different values the values will be merged, hence the requirement that the values must be ReplicatedData types.

It is rather inconvenient to use the ORMap directly since it does not expose specific types of the values. The ORMap is intended as a low level tool for building more specific maps, such as the following specialized maps.

ORMultiMap (observed-remove multi-map) is a multi-map implementation that wraps an ORMap with an ORSet for the map’s value.

PNCounterMap (positive negative counter map) is a map of named counters (where the name can be of any type). It is a specialized ORMap with PNCounter values.

LWWMap (last writer wins map) is a specialized ORMap with LWWRegister (last writer wins register) values.

ORMap, ORMultiMap, PNCounterMap and LWWMap have support for delta-CRDT and they require causal delivery of deltas. Support for deltas here means that the ORSet being underlying key type for all those maps uses delta propagation to deliver updates. Effectively, the update for map is then a pair, consisting of delta for the ORSet being the key and full update for the respective value (ORSet, PNCounter or LWWRegister) kept in the map.

Scala

    implicit val node = Cluster(system)
    val m0 = ORMultiMap.empty[String, Int]
    val m1 = m0 + ("a" -> Set(1, 2, 3))
    val m2 = m1.addBinding("a", 4)
    val m3 = m2.removeBinding("a", 2)
    val m4 = m3.addBinding("b", 1)
    println(m4.entries)

Java

When a data entry is changed the full state of that entry is replicated to other nodes, i.e. when you update a map, the whole map is replicated. Therefore, instead of using one ORMap with 1000 elements it is more efficient to split that up in 10 top level ORMap entries with 100 elements each. Top level entries are replicated individually, which has the trade-off that different entries may not be replicated at the same time and you may see inconsistencies between related entries. Separate top level entries cannot be updated atomically together.

There is a special version of ORMultiMap, created by using separate constructor ORMultiMap.emptyWithValueDeltas[A, B], that also propagates the updates to its values (of ORSet type) as deltas. This means that the ORMultiMap initiated with ORMultiMap.emptyWithValueDeltas propagates its updates as pairs consisting of delta of the key and delta of the value. It is much more efficient in terms of network bandwith consumed.

However, this behaviour has not been made default for ORMultiMap and if you wish to use it in your code, you need to replace invocations of ORMultiMap.empty[A, B] (or ORMultiMap()) with ORMultiMap.emptyWithValueDeltas[A, B] where A and B are types respectively of keys and values in the map.

Please also note, that despite having the same Scala type, ORMultiMap.emptyWithValueDeltas is not compatible with ‘vanilla’ ORMultiMap, because of different replication mechanism. One needs to be extra careful not to mix the two, as they have the same type, so compiler will not hint the error. Nonetheless ORMultiMap.emptyWithValueDeltas uses the same ORMultiMapKey type as the ‘vanilla’ ORMultiMap for referencing.

Note that LWWRegister and therefore LWWMap relies on synchronized clocks and should only be used when the choice of value is not important for concurrent updates occurring within the clock skew. Read more in the below section about LWWRegister.

## Flags and Registers

Flag is a data type for a boolean value that is initialized to false and can be switched to true. Thereafter it cannot be changed. true wins over false in merge.

Scala

    val f0 = Flag.Disabled
    val f1 = f0.switchOn
    println(f1.enabled)

Java

LWWRegister (last writer wins register) can hold any (serializable) value.

Merge of a LWWRegister takes the register with highest timestamp. Note that this relies on synchronized clocks. LWWRegister should only be used when the choice of value is not important for concurrent updates occurring within the clock skew.

Merge takes the register updated by the node with lowest address (UniqueAddress is ordered) if the timestamps are exactly the same.

Scala

    implicit val node = Cluster(system)
    val r1 = LWWRegister("Hello")
    val r2 = r1.withValue("Hi")
    println(s"${r1.value} by ${r1.updatedBy} at ${r1.timestamp}")

Java

Instead of using timestamps based on System.currentTimeMillis() time it is possible to use a timestamp value based on something else, for example an increasing version number from a database record that is used for optimistic concurrency control.

Scala

    case class Record(version: Int, name: String, address: String)

    implicit val node = Cluster(system)
    implicit val recordClock = new LWWRegister.Clock[Record] {
      override def apply(currentTimestamp: Long, value: Record): Long =
        value.version
    }

    val record1 = Record(version = 1, "Alice", "Union Square")
    val r1 = LWWRegister(record1)

    val record2 = Record(version = 2, "Alice", "Madison Square")
    val r2 = LWWRegister(record2)

    val r3 = r1.merge(r2)
    println(r3.value)

Java

For first-write-wins semantics you can use the LWWRegister#reverseClock instead of the LWWRegister#defaultClock.

The defaultClock is using max value of System.currentTimeMillis() and currentTimestamp + 1. This means that the timestamp is increased for changes on the same node that occurs within the same millisecond. It also means that it is safe to use the LWWRegister without synchronized clocks when there is only one active writer, e.g. a Cluster Singleton. Such a single writer should then first read current value with ReadMajority (or more) before changing and writing the value with WriteMajority (or more).

## Custom Data Type

You can rather easily implement your own data types. The only requirement is that it implements the merge function of the ReplicatedData trait.

A nice property of stateful CRDTs is that they typically compose nicely, i.e. you can combine several smaller data types to build richer data structures. For example, the PNCounter is composed of two internal GCounter instances to keep track of increments and decrements separately.

Here is s simple implementation of a custom TwoPhaseSet that is using two internal GSet types to keep track of addition and removals. A TwoPhaseSet is a set where an element may be added and removed, but never added again thereafter.

Scala

    case class TwoPhaseSet(
      adds:     GSet[String] = GSet.empty,
      removals: GSet[String] = GSet.empty)
      extends ReplicatedData {
      type T = TwoPhaseSet

      def add(element: String): TwoPhaseSet =
        copy(adds = adds.add(element))

      def remove(element: String): TwoPhaseSet =
        copy(removals = removals.add(element))

      def elements: Set[String] = adds.elements diff removals.elements

      override def merge(that: TwoPhaseSet): TwoPhaseSet =
        copy(
          adds = this.adds.merge(that.adds),
          removals = this.removals.merge(that.removals))
    }

Java

Data types should be immutable, i.e. “modifying” methods should return a new instance.

Implement the additional methods of DeltaReplicatedData if it has support for delta-CRDT replication.
Serialization

The data types must be serializable with an Akka Serializer. It is highly recommended that you implement efficient serialization with Protobuf or similar for your custom data types. The built in data types are marked with ReplicatedDataSerialization and serialized with akka.cluster.ddata.protobuf.ReplicatedDataSerializer.


### Serialization of the data types are used in remote messages and also for creating message digests (SHA-1) to detect changes. Therefore it is important that the serialization is efficient and produce the same bytes for the same content. For example sets and maps should be sorted deterministically in the serialization.

This is a protobuf representation of the above TwoPhaseSet:

option java_package = "docs.ddata.protobuf.msg";
option optimize_for = SPEED;

message TwoPhaseSet {
  repeated string adds = 1;
  repeated string removals = 2;
}

The serializer for the TwoPhaseSet:

Scala

    import java.util.ArrayList
    import java.util.Collections
    import scala.collection.JavaConverters._
    import akka.actor.ExtendedActorSystem
    import akka.cluster.ddata.GSet
    import akka.cluster.ddata.protobuf.SerializationSupport
    import akka.serialization.Serializer
    import docs.ddata.TwoPhaseSet
    import docs.ddata.protobuf.msg.TwoPhaseSetMessages

    class TwoPhaseSetSerializer(val system: ExtendedActorSystem)
      extends Serializer with SerializationSupport {

      override def includeManifest: Boolean = false

      override def identifier = 99999

      override def toBinary(obj: AnyRef): Array[Byte] = obj match {
        case m: TwoPhaseSet ⇒ twoPhaseSetToProto(m).toByteArray
        case _ ⇒ throw new IllegalArgumentException(
          s"Can't serialize object of type ${obj.getClass}")
      }

      override def fromBinary(bytes: Array[Byte], clazz: Option[Class[_]]): AnyRef = {
        twoPhaseSetFromBinary(bytes)
      }

      def twoPhaseSetToProto(twoPhaseSet: TwoPhaseSet): TwoPhaseSetMessages.TwoPhaseSet = {
        val b = TwoPhaseSetMessages.TwoPhaseSet.newBuilder()
        // using java collections and sorting for performance (avoid conversions)
        val adds = new ArrayList[String]
        twoPhaseSet.adds.elements.foreach(adds.add)
        if (!adds.isEmpty) {
          Collections.sort(adds)
          b.addAllAdds(adds)
        }
        val removals = new ArrayList[String]
        twoPhaseSet.removals.elements.foreach(removals.add)
        if (!removals.isEmpty) {
          Collections.sort(removals)
          b.addAllRemovals(removals)
        }
        b.build()
      }

      def twoPhaseSetFromBinary(bytes: Array[Byte]): TwoPhaseSet = {
        val msg = TwoPhaseSetMessages.TwoPhaseSet.parseFrom(bytes)
        val addsSet = msg.getAddsList.iterator.asScala.toSet
        val removalsSet = msg.getRemovalsList.iterator.asScala.toSet
        val adds = addsSet.foldLeft(GSet.empty[String])((acc, el) ⇒ acc.add(el))
        val removals = removalsSet.foldLeft(GSet.empty[String])((acc, el) ⇒ acc.add(el))
        // GSet will accumulate deltas when adding elements,
        // but those are not of interest in the result of the deserialization
        TwoPhaseSet(adds.resetDelta, removals.resetDelta)
      }
    }

Java

Note that the elements of the sets are sorted so the SHA-1 digests are the same for the same elements.

You register the serializer in configuration:

Scala

    akka.actor {
      serializers {
        two-phase-set = "docs.ddata.protobuf.TwoPhaseSetSerializer"
      }
      serialization-bindings {
        "docs.ddata.TwoPhaseSet" = two-phase-set
      }
    }

Java

Using compression can sometimes be a good idea to reduce the data size. Gzip compression is provided by the akka.cluster.ddata.protobuf.SerializationSupport trait:

Scala

    override def toBinary(obj: AnyRef): Array[Byte] = obj match {
      case m: TwoPhaseSet ⇒ compress(twoPhaseSetToProto(m))
      case _ ⇒ throw new IllegalArgumentException(
        s"Can't serialize object of type ${obj.getClass}")
    }

    override def fromBinary(bytes: Array[Byte], clazz: Option[Class[_]]): AnyRef = {
      twoPhaseSetFromBinary(decompress(bytes))
    }

Java

The two embedded GSet can be serialized as illustrated above, but in general when composing new data types from the existing built in types it is better to make use of the existing serializer for those types. This can be done by declaring those as bytes fields in protobuf:

message TwoPhaseSet2 {
  optional bytes adds = 1;
  optional bytes removals = 2;
}

and use the methods otherMessageToProto and otherMessageFromBinary that are provided by the SerializationSupport trait to serialize and deserialize the GSet instances. This works with any type that has a registered Akka serializer. This is how such an serializer would look like for the TwoPhaseSet:

Scala

    import akka.actor.ExtendedActorSystem
    import akka.cluster.ddata.GSet
    import akka.cluster.ddata.protobuf.ReplicatedDataSerializer
    import akka.cluster.ddata.protobuf.SerializationSupport
    import akka.serialization.Serializer
    import docs.ddata.TwoPhaseSet
    import docs.ddata.protobuf.msg.TwoPhaseSetMessages

    class TwoPhaseSetSerializer2(val system: ExtendedActorSystem)
      extends Serializer with SerializationSupport {

      override def includeManifest: Boolean = false

      override def identifier = 99999

      val replicatedDataSerializer = new ReplicatedDataSerializer(system)

      override def toBinary(obj: AnyRef): Array[Byte] = obj match {
        case m: TwoPhaseSet ⇒ twoPhaseSetToProto(m).toByteArray
        case _ ⇒ throw new IllegalArgumentException(
          s"Can't serialize object of type ${obj.getClass}")
      }

      override def fromBinary(bytes: Array[Byte], clazz: Option[Class[_]]): AnyRef = {
        twoPhaseSetFromBinary(bytes)
      }

      def twoPhaseSetToProto(twoPhaseSet: TwoPhaseSet): TwoPhaseSetMessages.TwoPhaseSet2 = {
        val b = TwoPhaseSetMessages.TwoPhaseSet2.newBuilder()
        if (!twoPhaseSet.adds.isEmpty)
          b.setAdds(otherMessageToProto(twoPhaseSet.adds).toByteString())
        if (!twoPhaseSet.removals.isEmpty)
          b.setRemovals(otherMessageToProto(twoPhaseSet.removals).toByteString())
        b.build()
      }

      def twoPhaseSetFromBinary(bytes: Array[Byte]): TwoPhaseSet = {
        val msg = TwoPhaseSetMessages.TwoPhaseSet2.parseFrom(bytes)
        val adds =
          if (msg.hasAdds)
            otherMessageFromBinary(msg.getAdds.toByteArray).asInstanceOf[GSet[String]]
          else
            GSet.empty[String]
        val removals =
          if (msg.hasRemovals)
            otherMessageFromBinary(msg.getRemovals.toByteArray).asInstanceOf[GSet[String]]
          else
            GSet.empty[String]
        TwoPhaseSet(adds, removals)
      }
    }

Java


## Durable Storage

By default the data is only kept in memory. It is redundant since it is replicated to other nodes in the cluster, but if you stop all nodes the data is lost, unless you have saved it elsewhere.

Entries can be configured to be durable, i.e. stored on local disk on each node. The stored data will be loaded next time the replicator is started, i.e. when actor system is restarted. This means data will survive as long as at least one node from the old cluster takes part in a new cluster. The keys of the durable entries are configured with:

akka.cluster.distributed-data.durable.keys = ["a", "b", "durable*"]

Prefix matching is supported by using * at the end of a key.

All entries can be made durable by specifying:

akka.cluster.distributed-data.durable.keys = ["*"]

LMDB is the default storage implementation. It is possible to replace that with another implementation by implementing the actor protocol described in akka.cluster.ddata.DurableStore and defining the akka.cluster.distributed-data.durable.store-actor-class property for the new implementation.

The location of the files for the data is configured with:

Scala

    # Directory of LMDB file. There are two options:
    # 1. A relative or absolute path to a directory that ends with 'ddata'
    #    the full name of the directory will contain name of the ActorSystem
    #    and its remote port.
    # 2. Otherwise the path is used as is, as a relative or absolute path to
    #    a directory.
    akka.cluster.distributed-data.durable.lmdb.dir = "ddata"

Java

When running in production you may want to configure the directory to a specific path (alt 2), since the default directory contains the remote port of the actor system to make the name unique. If using a dynamically assigned port (0) it will be different each time and the previously stored data will not be loaded.

Making the data durable has of course a performance cost. By default, each update is flushed to disk before the UpdateSuccess reply is sent. For better performance, but with the risk of losing the last writes if the JVM crashes, you can enable write behind mode. Changes are then accumulated during a time period before it is written to LMDB and flushed to disk. Enabling write behind is especially efficient when performing many writes to the same key, because it is only the last value for each key that will be serialized and stored. The risk of losing writes if the JVM crashes is small since the data is typically replicated to other nodes immediately according to the given WriteConsistency.

akka.cluster.distributed-data.lmdb.write-behind-interval = 200 ms

Note that you should be prepared to receive WriteFailure as reply to an Update of a durable entry if the data could not be stored for some reason. When enabling write-behind-interval such errors will only be logged and UpdateSuccess will still be the reply to the Update.

There is one important caveat when it comes pruning of CRDT Garbage for durable data. If an old data entry that was never pruned is injected and merged with existing data after that the pruning markers have been removed the value will not be correct. The time-to-live of the markers is defined by configuration akka.cluster.distributed-data.durable.remove-pruning-marker-after and is in the magnitude of days. This would be possible if a node with durable data didn’t participate in the pruning (e.g. it was shutdown) and later started after this time. A node with durable data should not be stopped for longer time than this duration and if it is joining again after this duration its data should first be manually removed (from the lmdb directory).

## CRDT Garbage

One thing that can be problematic with CRDTs is that some data types accumulate history (garbage). For example a GCounter keeps track of one counter per node. If a GCounter has been updated from one node it will associate the identifier of that node forever. That can become a problem for long running systems with many cluster nodes being added and removed. To solve this problem the Replicator performs pruning of data associated with nodes that have been removed from the cluster. Data types that need pruning have to implement the RemovedNodePruning trait. See the API documentation of the Replicator for details.

# Samples

Several interesting samples are included and described in the tutorial named Akka Distributed Data Samples with Scala (source code)

    Low Latency Voting Service
    Highly Available Shopping Cart
    Distributed Service Registry
    Replicated Cache
    Replicated Metrics


# Limitations

There are some limitations that you should be aware of.

CRDTs cannot be used for all types of problems, and eventual consistency does not fit all domains. Sometimes you need strong consistency.

It is not intended for Big Data. The number of top level entries should not exceed 100000. When a new node is added to the cluster all these entries are transferred (gossiped) to the new node. The entries are split up in chunks and all existing nodes collaborate in the gossip, but it will take a while (tens of seconds) to transfer all entries and this means that you cannot have too many top level entries. The current recommended limit is 100000. We will be able to improve this if needed, but the design is still not intended for billions of entries.

All data is held in memory, which is another reason why it is not intended for Big Data.

When a data entry is changed the full state of that entry may be replicated to other nodes if it doesn’t support delta-CRDT. The full state is also replicated for delta-CRDTs, for example when new nodes are added to the cluster or when deltas could not be propagated because of network partitions or similar problems. This means that you cannot have too large data entries, because then the remote message size will be too large.

# Learn More about CRDTs

    The Final Causal Frontier talk by Sean Cribbs
    Eventually Consistent Data Structures talk by Sean Cribbs
    Strong Eventual Consistency and Conflict-free Replicated Data Types talk by Mark Shapiro
    A comprehensive study of Convergent and Commutative Replicated Data Types paper by Mark Shapiro et. al.


# Dependencies

To use Distributed Data you must add the following dependency in your project.

sbt

    "com.typesafe.akka" %% "akka-distributed-data" % "2.5.9"

Gradle
Maven


# Configuration

The DistributedData extension can be configured with the following properties:

# Settings for the DistributedData extension
akka.cluster.distributed-data {
  # Actor name of the Replicator actor, /system/ddataReplicator
  name = ddataReplicator

  # Replicas are running on members tagged with this role.
  # All members are used if undefined or empty.
  role = ""

  # How often the Replicator should send out gossip information
  gossip-interval = 2 s
  
  # How often the subscribers will be notified of changes, if any
  notify-subscribers-interval = 500 ms

  # Maximum number of entries to transfer in one gossip message when synchronizing
  # the replicas. Next chunk will be transferred in next round of gossip.
  max-delta-elements = 1000
  
  # The id of the dispatcher to use for Replicator actors. If not specified
  # default dispatcher is used.
  # If specified you need to define the settings of the actual dispatcher.
  use-dispatcher = ""

  # How often the Replicator checks for pruning of data associated with
  # removed cluster nodes. If this is set to 'off' the pruning feature will
  # be completely disabled.
  pruning-interval = 120 s
  
  # How long time it takes to spread the data to all other replica nodes.
  # This is used when initiating and completing the pruning process of data associated
  # with removed cluster nodes. The time measurement is stopped when any replica is 
  # unreachable, but it's still recommended to configure this with certain margin.
  # It should be in the magnitude of minutes even though typical dissemination time
  # is shorter (grows logarithmic with number of nodes). There is no advantage of 
  # setting this too low. Setting it to large value will delay the pruning process.
  max-pruning-dissemination = 300 s
  
  # The markers of that pruning has been performed for a removed node are kept for this
  # time and thereafter removed. If and old data entry that was never pruned is somehow
  # injected and merged with existing data after this time the value will not be correct.
  # This would be possible (although unlikely) in the case of a long network partition.
  # It should be in the magnitude of hours. For durable data it is configured by 
  # 'akka.cluster.distributed-data.durable.pruning-marker-time-to-live'.
 pruning-marker-time-to-live = 6 h
  
  # Serialized Write and Read messages are cached when they are sent to 
  # several nodes. If no further activity they are removed from the cache
  # after this duration.
  serializer-cache-time-to-live = 10s
  
  # Settings for delta-CRDT
  delta-crdt {
    # enable or disable delta-CRDT replication
    enabled = on
    
    # Some complex deltas grow in size for each update and above this
    # threshold such deltas are discarded and sent as full state instead.
    # This is number of elements or similar size hint, not size in bytes.
    max-delta-size = 200
  }
  
  durable {
    # List of keys that are durable. Prefix matching is supported by using * at the
    # end of a key.  
    keys = []
    
    # The markers of that pruning has been performed for a removed node are kept for this
    # time and thereafter removed. If and old data entry that was never pruned is
    # injected and merged with existing data after this time the value will not be correct.
    # This would be possible if replica with durable data didn't participate in the pruning
    # (e.g. it was shutdown) and later started after this time. A durable replica should not 
    # be stopped for longer time than this duration and if it is joining again after this
    # duration its data should first be manually removed (from the lmdb directory).
    # It should be in the magnitude of days. Note that there is a corresponding setting
    # for non-durable data: 'akka.cluster.distributed-data.pruning-marker-time-to-live'.
    pruning-marker-time-to-live = 10 d
    
    # Fully qualified class name of the durable store actor. It must be a subclass
    # of akka.actor.Actor and handle the protocol defined in 
    # akka.cluster.ddata.DurableStore. The class must have a constructor with 
    # com.typesafe.config.Config parameter.
    store-actor-class = akka.cluster.ddata.LmdbDurableStore
    
    use-dispatcher = akka.cluster.distributed-data.durable.pinned-store
    
    pinned-store {
      executor = thread-pool-executor
      type = PinnedDispatcher
    }
    
    # Config for the LmdbDurableStore
    lmdb {
      # Directory of LMDB file. There are two options:
      # 1. A relative or absolute path to a directory that ends with 'ddata'
      #    the full name of the directory will contain name of the ActorSystem
      #    and its remote port.
      # 2. Otherwise the path is used as is, as a relative or absolute path to
      #    a directory.
      #
      # When running in production you may want to configure this to a specific
      # path (alt 2), since the default directory contains the remote port of the
      # actor system to make the name unique. If using a dynamically assigned 
      # port (0) it will be different each time and the previously stored data 
      # will not be loaded.
      dir = "ddata"
      
      # Size in bytes of the memory mapped file.
      map-size = 100 MiB
      
      # Accumulate changes before storing improves performance with the
      # risk of losing the last writes if the JVM crashes.
      # The interval is by default set to 'off' to write each update immediately.
      # Enabling write behind by specifying a duration, e.g. 200ms, is especially 
      # efficient when performing many writes to the same key, because it is only 
      # the last value for each key that will be serialized and stored.  
      # write-behind-interval = 200 ms
      write-behind-interval = off
    }
  }
  
}

