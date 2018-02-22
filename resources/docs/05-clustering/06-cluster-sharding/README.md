# Cluster Sharding - Overview

Cluster sharding is useful when you need to distribute actors across several nodes in the cluster and want to be able to interact with them using their logical identifier, but without having to care about their physical location in the cluster, which might also change over time.

It could for example be actors representing Aggregate Roots in Domain-Driven Design terminology. Here we call these actors “entities”. These actors typically have persistent (durable) state, but this feature is not limited to actors with persistent state.

Cluster sharding is typically used when you have many stateful actors that together consume more resources (e.g. memory) than fit on one machine. If you only have a few stateful actors it might be easier to run them on a Cluster Singleton node.

In this context sharding means that actors with an identifier, so called entities, can be automatically distributed across multiple nodes in the cluster. Each entity actor runs only at one place, and messages can be sent to the entity without requiring the sender to know the location of the destination actor. This is achieved by sending the messages via a ShardRegion actor provided by this extension, which knows how to route the message with the entity id to the final destination.

Cluster sharding will not be active on members with status WeaklyUp if that feature is enabled.
Warning

Don’t use Cluster Sharding together with Automatic Downing, since it allows the cluster to split up into two separate clusters, which in turn will result in multiple shards and entities being started, one in each separate cluster! See Downing.

# An Example

This is how an entity actor may look like:

Scala

    case object Increment
    case object Decrement
    final case class Get(counterId: Long)
    final case class EntityEnvelope(id: Long, payload: Any)

    case object Stop
    final case class CounterChanged(delta: Int)

    class Counter extends PersistentActor {
      import ShardRegion.Passivate

      context.setReceiveTimeout(120.seconds)

      // self.path.name is the entity identifier (utf-8 URL-encoded)
      override def persistenceId: String = "Counter-" + self.path.name

      var count = 0

      def updateState(event: CounterChanged): Unit =
        count += event.delta

      override def receiveRecover: Receive = {
        case evt: CounterChanged ⇒ updateState(evt)
      }

      override def receiveCommand: Receive = {
        case Increment      ⇒ persist(CounterChanged(+1))(updateState)
        case Decrement      ⇒ persist(CounterChanged(-1))(updateState)
        case Get(_)         ⇒ sender() ! count
        case ReceiveTimeout ⇒ context.parent ! Passivate(stopMessage = Stop)
        case Stop           ⇒ context.stop(self)
      }
    }

Java

The above actor uses event sourcing and the support provided in PersistentActor to store its state. It does not have to be a persistent actor, but in case of failure or migration of entities between nodes it must be able to recover its state if it is valuable.

Note how the persistenceId is defined. The name of the actor is the entity identifier (utf-8 URL-encoded). You may define it another way, but it must be unique.

When using the sharding extension you are first, typically at system startup on each node in the cluster, supposed to register the supported entity types with the ClusterSharding.start method. ClusterSharding.start gives you the reference which you can pass along.

Scala

    val counterRegion: ActorRef = ClusterSharding(system).start(
      typeName = "Counter",
      entityProps = Props[Counter],
      settings = ClusterShardingSettings(system),
      extractEntityId = extractEntityId,
      extractShardId = extractShardId)

Java

The extractEntityId and extractShardId are two application specific functions to extract the entity identifier and the shard identifier from incoming messages.

Scala

    val extractEntityId: ShardRegion.ExtractEntityId = {
      case EntityEnvelope(id, payload) ⇒ (id.toString, payload)
      case msg @ Get(id)               ⇒ (id.toString, msg)
    }

    val numberOfShards = 100

    val extractShardId: ShardRegion.ExtractShardId = {
      case EntityEnvelope(id, _) ⇒ (id % numberOfShards).toString
      case Get(id)               ⇒ (id % numberOfShards).toString
      case ShardRegion.StartEntity(id) ⇒
        // StartEntity is used by remembering entities feature
        (id.toLong % numberOfShards).toString
    }

Java

This example illustrates two different ways to define the entity identifier in the messages:

    The Get message includes the identifier itself.
    The EntityEnvelope holds the identifier, and the actual message that is sent to the entity actor is wrapped in the envelope.

Note how these two messages types are handled in the extractEntityId function shown above. The message sent to the entity actor is the second part of the tuple returned by the extractEntityId and that makes it possible to unwrap envelopes if needed.

