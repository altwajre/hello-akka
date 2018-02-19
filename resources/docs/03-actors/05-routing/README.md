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





# Router usage





# Specially Handled Messages


## Broadcast Messages





## PoisonPill Messages





## Kill Messages





## Management Messages







# Dynamically Resizable Pool





# How Routing is Designed within Akka





# Custom Router





# Configuring Dispatchers










