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

## RoundRobinPool and RoundRobinGroup
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

### RoundRobinGroup defined in configuration
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

### RoundRobinGroup defined in code
```scala
val paths = List("/user/workers/w1", "/user/workers/w2", "/user/workers/w3")
val router4: ActorRef =
  context.actorOf(RoundRobinGroup(paths).props(), "router4")
```

## RandomPool and RandomGroup
- This router type selects one of its routees randomly for each message.

### RandomPool defined in configuration
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

### RandomPool defined in code
```scala
val router6: ActorRef =
  context.actorOf(RandomPool(5).props(Props[Worker]), "router6")
```

### RandomGroup defined in configuration
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

### RandomGroup defined in code
```scala
val paths = List("/user/workers/w1", "/user/workers/w2", "/user/workers/w3")
val router8: ActorRef =
  context.actorOf(RandomGroup(paths).props(), "router8")
```

## BalancingPool
- A Router that will try to redistribute work from busy routees to idle routees. 
- All routees share the same mailbox.
  
#### Warning
- The BalancingPool has the property that its routees do not have truly distinct identity.
- They have different names, but talking to them will not end up at the right actor in most cases. 
- Therefore you cannot use it for workflows that require state to be kept within the routee.
- You would in this case have to include the whole state in the messages.
- See [SmallestMailboxPool](#smallestmailboxpool) for an alternative:
    - You can have a vertically scaling service.
    - That can interact in a stateful fashion with other services in the back-end.
    - Before replying to the original client.
    - Without placing a restriction on the message queue implementation.

#### Warning
- Do not use [Broadcast Messages](#broadcast-messages) when you use BalancingPool for routers.
- See [Specially Handled Messages](#specially-handled-messages).

### BalancingPool defined in configuration
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

### BalancingPool defined in code
```scala
val router10: ActorRef =
  context.actorOf(BalancingPool(5).props(Props[Worker]), "router10")
```

## SmallestMailboxPool



### SmallestMailboxPool defined in configuration
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


### SmallestMailboxPool defined in code
```scala
val router12: ActorRef =
  context.actorOf(SmallestMailboxPool(5).props(Props[Worker]), "router12")
```







## BroadcastPool and BroadcastGroup



### BroadcastPool defined in configuration
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



### BroadcastPool defined in code
```scala
val router14: ActorRef =
  context.actorOf(BroadcastPool(5).props(Props[Worker]), "router14")
```




### BroadcastGroup defined in configuration
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



### BroadcastGroup defined in code
```scala
val paths = List("/user/workers/w1", "/user/workers/w2", "/user/workers/w3")
val router16: ActorRef =
  context.actorOf(BroadcastGroup(paths).props(), "router16")
```







## ScatterGatherFirstCompletedPool and ScatterGatherFirstCompletedGroup

### ScatterGatherFirstCompletedPool defined in configuration
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



### ScatterGatherFirstCompletedPool defined in code
```scala
val router18: ActorRef =
  context.actorOf(ScatterGatherFirstCompletedPool(5, within = 10.seconds).
    props(Props[Worker]), "router18")
```



### ScatterGatherFirstCompletedGroup defined in configuration
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


### ScatterGatherFirstCompletedGroup defined in code
```scala
val paths = List("/user/workers/w1", "/user/workers/w2", "/user/workers/w3")
val router20: ActorRef =
  context.actorOf(ScatterGatherFirstCompletedGroup(
    paths,
    within = 10.seconds).props(), "router20")
```






## TailChoppingPool and TailChoppingGroup



### TailChoppingPool defined in configuration
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



### TailChoppingPool defined in code
```scala
val router22: ActorRef =
  context.actorOf(TailChoppingPool(5, within = 10.seconds, interval = 20.millis).
    props(Props[Worker]), "router22")
```




### TailChoppingGroup defined in configuration
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



### TailChoppingGroup defined in code
```scala
val paths = List("/user/workers/w1", "/user/workers/w2", "/user/workers/w3")
val router24: ActorRef =
  context.actorOf(TailChoppingGroup(
    paths,
    within = 10.seconds, interval = 20.millis).props(), "router24")
```








## ConsistentHashingPool and ConsistentHashingGroup



### ConsistentHashingPool defined in configuration
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



### ConsistentHashingPool defined in code
```scala
val router26: ActorRef =
  context.actorOf(
    ConsistentHashingPool(5).props(Props[Worker]),
    "router26")
```




### ConsistentHashingGroup defined in configuration
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



### ConsistentHashingGroup defined in code
```scala
val paths = List("/user/workers/w1", "/user/workers/w2", "/user/workers/w3")
val router28: ActorRef =
  context.actorOf(ConsistentHashingGroup(paths).props(), "router28")
```





# Specially Handled Messages


## Broadcast Messages





## PoisonPill Messages





## Kill Messages





## Management Messages







# Dynamically Resizable Pool

## Default Resizer
## Optimal Size Exploring Resizer


# How Routing is Designed within Akka





# Custom Router





# Configuring Dispatchers