A shard is a group of entities that will be managed together. The grouping is defined by the extractShardId function shown above. For a specific entity identifier the shard identifier must always be the same. Otherwise the entity actor might accidentally be started in several places at the same time.

Creating a good sharding algorithm is an interesting challenge in itself. Try to produce a uniform distribution, i.e. same amount of entities in each shard. As a rule of thumb, the number of shards should be a factor ten greater than the planned maximum number of cluster nodes. Less shards than number of nodes will result in that some nodes will not host any shards. Too many shards will result in less efficient management of the shards, e.g. rebalancing overhead, and increased latency because the coordinator is involved in the routing of the first message for each shard. The sharding algorithm must be the same on all nodes in a running cluster. It can be changed after stopping all nodes in the cluster.

A simple sharding algorithm that works fine in most cases is to take the absolute value of the hashCode of the entity identifier modulo number of shards. As a convenience this is provided by the ShardRegion.HashCodeMessageExtractor.

Messages to the entities are always sent via the local ShardRegion. The ShardRegion actor reference for a named entity type is returned by ClusterSharding.start and it can also be retrieved with ClusterSharding.shardRegion. The ShardRegion will lookup the location of the shard for the entity if it does not already know its location. It will delegate the message to the right node and it will create the entity actor on demand, i.e. when the first message for a specific entity is delivered.

Scala

    val counterRegion: ActorRef = ClusterSharding(system).shardRegion("Counter")
    counterRegion ! Get(123)
    expectMsg(0)

    counterRegion ! EntityEnvelope(123, Increment)
    counterRegion ! Get(123)
    expectMsg(1)

Java

A more comprehensive sample is available in the tutorial named Akka Cluster Sharding with Scala!.

# How it works

The ShardRegion actor is started on each node in the cluster, or group of nodes tagged with a specific role. The ShardRegion is created with two application specific functions to extract the entity identifier and the shard identifier from incoming messages. A shard is a group of entities that will be managed together. For the first message in a specific shard the ShardRegion requests the location of the shard from a central coordinator, the ShardCoordinator.

The ShardCoordinator decides which ShardRegion shall own the Shard and informs that ShardRegion. The region will confirm this request and create the Shard supervisor as a child actor. The individual Entities will then be created when needed by the Shard actor. Incoming messages thus travel via the ShardRegion and the Shard to the target Entity.

If the shard home is another ShardRegion instance messages will be forwarded to that ShardRegion instance instead. While resolving the location of a shard incoming messages for that shard are buffered and later delivered when the shard home is known. Subsequent messages to the resolved shard can be delivered to the target destination immediately without involving the ShardCoordinator.

Scenario 1:

    Incoming message M1 to ShardRegion instance R1.
    M1 is mapped to shard S1. R1 doesn’t know about S1, so it asks the coordinator C for the location of S1.
    C answers that the home of S1 is R1.
    R1 creates child actor for the entity E1 and sends buffered messages for S1 to E1 child
    All incoming messages for S1 which arrive at R1 can be handled by R1 without C. It creates entity children as needed, and forwards messages to them.

Scenario 2:

    Incoming message M2 to R1.
    M2 is mapped to S2. R1 doesn’t know about S2, so it asks C for the location of S2.
    C answers that the home of S2 is R2.
    R1 sends buffered messages for S2 to R2
    All incoming messages for S2 which arrive at R1 can be handled by R1 without C. It forwards messages to R2.
    R2 receives message for S2, ask C, which answers that the home of S2 is R2, and we are in Scenario 1 (but for R2).

To make sure that at most one instance of a specific entity actor is running somewhere in the cluster it is important that all nodes have the same view of where the shards are located. Therefore the shard allocation decisions are taken by the central ShardCoordinator, which is running as a cluster singleton, i.e. one instance on the oldest member among all cluster nodes or a group of nodes tagged with a specific role.

The logic that decides where a shard is to be located is defined in a pluggable shard allocation strategy. The default implementation ShardCoordinator.LeastShardAllocationStrategy allocates new shards to the ShardRegion with least number of previously allocated shards. This strategy can be replaced by an application specific implementation.

