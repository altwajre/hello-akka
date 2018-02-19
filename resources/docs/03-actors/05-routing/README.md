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




## Supervision





## Group






# Router usage


## RoundRobinPool and RoundRobinGroup





## RandomPool and RandomGroup





## BalancingPool





## SmallestMailboxPool





## BroadcastPool and BroadcastGroup





## ScatterGatherFirstCompletedPool and ScatterGatherFirstCompletedGroup





## TailChoppingPool and TailChoppingGroup





## ConsistentHashingPool and ConsistentHashingGroup







# Specially Handled Messages


## Broadcast Messages





## PoisonPill Messages





## Kill Messages





## Management Messages







# Dynamically Resizable Pool





# How Routing is Designed within Akka





# Custom Router





# Configuring Dispatchers










