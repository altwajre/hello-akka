# Routing - Overview
- Messages can be sent via a **router** to efficiently route them to destination actors, known as its **routees**. 
- A `Router` can be used inside or outside of an actor.
- You can manage the routees yourself or use a self contained router actor with configuration capabilities.
- Different routing strategies can be used, according to your application’s needs. 
- Akka comes with several useful routing strategies right out of the box. 
- It is also possible to [create your own](#custom-router).

### The routing logic shipped with Akka are:
- `akka.routing.RoundRobinRoutingLogic`
- `akka.routing.RandomRoutingLogic`
- `akka.routing.SmallestMailboxRoutingLogic`
- `akka.routing.BroadcastRoutingLogic`
- `akka.routing.ScatterGatherFirstCompletedRoutingLogic`
- `akka.routing.TailChoppingRoutingLogic`
- `akka.routing.ConsistentHashingRoutingLogic`

# A Simple Router
- See [Example code](./routing-examples/src/main/scala/routing/simple)
- The following example illustrates how to use a `Router` and manage the routees from within an actor.
```scala
class Master extends Actor {
  var router = {
    val routees = Vector.fill(5) {
      val r = context.actorOf(Props[Worker])
      context watch r
      ActorRefRoutee(r)
    }
    Router(RoundRobinRoutingLogic(), routees)
  }

  def receive = {
    case w: Work ⇒
      router.route(w, sender())
    case Terminated(a) ⇒
      router = router.removeRoutee(a)
      val r = context.actorOf(Props[Worker])
      context watch r
      router = router.addRoutee(r)
  }
}
```
- We create a `Router` and specify that it should use `RoundRobinRoutingLogic` when routing the messages to the routees.
- We create the routees as ordinary child actors wrapped in `ActorRefRoutee`. 
- We watch the routees to be able to replace them if they are terminated.
- Sending messages via the router is done with the `route` method, as is done for the `Work` messages in the example above.
- The `Router` is immutable and the `RoutingLogic` is thread safe.
- Meaning that they can also be used outside of actors. 

#### Note
- In general, any message sent to a router will be sent onwards to its routees.
- But there is one exception: 
- The special [Broadcast Messages](#broadcast-messages) will send to all of a router’s routees. 
- However, do not use _Broadcast Messages_ when you use `BalancingPool` for routees as described in [Specially Handled Messages](#specially-handled-messages).

# A Router Actor
- A router can also be created as:
    - A self contained actor that manages the routees itself.
    - And loads routing logic and other settings from configuration.
- This type of router actor comes in two distinct flavors:
    - **Pool**: 
        - The router creates routees as child actors.
        - And removes them from the router if they terminate.
    - **Group**: 
        - The routee actors are created externally to the router.
        - The router sends messages to the specified path using actor selection.
        - Without watching for termination.
- The settings for a router actor can be defined in configuration or programmatically. 
- To make an actor use an externally configurable router:
    - The `FromConfig` props wrapper must be used.
    - To denote that the actor accepts routing settings from configuration. 
- This is in contrast with [Remote Deployment](#remote-deployed-routees) where such marker props is not necessary. 
- If the props of an actor is **not wrapped** in `FromConfig`:
    - It will ignore the router section of the deployment configuration.
- You send messages to the routees via the router actor in the same way as for ordinary actors.
- The router actor forwards messages onto its routees without changing the original sender. 
- When a routee replies to a routed message, the reply will be sent to the original sender, not to the router actor.

#### Note
- In general, any message sent to a router will be sent onwards to its routees, but there are a few exceptions. 
- See [Specially Handled Messages](#specially-handled-messages).

## Pool
- The following code and configuration snippets show how to create a [round-robin](#roundrobinpool-and-roundrobingroup) router.
- It forwards messages to five `Worker` routees. 
- The routees will be created as the router’s children.
```hocon
akka.actor.deployment {
  /parent/router1 {
    router = round-robin-pool
    nr-of-instances = 5
  }
}
```
```scala
val router1: ActorRef =
  context.actorOf(FromConfig.props(Props[Worker]), "router1")
```
- Here is the same example, but with the router configuration provided programmatically instead of from configuration:
```scala
val router2: ActorRef =
  context.actorOf(RoundRobinPool(5).props(Props[Worker]), "router2")
```

## Remote Deployed Routees
- You can instruct the router to deploy its created children on a set of remote hosts. 
- Routees will be deployed in round-robin fashion. 
- In order to deploy routees remotely, wrap the router configuration in a `RemoteRouterConfig`.
- Attaching the remote addresses of the nodes to deploy to. 
- Remote deployment requires the `akka-remote` module to be included in the classpath.
```scala
val addresses = Seq(
  Address("akka.tcp", "remotesys", "otherhost", 1234),
  AddressFromURIString("akka.tcp://othersys@anotherhost:1234"))
val routerRemote = system.actorOf(
  RemoteRouterConfig(RoundRobinPool(5), addresses).props(Props[Echo]))
```

## Senders
- By default, when a routee sends a message, it will [implicitly set itself as the sender](../01-actors#tell-fire-forget).
```scala
sender() ! x // replies will go to this actor
```
- However, it is often useful for routees to set the router as a sender. 
- For example, you might want to set the router as the sender if you want to hide the details of the routees behind the router. 
- The following code snippet shows how to set the parent router as sender.
```scala
sender().tell("reply", context.parent) // replies will go back to parent
sender().!("reply")(context.parent) // alternative syntax (beware of the parens!)
```

## Supervision
- Routees that are created by a pool router will be created as the router’s children. 
- The router is therefore also the children’s supervisor.
- The supervision strategy of the router actor can be configured with the `supervisorStrategy` property of the Pool. 
- If no configuration is provided, routers default to a strategy of “always escalate”. 
- This means that errors are passed up to the router’s supervisor for handling. 
- The router’s supervisor will decide what to do about any errors.
- Note the router’s supervisor will treat the error as an error with the router itself. 
- Therefore a directive to stop or restart will cause the router itself to stop or restart. 
- The router, in turn, will cause its children to stop and restart.
- It should be mentioned that the router’s restart behavior has been overridden:
    - So that a restart will still preserve the same number of actors in the pool.
    - While still re-creating the children. 
- This means that if you have not specified `supervisorStrategy` of the router or its parent:
    - A failure in a routee will escalate to the parent of the router.
    - Which will by default restart the router.
    - Which will restart all routees.
    - It uses Escalate and does not stop routees during restart. 
- The reason is to make the default behave such that:
    - Adding `.withRouter` to a child’s definition does not change the supervision strategy applied to the child. 
- This might be an inefficiency that you can avoid by specifying the strategy when defining the router.
- Setting the strategy is easily done:
```scala
val escalator = OneForOneStrategy() {
  case e ⇒ testActor ! e; SupervisorStrategy.Escalate
}
val router = system.actorOf(RoundRobinPool(1, supervisorStrategy = escalator).props(
  routeeProps = Props[TestActor]))
```

#### Note
- If the child of a pool router terminates, the pool router will not automatically spawn a new child. 
- In the event that all children of a pool router have terminated:
    - The router will terminate itself unless it is a dynamic router.
    - E.g. using a resizer.

## Group
- Sometimes, rather than having the router actor create its routees:
    - It is desirable to create routees separately.
    - And provide them to the router for its use. 
- You can do this by passing an paths of the routees to the router’s configuration. 
- Messages will be sent with `ActorSelection` to these paths.
- The example below shows how to create a router by providing it with the path strings of three routee actors:
```hocon
akka.actor.deployment {
  /parent/router3 {
    router = round-robin-group
    routees.paths = ["/user/workers/w1", "/user/workers/w2", "/user/workers/w3"]
  }
}
``` 
```scala
val router3: ActorRef =
  context.actorOf(FromConfig.props(), "router3")
```
- Here is the same example with the router configuration provided programmatically:
```scala
val router4: ActorRef =
  context.actorOf(RoundRobinGroup(paths).props(), "router4")
```
- The routee actors are created externally from the router:
```scala
system.actorOf(Props[Workers], "workers")
```
```scala
class Workers extends Actor {
  context.actorOf(Props[Worker], name = "w1")
  context.actorOf(Props[Worker], name = "w2")
  context.actorOf(Props[Worker], name = "w3")
  // ...
```
- The paths may contain protocol and address information for actors running on remote hosts. 
- Remoting requires the `akka-remote` module to be included in the classpath.
```hocon
akka.actor.deployment {
  /parent/remoteGroup {
    router = round-robin-group
    routees.paths = [
      "akka.tcp://app@10.0.0.1:2552/user/workers/w1",
      "akka.tcp://app@10.0.0.2:2552/user/workers/w1",
      "akka.tcp://app@10.0.0.3:2552/user/workers/w1"]
  }
}
```

# Router usage
- In this section we will describe how to create the different types of router actors.
- The router actors in this section are created from within a top level actor named `parent`. 
- Note that deployment paths in the configuration starts with `/parent/` followed by the name of the router actor. 
```scala
system.actorOf(Props[Parent], "parent")
```

## `RoundRobinPool` and `RoundRobinGroup`
- Routes in a [round-robin](http://en.wikipedia.org/wiki/Round-robin) fashion to its routees.

### `RoundRobinPool` defined in configuration
```hocon
akka.actor.deployment {
  /parent/router1 {
    router = round-robin-pool
    nr-of-instances = 5
  }
}
```
```scala
val router1: ActorRef =
  context.actorOf(FromConfig.props(Props[Worker]), "router1")
```

### `RoundRobinPool` defined in code
```scala
val router2: ActorRef =
  context.actorOf(RoundRobinPool(5).props(Props[Worker]), "router2")
```

### `RoundRobinGroup` defined in configuration
```hocon
akka.actor.deployment {
  /parent/router3 {
    router = round-robin-group
    routees.paths = ["/user/workers/w1", "/user/workers/w2", "/user/workers/w3"]
  }
}
```
```scala
val router3: ActorRef =
  context.actorOf(FromConfig.props(), "router3")
```

### `RoundRobinGroup` defined in code
```scala
val paths = List("/user/workers/w1", "/user/workers/w2", "/user/workers/w3")
val router4: ActorRef =
  context.actorOf(RoundRobinGroup(paths).props(), "router4")
```

## `RandomPool` and `RandomGroup`
- This router type selects one of its routees randomly for each message.

### `RandomPool` defined in configuration
```hocon
akka.actor.deployment {
  /parent/router5 {
    router = random-pool
    nr-of-instances = 5
  }
}
```
```scala
val router5: ActorRef =
  context.actorOf(FromConfig.props(Props[Worker]), "router5")
```

### `RandomPool` defined in code
```scala
val router6: ActorRef =
  context.actorOf(RandomPool(5).props(Props[Worker]), "router6")
```

### `RandomGroup` defined in configuration
```hocon
akka.actor.deployment {
  /parent/router7 {
    router = random-group
    routees.paths = ["/user/workers/w1", "/user/workers/w2", "/user/workers/w3"]
  }
}
```
```scala
val router7: ActorRef =
  context.actorOf(FromConfig.props(), "router7")
```

### `RandomGroup` defined in code
```scala
val paths = List("/user/workers/w1", "/user/workers/w2", "/user/workers/w3")
val router8: ActorRef =
  context.actorOf(RandomGroup(paths).props(), "router8")
```

## `BalancingPool`
- A Router that will try to redistribute work from busy routees to idle routees. 
- All routees share the same mailbox.
  
#### Warning
- The BalancingPool has the property that its routees do not have truly distinct identity.
- They have different names, but talking to them will not end up at the right actor in most cases. 
- Therefore you cannot use it for workflows that require state to be kept within the routee.
- You would in this case have to include the whole state in the messages.

#### Warning
- Do not use [Broadcast Messages](#broadcast-messages) when you use BalancingPool for routers.
- See [Specially Handled Messages](#specially-handled-messages).

### `BalancingPool` defined in configuration
```hocon
akka.actor.deployment {
  /parent/router9 {
    router = balancing-pool
    nr-of-instances = 5
  }
}
```
```scala
val router9: ActorRef =
  context.actorOf(FromConfig.props(Props[Worker]), "router9")
```

### `BalancingPool` defined in code
```scala
val router10: ActorRef =
  context.actorOf(BalancingPool(5).props(Props[Worker]), "router10")
```
- Additional configuration for the balancing dispatcher can be configured:
    - Which is used by the pool.
    - In the `pool-dispatcher` section of the router deployment configuration:
```hocon
akka.actor.deployment {
  /parent/router9b {
    router = balancing-pool
    nr-of-instances = 5
    pool-dispatcher {
      attempt-teamwork = off
    }
  }
}
```
- The `BalancingPool` automatically uses a special `BalancingDispatcher` for its routees.
- Disregarding any dispatcher that is set on the routee `Props` object. 
- This is needed in order to implement the balancing semantics via sharing the same mailbox by all the routees.
- While it is not possible to change the dispatcher used by the routees:
    - It is possible to fine tune the used executor. 
- By default the `fork-join-dispatcher` is used and can be configured.
    - See [Dispatchers](../03-dispatchers). 
- In situations where the routees are expected to perform blocking operations:
    - It may be useful to replace it with a `thread-pool-executor`.
    - Hinting the number of allocated threads explicitly:
```hocon
akka.actor.deployment {
  /parent/router10b {
    router = balancing-pool
    nr-of-instances = 5
    pool-dispatcher {
      executor = "thread-pool-executor"

      # allocate exactly 5 threads for this pool
      thread-pool-executor {
        core-pool-size-min = 5
        core-pool-size-max = 5
      }
    }
  }
}
```
- It is also possible to change the mailbox used by the balancing dispatcher.
- For scenarios where the default unbounded mailbox is not well suited. 
- E.g. whether there exists the need to manage priority for each message. 
- You can then implement a priority mailbox and configure your dispatcher:
```hocon
akka.actor.deployment {
  /parent/router10c {
    router = balancing-pool
    nr-of-instances = 5
    pool-dispatcher {
      mailbox = myapp.myprioritymailbox
    }
  }
}
```
#### Note
- `BalancingDispatcher` requires a message queue that must be thread-safe for multiple concurrent consumers. 
- So it is mandatory for the message queue:
    - Backing a custom mailbox for this kind of dispatcher.
    - To implement `akka.dispatch.MultipleConsumerSemantics`. 
- See [Create your own Mailbox type](../04-mailboxes#creating-your-own-mailbox-type).

#### Note
- There is no Group variant of the BalancingPool.

## `SmallestMailboxPool`
- A Router that tries to send to the non-suspended child routee with fewest messages in mailbox. 
- The selection is done in this order:
    - Pick any idle routee (not processing message) with empty mailbox.
    - Pick any routee with empty mailbox.
    - Pick routee with fewest pending messages in mailbox.
    - Pick any remote routee, remote actors are consider lowest priority, since their mailbox size is unknown.

### `SmallestMailboxPool` defined in configuration
```hocon
akka.actor.deployment {
  /parent/router11 {
    router = smallest-mailbox-pool
    nr-of-instances = 5
  }
}
```
```scala
val router11: ActorRef =
  context.actorOf(FromConfig.props(Props[Worker]), "router11")
```

### `SmallestMailboxPool` defined in code
```scala
val router12: ActorRef =
  context.actorOf(SmallestMailboxPool(5).props(Props[Worker]), "router12")
```

## `BroadcastPool` and `BroadcastGroup`
- A broadcast router forwards the message it receives to all its routees.

### `BroadcastPool` defined in configuration
```hocon
akka.actor.deployment {
  /parent/router13 {
    router = broadcast-pool
    nr-of-instances = 5
  }
}
```
```scala
val router13: ActorRef =
  context.actorOf(FromConfig.props(Props[Worker]), "router13")
```

### `BroadcastPool` defined in code
```scala
val router14: ActorRef =
  context.actorOf(BroadcastPool(5).props(Props[Worker]), "router14")
```

### `BroadcastGroup` defined in configuration
```hocon
akka.actor.deployment {
  /parent/router15 {
    router = broadcast-group
    routees.paths = ["/user/workers/w1", "/user/workers/w2", "/user/workers/w3"]
  }
}
```
```scala
val router15: ActorRef =
  context.actorOf(FromConfig.props(), "router15")
```

### `BroadcastGroup` defined in code
```scala
val paths = List("/user/workers/w1", "/user/workers/w2", "/user/workers/w3")
val router16: ActorRef =
  context.actorOf(BroadcastGroup(paths).props(), "router16")
```

#### Note
- Broadcast routers always broadcast every message to their routees. 
- If you do not want to broadcast every message:
- Then you can use a non-broadcasting router and use [Broadcast Messages](#broadcast-messages) as needed.

## `ScatterGatherFirstCompletedPool` and `ScatterGatherFirstCompletedGroup`
- The `ScatterGatherFirstCompletedRouter` will send the message on to all its routees. 
- It then waits for first reply it gets back. 
- This result will be sent back to original sender. 
- Other replies are discarded.
- It is expecting at least one reply within a configured duration.
- Otherwise it will reply with `akka.pattern.AskTimeoutException` in a `akka.actor.Status.Failure`.
  
### `ScatterGatherFirstCompletedPool` defined in configuration
```hocon
akka.actor.deployment {
  /parent/router17 {
    router = scatter-gather-pool
    nr-of-instances = 5
    within = 10 seconds
  }
}
```
```scala
val router17: ActorRef =
  context.actorOf(FromConfig.props(Props[Worker]), "router17")
```

### `ScatterGatherFirstCompletedPool` defined in code
```scala
val router18: ActorRef =
  context.actorOf(ScatterGatherFirstCompletedPool(5, within = 10.seconds).
    props(Props[Worker]), "router18")
```

### `ScatterGatherFirstCompletedGroup` defined in configuration
```hocon
akka.actor.deployment {
  /parent/router19 {
    router = scatter-gather-group
    routees.paths = ["/user/workers/w1", "/user/workers/w2", "/user/workers/w3"]
    within = 10 seconds
  }
}
```
```scala
val router19: ActorRef =
  context.actorOf(FromConfig.props(), "router19")
```

### `ScatterGatherFirstCompletedGroup` defined in code
```scala
val paths = List("/user/workers/w1", "/user/workers/w2", "/user/workers/w3")
val router20: ActorRef =
  context.actorOf(ScatterGatherFirstCompletedGroup(
    paths,
    within = 10.seconds).props(), "router20")
```

## `TailChoppingPool` and `TailChoppingGroup`
- The `TailChoppingRouter` will first send the message to one, randomly picked, routee.
- Then after a small delay to a second routee (picked randomly from the remaining routees) and so on. 
- It waits for first reply it gets back and forwards it back to original sender. 
- Other replies are discarded.
- The goal of this router is to decrease latency:
    - By performing redundant queries to multiple routees.
    - Assuming that one of the other actors may still be faster to respond than the initial one.
- See [Doing redundant work to speed up distributed queries](http://www.bailis.org/blog/doing-redundant-work-to-speed-up-distributed-queries/).

### `TailChoppingPool` defined in configuration
```hocon
akka.actor.deployment {
  /parent/router21 {
    router = tail-chopping-pool
    nr-of-instances = 5
    within = 10 seconds
    tail-chopping-router.interval = 20 milliseconds
  }
}
```
```scala
val router21: ActorRef =
  context.actorOf(FromConfig.props(Props[Worker]), "router21")
```

### `TailChoppingPool` defined in code
```scala
val router22: ActorRef =
  context.actorOf(TailChoppingPool(5, within = 10.seconds, interval = 20.millis).
    props(Props[Worker]), "router22")
```

### `TailChoppingGroup` defined in configuration
```hocon
akka.actor.deployment {
  /parent/router23 {
    router = tail-chopping-group
    routees.paths = ["/user/workers/w1", "/user/workers/w2", "/user/workers/w3"]
    within = 10 seconds
    tail-chopping-router.interval = 20 milliseconds
  }
}
```
```scala
val router23: ActorRef =
  context.actorOf(FromConfig.props(), "router23")
```

### `TailChoppingGroup` defined in code
```scala
val paths = List("/user/workers/w1", "/user/workers/w2", "/user/workers/w3")
val router24: ActorRef =
  context.actorOf(TailChoppingGroup(
    paths,
    within = 10.seconds, interval = 20.millis).props(), "router24")
```

## `ConsistentHashingPool` and `ConsistentHashingGroup`
- The `ConsistentHashingPool` uses [consistent hashing](http://en.wikipedia.org/wiki/Consistent_hashing) to select a routee based on the sent message. 
- [This article](http://www.tom-e-white.com/2007/11/consistent-hashing.html) gives good insight into how consistent hashing is implemented.
- There is 3 ways to define what data to use for the consistent hash key:
    - You can define `hashMapping` of the router:
        - To map incoming messages to their consistent hash key. 
        - This makes the decision transparent for the sender.
    - The messages may implement `akka.routing.ConsistentHashingRouter.ConsistentHashable`:
        - The key is part of the message.
        - It is convenient to define it together with the message definition.
    - The messages can be wrapped in a `akka.routing.ConsistentHashingRouter.ConsistentHashableEnvelope`:
        - To define what data to use for the consistent hash key. 
        - The sender knows the key to use.
- These ways to define the consistent hash key can be used together and at the same time for one router. 
- The `hashMapping` is tried first.
- Code example:
```scala
class Cache extends Actor {
  var cache = Map.empty[String, String]

  def receive = {
    case Entry(key, value) ⇒ cache += (key -> value)
    case Get(key)          ⇒ sender() ! cache.get(key)
    case Evict(key)        ⇒ cache -= key
  }
}

final case class Evict(key: String)

final case class Get(key: String) extends ConsistentHashable {
  override def consistentHashKey: Any = key
}

final case class Entry(key: String, value: String)
```
```scala
def hashMapping: ConsistentHashMapping = {
  case Evict(key) ⇒ key
}

val cache: ActorRef =
  context.actorOf(ConsistentHashingPool(10, hashMapping = hashMapping).
    props(Props[Cache]), name = "cache")

cache ! ConsistentHashableEnvelope(
  message = Entry("hello", "HELLO"), hashKey = "hello")
cache ! ConsistentHashableEnvelope(
  message = Entry("hi", "HI"), hashKey = "hi")

cache ! Get("hello")
expectMsg(Some("HELLO"))

cache ! Get("hi")
expectMsg(Some("HI"))

cache ! Evict("hi")
cache ! Get("hi")
expectMsg(None)
```
- The `Get` message implements `ConsistentHashable` itself.
- The `Entry` message is wrapped in a `ConsistentHashableEnvelope`. 
- The `Evict` message is handled by the `hashMapping` partial function.

### `ConsistentHashingPool` defined in configuration
- `virtual-nodes-factor` is the number of virtual nodes per routee.
- That is used in the consistent hash node ring to make the distribution more uniform:
```hocon
akka.actor.deployment {
  /parent/router25 {
    router = consistent-hashing-pool
    nr-of-instances = 5
    virtual-nodes-factor = 10
  }
}
```
```scala
val router25: ActorRef =
  context.actorOf(FromConfig.props(Props[Worker]), "router25")
```

### `ConsistentHashingPool` defined in code
```scala
val router26: ActorRef =
  context.actorOf(
    ConsistentHashingPool(5).props(Props[Worker]),
    "router26")
```

### `ConsistentHashingGroup` defined in configuration
```hocon
akka.actor.deployment {
  /parent/router27 {
    router = consistent-hashing-group
    routees.paths = ["/user/workers/w1", "/user/workers/w2", "/user/workers/w3"]
    virtual-nodes-factor = 10
  }
}
```
```scala
val router27: ActorRef =
  context.actorOf(FromConfig.props(), "router27")
```

### `ConsistentHashingGroup` defined in code
```scala
val paths = List("/user/workers/w1", "/user/workers/w2", "/user/workers/w3")
val router28: ActorRef =
  context.actorOf(ConsistentHashingGroup(paths).props(), "router28")
```

# Specially Handled Messages
- Most messages sent to router actors will be forwarded according to the routers’ routing logic. 
- However there are a few types of messages that have special behavior.
- Note that these special messages:
    - Except for the Broadcast message.
    - Are only handled by self contained router actors.
    - Not by the `akka.routing.Router` component described in [A Simple Router](#a-simple-router).

## Broadcast Messages
- A `Broadcast` message can be used to send a message to all of a router’s routees. 
- When a router receives a `Broadcast` message:
    - It will broadcast that message’s payload to all routees.
    - No matter how that router would normally route its messages.
- The example below shows how you would use a `Broadcast` message:
```scala
router ! Broadcast("Watch out for Davy Jones' locker")
```
- In this example:
    - The router receives the `Broadcast` message.
    - Extracts its payload.
    - And then sends the payload on to all of the router’s routees. 
- It is up to each routee actor to handle the received payload message.

#### Warning
- Do not use _Broadcast Messages_ when you use `BalancingPool` for routers. 
- Routees on `BalancingPool` shares the same mailbox instance.
- Thus some routees can possibly get the broadcast message multiple times.
- While other routees get no broadcast message.

## PoisonPill Messages
- A `PoisonPill` message has special handling for all actors, including for routers. 
- When any actor receives a `PoisonPill` message:
    - That actor will be stopped. 
- See the [PoisonPill documentation](../01-actors#poisonpill).
```scala
router ! PoisonPill
```
- `PoisonPill` messages are processed by the router only. 
- `PoisonPill` messages sent to a router will not be sent on to routees.
- However, a `PoisonPill` message sent to a router may still affect its routees.
- It will stop the router and when the router stops it also stops its children. 
- Stopping children is normal actor behavior. 
- The router will stop routees that it has created as children. 
- Each child will process its current message and then stop. 
- This may lead to some messages being unprocessed. 
- See the documentation on [Stopping actors](../01-actors#stopping-actors).
- If you wish to stop a router and its routees:
    - But you would like the routees to first process all the messages currently in their mailboxes.
    - Then you should not send a `PoisonPill` message to the router. 
    - Instead you should wrap a `PoisonPill` message inside a `Broadcast` message.
    - So that each routee will receive the `PoisonPill` message. 
- Note that this will stop all routees:
    - Even if the routees aren’t children of the router.
    - I.e. even routees programmatically provided to the router.
```scala
router ! Broadcast(PoisonPill)
```
- Each routee will receive a `PoisonPill` message. 
- Each routee will continue to process its messages as normal.
- Eventually processing the PoisonPill. 
- This will cause the routee to stop. 
- After all routees have stopped:
    - The router will itself be stopped automatically.
    - Unless it is a dynamic router, e.g. using a resizer.
    
#### Note
- See [Distributing Akka Workloads - And Shutting Down Afterwards](http://bytes.codes/2013/01/17/Distributing_Akka_Workloads_And_Shutting_Down_After/). 
    
## Kill Messages
- See [Killing an Actor](../01-actors#killing-an-actor).
- When a `Kill` message is sent to a router:
    - The router processes the message internally.
    - And does not send it on to its routees. 
    - The router will throw an `ActorKilledException` and fail. 
    - It will then be either resumed, restarted or terminated, depending how it is supervised.
- Routees that are children of the router:
    - Will also be suspended.
    - And will be affected by the supervision directive that is applied to the router. 
- Routees that are not the routers children:
    - I.e. those that were created externally to the router.
    - Will not be affected.
```scala
router ! Kill
```
- There is a distinction between:
    - Killing a router, which indirectly kills its children (who happen to be routees).
    - And killing routees directly (some of whom may not be children.) 
- To kill routees directly:
    - The router should be sent a `Kill` message wrapped in a `Broadcast` message.
```scala
router ! Broadcast(Kill)
```

## Management Messages
- **Sending `akka.routing.GetRoutees` to a router actor**:
    - Will make it send back its currently used routees in a `akka.routing.Routees` message.
- **Sending `akka.routing.AddRoutee` to a router actor**:
    - Will add that routee to its collection of routees.
- **Sending `akka.routing.RemoveRoutee` to a router actor**:
    - Will remove that routee to its collection of routees.
- **Sending `akka.routing.AdjustPoolSize` to a pool router actor**:
    - Will add or remove that number of routees to its collection of routees.

- These management messages may be handled after other messages.
- So if you send `AddRoutee` immediately followed by an ordinary message:
    - You are not guaranteed that the routees have been changed when the ordinary message is routed. 
- If you need to know when the change has been applied you can send `AddRoutee` followed by `GetRoutees`.
- And when you receive the `Routees` reply you know that the preceding change has been applied.

# Dynamically Resizable Pool
- Most pools can be used with a fixed number of routees.
- Or with a resize strategy to adjust the number of routees dynamically.
- There are two types of resizers:
    - The default `Resizer`.
    - The `OptimalSizeExploringResizer`.

## Default `Resizer`
- The default resizer ramps up and down pool size based on pressure.
- Measured by the percentage of busy routees in the pool. 
- It ramps up pool size if the pressure is higher than a certain threshold.
- And backs off if the pressure is lower than certain threshold. 
- Both thresholds are configurable.

### Pool with default resizer defined in configuration
```hocon
akka.actor.deployment {
  /parent/router29 {
    router = round-robin-pool
    resizer {
      lower-bound = 2
      upper-bound = 15
      messages-per-resize = 100
    }
  }
}
```
```scala
val router29: ActorRef =
  context.actorOf(FromConfig.props(Props[Worker]), "router29")
```
- See `akka.actor.deployment.default.resizer` section of the reference [configuration](https://doc.akka.io/docs/akka/current/general/configuration.html#akka-actor).

### Pool with resizer defined in code
```scala
val resizer = DefaultResizer(lowerBound = 2, upperBound = 15)
val router30: ActorRef =
  context.actorOf(
    RoundRobinPool(5, Some(resizer)).props(Props[Worker]),
    "router30")
```

#### Note
- If you define the router in the configuration file:
- Then this value will be used instead of any programmatically sent parameters.

## `OptimalSizeExploringResizer`
- The `OptimalSizeExploringResizer` resizes the pool to an optimal size that provides the most message throughput.
- This resizer works best when you expect the pool size to performance function to be a convex function. 
- For example, when you have a CPU bound tasks:
    - The optimal size is bound to the number of CPU cores. 
- When your task is IO bound:
    - The optimal size is bound to optimal number of concurrent connections to that IO service.
    - E.g. a 4 node _ElasticSearch_ cluster may handle 4-8 concurrent requests at optimal speed.
- It achieves this by:
    - Keeping track of message throughput at each pool size.
    - And performing the following three resizing operations (one at a time) periodically:
        - Downsize if it hasn’t seen all routees ever fully utilized for a period of time.
        - Explore to a random nearby pool size to try and collect throughput metrics.
        - Optimize to a nearby pool size with a better (than any other nearby sizes) throughput metrics.
- When the pool is fully-utilized:
    - I.e. all routees are busy.
    - It randomly choose between exploring and optimizing. 
- When the pool has not been fully-utilized for a period of time:
    - It will downsize the pool to the last seen max utilization multiplied by a configurable ratio.
- By constantly exploring and optimizing:
    - The resizer will eventually walk to the optimal size and remain nearby. 
- When the optimal size changes:
    - It will start walking towards the new one.
- It keeps a performance log so it’s stateful.
- It has a larger memory footprint than the default `Resizer`. 
- The memory usage is _O(n)_ where _n_ is the number of sizes you allow:
    - I.e. upperBound - lowerBound.

### Pool with `OptimalSizeExploringResizer` defined in configuration
```hocon
akka.actor.deployment {
  /parent/router31 {
    router = round-robin-pool
    optimal-size-exploring-resizer {
      enabled = on
      action-interval = 5s
      downsize-after-underutilized-for = 72h
    }
  }
}
```
```scala
val router31: ActorRef =
  context.actorOf(FromConfig.props(Props[Worker]), "router31")
```
- See `akka.actor.deployment.default.optimal-size-exploring-resizer` section of the reference [configuration](https://doc.akka.io/docs/akka/current/general/configuration.html#akka-actor).

#### Note
- Resizing is triggered by sending messages to the actor pool.
- But it is not completed synchronously.
- Instead a message is sent to the “head” `RouterActor` to perform the size change. 
- Thus you cannot rely on resizing to instantaneously create new workers when all others are busy.
- Because the message just sent will be queued to the mailbox of a busy actor. 
- To remedy this, configure the pool to use a balancing dispatcher.
- See [Configuring Dispatchers](#configuring-dispatchers).

# How Routing is Designed within Akka
- On the surface routers look like normal actors.
- But they are actually implemented differently. 
- Routers are designed to be extremely efficient at receiving messages and passing them quickly on to routees.
- A normal actor can be used for routing messages.
- But an actor’s single-threaded processing can become a bottleneck. 
- Routers can achieve much higher throughput:
    - With an optimization to the usual message-processing pipeline.
    - That allows concurrent routing. 
- This is achieved by:
    - Embedding routers’ routing logic directly in their `ActorRef`.
    - Rather than in the router actor. 
- Messages sent to a router’s `ActorRef` can be immediately routed to the routee:
    - Bypassing the single-threaded router actor entirely.
- The cost to this is:
    - That the internals of routing code are more complicated than if routers were implemented with normal actors. 
    - Fortunately all of this complexity is invisible to consumers of the routing API. 
    - However, it is something to be aware of when implementing your own routers.

# Custom Router
- You can create your own router should you not find any of the ones provided by Akka sufficient for your needs. 
- In order to roll your own router you have to fulfill certain criteria which are explained in this section.
- Before creating your own router:
    - You should consider whether a normal actor with router-like behavior:
    - Might do the job just as well as a full-blown router. 
- As explained above, the primary benefit of routers over normal actors is their higher performance. 
- But they are somewhat more complicated to write than normal actors. 
- Therefore if lower maximum throughput is acceptable in your application you may wish to stick with traditional actors. 
- This section, however, assumes that you wish to get maximum performance and so demonstrates how you can create your own router.
- The router created in this example is replicating each message to a few destinations.
- Start with the routing logic:
```scala
class RedundancyRoutingLogic(nbrCopies: Int) extends RoutingLogic {
  val roundRobin = RoundRobinRoutingLogic()
  def select(message: Any, routees: immutable.IndexedSeq[Routee]): Routee = {
    val targets = (1 to nbrCopies).map(_ ⇒ roundRobin.select(message, routees))
    SeveralRoutees(targets)
  }
}
```
- `select` will be called for each message and in this example pick a few destinations by round-robin:
- By reusing the existing `RoundRobinRoutingLogic` and wrap the result in a `SeveralRoutees` instance. 
- `SeveralRoutees` will send the message to all of the supplied routes.
- The implementation of the routing logic must be thread safe, since it might be used outside of actors.
- A unit test of the routing logic: 
```scala
final case class TestRoutee(n: Int) extends Routee {
  override def send(message: Any, sender: ActorRef): Unit = ()
}

val logic = new RedundancyRoutingLogic(nbrCopies = 3)

val routees = for (n ← 1 to 7) yield TestRoutee(n)

val r1 = logic.select("msg", routees)
r1.asInstanceOf[SeveralRoutees].routees should be(
  Vector(TestRoutee(1), TestRoutee(2), TestRoutee(3)))

val r2 = logic.select("msg", routees)
r2.asInstanceOf[SeveralRoutees].routees should be(
  Vector(TestRoutee(4), TestRoutee(5), TestRoutee(6)))

val r3 = logic.select("msg", routees)
r3.asInstanceOf[SeveralRoutees].routees should be(
  Vector(TestRoutee(7), TestRoutee(1), TestRoutee(2)))
```
- You could stop here and use the `RedundancyRoutingLogic` with a `akka.routing.Router`.
    - See [A Simple Router](#a-simple-router).
- Let us continue and make this into a self contained, configurable, router actor.
- Create a class that extends `Pool`, `Group` or `CustomRouterConfig`. 
- That class is a factory for the routing logic and holds the configuration for the router. 
- Here we make it a `Group`:
```scala

final case class RedundancyGroup(routeePaths: immutable.Iterable[String], nbrCopies: Int) extends Group {

  def this(config: Config) = this(
    routeePaths = immutableSeq(config.getStringList("routees.paths")),
    nbrCopies = config.getInt("nbr-copies"))

  override def paths(system: ActorSystem): immutable.Iterable[String] = routeePaths

  override def createRouter(system: ActorSystem): Router =
    new Router(new RedundancyRoutingLogic(nbrCopies))

  override val routerDispatcher: String = Dispatchers.DefaultDispatcherId
}
```
- This can be used exactly as the router actors provided by Akka:
```scala
for (n ← 1 to 10) system.actorOf(Props[Storage], "s" + n)

val paths = for (n ← 1 to 10) yield ("/user/s" + n)
val redundancy1: ActorRef =
  system.actorOf(
    RedundancyGroup(paths, nbrCopies = 3).props(),
    name = "redundancy1")
redundancy1 ! "important"
```
- Note that we added a constructor in `RedundancyGroup` that takes a `Config` parameter. 
- That makes it possible to define it in configuration:
```hocon
akka.actor.deployment {
  /redundancy2 {
    router = "jdocs.routing.RedundancyGroup"
    routees.paths = ["/user/s1", "/user/s2", "/user/s3"]
    nbr-copies = 5
  }
}
```
- Note the fully qualified class name in the router property. 
- The router class must extend `akka.routing.RouterConfig` (`Pool`, `Group` or `CustomRouterConfig`).
- And have a constructor with one `com.typesafe.config.Config` parameter. 
- The deployment section of the configuration is passed to the constructor:
```scala
val redundancy2: ActorRef = system.actorOf(
  FromConfig.props(),
  name = "redundancy2")
redundancy2 ! "very important"
```

# Configuring Dispatchers
- The dispatcher for created children of the pool will be taken from `Props` as described in [Dispatchers](../03-dispatchers).
- To make it easy to define the dispatcher of the routees of the pool:
- You can define the dispatcher inline in the deployment section of the config:
```hocon
akka.actor.deployment {
  /poolWithDispatcher {
    router = random-pool
    nr-of-instances = 5
    pool-dispatcher {
      fork-join-executor.parallelism-min = 5
      fork-join-executor.parallelism-max = 5
    }
  }
}
```
- That is the only thing you need to do enable a dedicated dispatcher for a pool.
- If you use a group of actors and route to their paths:
    - Then they will still use the same dispatcher that was configured for them in their `Props`.
    - It is not possible to change an actors dispatcher after it has been created.
- The “head” router cannot always run on the same dispatcher.
- Because it does not process the same type of messages.
- Hence this special actor does not use the dispatcher configured in `Props`.
- But takes the `routerDispatcher` from the `RouterConfig` instead.
- Which defaults to the actor system’s default dispatcher. 
- All standard routers allow setting this property in their constructor or factory method.
- Custom routers have to implement the method in a suitable way.
```scala
val router: ActorRef = system.actorOf(
  // “head” router actor will run on "router-dispatcher" dispatcher
  // Worker routees will run on "pool-dispatcher" dispatcher
  RandomPool(5, routerDispatcher = "router-dispatcher").props(Props[Worker]),
  name = "poolWithDispatcher")
```
#### Note
- It is not allowed to configure the `routerDispatcher` to be a `akka.dispatch.BalancingDispatcherConfigurator`.
- Since the messages meant for the special router actor cannot be processed by any other actor.