To be able to use newly added members in the cluster the coordinator facilitates rebalancing of shards, i.e. migrate entities from one node to another. In the rebalance process the coordinator first notifies all ShardRegion actors that a handoff for a shard has started. That means they will start buffering incoming messages for that shard, in the same way as if the shard location is unknown. During the rebalance process the coordinator will not answer any requests for the location of shards that are being rebalanced, i.e. local buffering will continue until the handoff is completed. The ShardRegion responsible for the rebalanced shard will stop all entities in that shard by sending the specified handOffStopMessage (default PoisonPill) to them. When all entities have been terminated the ShardRegion owning the entities will acknowledge the handoff as completed to the coordinator. Thereafter the coordinator will reply to requests for the location of the shard and thereby allocate a new home for the shard and then buffered messages in the ShardRegion actors are delivered to the new location. This means that the state of the entities are not transferred or migrated. If the state of the entities are of importance it should be persistent (durable), e.g. with Persistence, so that it can be recovered at the new location.

The logic that decides which shards to rebalance is defined in a pluggable shard allocation strategy. The default implementation ShardCoordinator.LeastShardAllocationStrategy picks shards for handoff from the ShardRegion with most number of previously allocated shards. They will then be allocated to the ShardRegion with least number of previously allocated shards, i.e. new members in the cluster. There is a configurable threshold of how large the difference must be to begin the rebalancing. This strategy can be replaced by an application specific implementation.

The state of shard locations in the ShardCoordinator is persistent (durable) with Distributed Data or Persistence to survive failures. When a crashed or unreachable coordinator node has been removed (via down) from the cluster a new ShardCoordinator singleton actor will take over and the state is recovered. During such a failure period shards with known location are still available, while messages for new (unknown) shards are buffered until the new ShardCoordinator becomes available.

As long as a sender uses the same ShardRegion actor to deliver messages to an entity actor the order of the messages is preserved. As long as the buffer limit is not reached messages are delivered on a best effort basis, with at-most once delivery semantics, in the same way as ordinary message sending. Reliable end-to-end messaging, with at-least-once semantics can be added by using AtLeastOnceDelivery in Persistence.

Some additional latency is introduced for messages targeted to new or previously unused shards due to the round-trip to the coordinator. Rebalancing of shards may also add latency. This should be considered when designing the application specific shard resolution, e.g. to avoid too fine grained shards.

# Distributed Data vs. Persistence Mode

The state of the coordinator and the state of Remembering Entities of the shards are persistent (durable) to survive failures. Distributed Data or Persistence can be used for the storage. Distributed Data is used by default.

The functionality when using the two modes is the same. If your sharded entities are not using Akka Persistence themselves it is more convenient to use the Distributed Data mode, since then you don’t have to setup and operate a separate data store (e.g. Cassandra) for persistence. Aside from that, there are no major reasons for using one mode over the the other.

It’s important to use the same mode on all nodes in the cluster, i.e. it’s not possible to perform a rolling upgrade to change this setting.
Distributed Data Mode

This mode is enabled with configuration (enabled by default):

akka.cluster.sharding.state-store-mode = ddata

The state of the ShardCoordinator will be replicated inside a cluster by the Distributed Data module with WriteMajority/ReadMajority consistency. The state of the coordinator is not durable, it’s not stored to disk. When all nodes in the cluster have been stopped the state is lost and not needed any more.

The state of Remembering Entities is also durable, i.e. it is stored to disk. The stored entities are started also after a complete cluster restart.

Cluster Sharding is using its own Distributed Data Replicator per node role. In this way you can use a subset of all nodes for some entity types and another subset for other entity types. Each such replicator has a name that contains the node role and therefore the role configuration must be the same on all nodes in the cluster, i.e. you can’t change the roles when performing a rolling upgrade.

The settings for Distributed Data is configured in the the section akka.cluster.sharding.distributed-data. It’s not possible to have different distributed-data settings for different sharding entity types.
Persistence Mode

This mode is enabled with configuration:

akka.cluster.sharding.state-store-mode = persistence

Since it is running in a cluster Persistence must be configured with a distributed journal.

# Startup after minimum number of members

It’s good to use Cluster Sharding with the Cluster setting akka.cluster.min-nr-of-members or akka.cluster.role.<role-name>.min-nr-of-members. That will defer the allocation of the shards until at least that number of regions have been started and registered to the coordinator. This avoids that many shards are allocated to the first region that registers and only later are rebalanced to other nodes.

See How To Startup when Cluster Size Reached for more information about min-nr-of-members.

# Proxy Only Mode

The ShardRegion actor can also be started in proxy only mode, i.e. it will not host any entities itself, but knows how to delegate messages to the right location. A ShardRegion is started in proxy only mode with the method ClusterSharding.startProxy method.

# Passivation

If the state of the entities are persistent you may stop entities that are not used to reduce memory consumption. This is done by the application specific implementation of the entity actors for example by defining receive timeout (context.setReceiveTimeout). If a message is already enqueued to the entity when it stops itself the enqueued message in the mailbox will be dropped. To support graceful passivation without losing such messages the entity actor can send ShardRegion.Passivate to its parent Shard. The specified wrapped message in Passivate will be sent back to the entity, which is then supposed to stop itself. Incoming messages will be buffered by the Shard between reception of Passivate and termination of the entity. Such buffered messages are thereafter delivered to a new incarnation of the entity.

# Remembering Entities

The list of entities in each Shard can be made persistent (durable) by setting the rememberEntities flag to true in ClusterShardingSettings when calling ClusterSharding.start and making sure the shardIdExtractor handles Shard.StartEntity(EntityId) which implies that a ShardId must be possible to extract from the EntityId.

Scala

    val extractShardId: ShardRegion.ExtractShardId = {
      case EntityEnvelope(id, _) ⇒ (id % numberOfShards).toString
      case Get(id)               ⇒ (id % numberOfShards).toString
      case ShardRegion.StartEntity(id) ⇒
        // StartEntity is used by remembering entities feature
        (id.toLong % numberOfShards).toString
    }

Java

When configured to remember entities, whenever a Shard is rebalanced onto another node or recovers after a crash it will recreate all the entities which were previously running in that Shard. To permanently stop entities, a Passivate message must be sent to the parent of the entity actor, otherwise the entity will be automatically restarted after the entity restart backoff specified in the configuration.

When Distributed Data mode is used the identifiers of the entities are stored in Durable Storage of Distributed Data. You may want to change the configuration of the akka.cluster.sharding.distributed-data.durable.lmdb.dir, since the default directory contains the remote port of the actor system. If using a dynamically assigned port (0) it will be different each time and the previously stored data will not be loaded.

When rememberEntities is set to false, a Shard will not automatically restart any entities after a rebalance or recovering from a crash. Entities will only be started once the first message for that entity has been received in the Shard. Entities will not be restarted if they stop without using a Passivate.

Note that the state of the entities themselves will not be restored unless they have been made persistent, e.g. with Persistence.

The performance cost of rememberEntities is rather high when starting/stopping entities and when shards are rebalanced. This cost increases with number of entities per shard and we currently don’t recommend using it with more than 10000 entities per shard.

# Supervision

If you need to use another supervisorStrategy for the entity actors than the default (restarting) strategy you need to create an intermediate parent actor that defines the supervisorStrategy to the child entity actor.

Scala

    class CounterSupervisor extends Actor {
      val counter = context.actorOf(Props[Counter], "theCounter")

      override val supervisorStrategy = OneForOneStrategy() {
        case _: IllegalArgumentException     ⇒ SupervisorStrategy.Resume
        case _: ActorInitializationException ⇒ SupervisorStrategy.Stop
        case _: DeathPactException           ⇒ SupervisorStrategy.Stop
        case _: Exception                    ⇒ SupervisorStrategy.Restart
      }

      def receive = {
        case msg ⇒ counter forward msg
      }
    }

Java

You start such a supervisor in the same way as if it was the entity actor.

Scala

    ClusterSharding(system).start(
      typeName = "SupervisedCounter",
      entityProps = Props[CounterSupervisor],
      settings = ClusterShardingSettings(system),
      extractEntityId = extractEntityId,
      extractShardId = extractShardId)

Java

Note that stopped entities will be started again when a new message is targeted to the entity.

# Graceful Shutdown

You can send the ShardRegion.GracefulShutdown message to the ShardRegion actor to hand off all shards that are hosted by that ShardRegion and then the ShardRegion actor will be stopped. You can watch the ShardRegion actor to know when it is completed. During this period other regions will buffer messages for those shards in the same way as when a rebalance is triggered by the coordinator. When the shards have been stopped the coordinator will allocate these shards elsewhere.

This is performed automatically by the Coordinated Shutdown and is therefore part of the graceful leaving process of a cluster member.

# Removal of Internal Cluster Sharding Data

The Cluster Sharding coordinator stores the locations of the shards using Akka Persistence. This data can safely be removed when restarting the whole Akka Cluster. Note that this is not application data.

There is a utility program akka.cluster.sharding.RemoveInternalClusterShardingData that removes this data.
Warning

Never use this program while there are running Akka Cluster nodes that are using Cluster Sharding. Stop all Cluster nodes before using this program.

It can be needed to remove the data if the Cluster Sharding coordinator cannot startup because of corrupt data, which may happen if accidentally two clusters were running at the same time, e.g. caused by using auto-down and there was a network partition.
Warning

Don’t use Cluster Sharding together with Automatic Downing, since it allows the cluster to split up into two separate clusters, which in turn will result in multiple shards and entities being started, one in each separate cluster! See Downing.

Use this program as a standalone Java main program:

java -classpath <jar files, including akka-cluster-sharding>
  akka.cluster.sharding.RemoveInternalClusterShardingData
    -2.3 entityType1 entityType2 entityType3

The program is included in the akka-cluster-sharding jar file. It is easiest to run it with same classpath and configuration as your ordinary application. It can be run from sbt or Maven in similar way.

Specify the entity type names (same as you use in the start method of ClusterSharding) as program arguments.

If you specify -2.3 as the first program argument it will also try to remove data that was stored by Cluster Sharding in Akka 2.3.x using different persistenceId.

# Dependencies

To use the Cluster Sharding you must add the following dependency in your project.

sbt

    "com.typesafe.akka" %% "akka-cluster-sharding" % "2.5.9"

Gradle
Maven


# Configuration

The ClusterSharding extension can be configured with the following properties. These configuration properties are read by the ClusterShardingSettings when created with a ActorSystem parameter. It is also possible to amend the ClusterShardingSettings or create it from another config section with the same layout as below. ClusterShardingSettings is a parameter to the start method of the ClusterSharding extension, i.e. each each entity type can be configured with different settings if needed.

# Settings for the ClusterShardingExtension
akka.cluster.sharding {

  # The extension creates a top level actor with this name in top level system scope,
  # e.g. '/system/sharding'
  guardian-name = sharding

  # Specifies that entities runs on cluster nodes with a specific role.
  # If the role is not specified (or empty) all nodes in the cluster are used.
  role = ""

  # When this is set to 'on' the active entity actors will automatically be restarted
  # upon Shard restart. i.e. if the Shard is started on a different ShardRegion
  # due to rebalance or crash.
  remember-entities = off

  # If the coordinator can't store state changes it will be stopped
  # and started again after this duration, with an exponential back-off
  # of up to 5 times this duration.
  coordinator-failure-backoff = 5 s

  # The ShardRegion retries registration and shard location requests to the
  # ShardCoordinator with this interval if it does not reply.
  retry-interval = 2 s

  # Maximum number of messages that are buffered by a ShardRegion actor.
  buffer-size = 100000

  # Timeout of the shard rebalancing process.
  handoff-timeout = 60 s

  # Time given to a region to acknowledge it's hosting a shard.
  shard-start-timeout = 10 s

  # If the shard is remembering entities and can't store state changes
  # will be stopped and then started again after this duration. Any messages
  # sent to an affected entity may be lost in this process.
  shard-failure-backoff = 10 s

  # If the shard is remembering entities and an entity stops itself without
  # using passivate. The entity will be restarted after this duration or when
  # the next message for it is received, which ever occurs first.
  entity-restart-backoff = 10 s

  # Rebalance check is performed periodically with this interval.
  rebalance-interval = 10 s

  # Absolute path to the journal plugin configuration entity that is to be
  # used for the internal persistence of ClusterSharding. If not defined
  # the default journal plugin is used. Note that this is not related to
  # persistence used by the entity actors.
  # Only used when state-store-mode=persistence
  journal-plugin-id = ""

  # Absolute path to the snapshot plugin configuration entity that is to be
  # used for the internal persistence of ClusterSharding. If not defined
  # the default snapshot plugin is used. Note that this is not related to
  # persistence used by the entity actors.
  # Only used when state-store-mode=persistence
  snapshot-plugin-id = ""

  # Defines how the coordinator stores its state. Same is also used by the
  # shards for rememberEntities.
  # Valid values are "ddata" or "persistence". 
  state-store-mode = "ddata"

  # The shard saves persistent snapshots after this number of persistent
  # events. Snapshots are used to reduce recovery times.
  # Only used when state-store-mode=persistence
  snapshot-after = 1000

  # The shard deletes persistent events (messages and snapshots) after doing snapshot
  # keeping this number of old persistent batches.
  # Batch is of size `snapshot-after`.
  # When set to 0 after snapshot is successfully done all messages with equal or lower sequence number will be deleted.
  # Default value of 2 leaves last maximum 2*`snapshot-after` messages and 3 snapshots (2 old ones + fresh snapshot)
  keep-nr-of-batches = 2

  # Setting for the default shard allocation strategy
  least-shard-allocation-strategy {
    # Threshold of how large the difference between most and least number of
    # allocated shards must be to begin the rebalancing.
    rebalance-threshold = 10

    # The number of ongoing rebalancing processes is limited to this number.
    max-simultaneous-rebalance = 3
  }

  # Timeout of waiting the initial distributed state (an initial state will be queried again if the timeout happened)
  # Only used when state-store-mode=ddata
  waiting-for-state-timeout = 5 s

  # Timeout of waiting for update the distributed state (update will be retried if the timeout happened)
  # Only used when state-store-mode=ddata
  updating-state-timeout = 5 s

  # The shard uses this strategy to determines how to recover the underlying entity actors. The strategy is only used
  # by the persistent shard when rebalancing or restarting. The value can either be "all" or "constant". The "all"
  # strategy start all the underlying entity actors at the same time. The constant strategy will start the underlying
  # entity actors at a fix rate. The default strategy "all".
  entity-recovery-strategy = "all"

  # Default settings for the constant rate entity recovery strategy
  entity-recovery-constant-rate-strategy {
    # Sets the frequency at which a batch of entity actors is started.
    frequency = 100 ms
    # Sets the number of entity actors to be restart at a particular interval
    number-of-entities = 5
  }

  # Settings for the coordinator singleton. Same layout as akka.cluster.singleton.
  # The "role" of the singleton configuration is not used. The singleton role will
  # be the same as "akka.cluster.sharding.role".
  coordinator-singleton = ${akka.cluster.singleton}
  
  # Settings for the Distributed Data replicator. 
  # Same layout as akka.cluster.distributed-data.
  # The "role" of the distributed-data configuration is not used. The distributed-data
  # role will be the same as "akka.cluster.sharding.role".
  # Note that there is one Replicator per role and it's not possible
  # to have different distributed-data settings for different sharding entity types.
  # Only used when state-store-mode=ddata
  distributed-data = ${akka.cluster.distributed-data}
  distributed-data {
    # minCap parameter to MajorityWrite and MajorityRead consistency level.
    majority-min-cap = 5
    durable.keys = ["shard-*"]
    
    # When using many entities with "remember entities" the Gossip message
    # can become to large if including to many in same message. Limit to
    # the same number as the number of ORSet per shard.
    max-delta-elements = 5
    
  }

  # The id of the dispatcher to use for ClusterSharding actors.
  # If not specified default dispatcher is used.
  # If specified you need to define the settings of the actual dispatcher.
  # This dispatcher for the entity actors is defined by the user provided
  # Props, i.e. this dispatcher is not used for the entity actors.
  use-dispatcher = ""
}

Custom shard allocation strategy can be defined in an optional parameter to ClusterSharding.start. See the API documentation of ShardAllocationStrategy for details of how to implement a custom shard allocation strategy.

# Inspecting cluster sharding state

Two requests to inspect the cluster state are available:

ShardRegion.GetShardRegionState which will return a ShardRegion.CurrentShardRegionState that contains the identifiers of the shards running in a Region and what entities are alive for each of them.

ShardRegion.GetClusterShardingStats which will query all the regions in the cluster and return a ShardRegion.ClusterShardingStats containing the identifiers of the shards running in each region and a count of entities that are alive in each shard.

The type names of all started shards can be acquired via ClusterSharding.shardTypeNames .

The purpose of these messages is testing and monitoring, they are not provided to give access to directly sending messages to the individual entities.

# Rolling upgrades

When doing rolling upgrades special care must be taken to not change any of the following aspects of sharding:

    the extractShardId function
    the role that the shard regions run on
    the persistence mode

If any one of these needs a change it will require a full cluster restart.
