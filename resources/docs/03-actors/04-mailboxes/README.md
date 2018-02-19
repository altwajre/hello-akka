# Mailboxes - Overview
- An Akka `Mailbox` holds the messages that are destined for an `Actor`. 
- Normally each `Actor` has its own mailbox, but with for example a `BalancingPool` all routees will share a single mailbox instance.

# Mailbox Selection

## Requiring a Message Queue Type for an Actor
- It is possible to require a certain type of message queue for a certain type of actor:
- By having that actor extend the parameterized trait `RequiresMessageQueue`:
```scala
class MyBoundedActor extends MyActor
  with RequiresMessageQueue[BoundedMessageQueueSemantics]
```
- The type parameter to the `RequiresMessageQueue` trait needs to be mapped to a mailbox in configuration like this:
```hocon
bounded-mailbox {
  mailbox-type = "akka.dispatch.BoundedMailbox"
  mailbox-capacity = 1000
  mailbox-push-timeout-time = 10s
}

akka.actor.mailbox.requirements {
  "akka.dispatch.BoundedMessageQueueSemantics" = bounded-mailbox
}
```
- Now every time you create an actor of type `MyBoundedActor` it will try to get a bounded mailbox. 
- If the actor has a different mailbox configured in deployment:
    - Either directly or via a dispatcher with a specified mailbox type.
    - Then that will override this mapping.
- The type of the queue in the mailbox created for an actor will be checked against the required type in the trait.
- If the queue doesn’t implement the required type then actor creation will fail.

## Requiring a Message Queue Type for a Dispatcher
- A dispatcher may also have a requirement for the mailbox type used by the actors running on it. 
- An example is the `BalancingDispatcher`:
    - Which requires a message queue that is thread-safe for multiple concurrent consumers. 
- Such a requirement is formulated within the dispatcher configuration section like this:
```hocon
my-dispatcher {
  mailbox-requirement = org.example.MyInterface
}
```
- The given requirement names a class or interface which will then be ensured to be a supertype of the message queue’s implementation. 
- In case of a conflict:
    - E.g. if the actor requires a mailbox type which does not satisfy this requirement.
    - Then actor creation will fail.

## How the Mailbox Type is Selected
- When an actor is created, the `ActorRefProvider` first determines the dispatcher which will execute it. 
- Then the mailbox is determined as follows:

### Priority 1:
- If the actor’s deployment configuration section contains a `mailbox` key:
- Then that names a configuration section describing the mailbox type to be used.

### Priority 2:
- If the actor’s `Props` contains a mailbox selection:
- I.e. `withMailbox` was called on it.
- Then that names a configuration section describing the mailbox type to be used.

### Priority 3:
- If the dispatcher’s configuration section contains a `mailbox-type` key:
- The same section will be used to configure the mailbox type.

### Priority 4:
- If the actor requires a mailbox type as described above:
- Then the mapping for that requirement will be used to determine the mailbox type to be used.
- If that fails then the dispatcher’s requirement (if any) will be tried instead.

### Priority 5:
- If the dispatcher requires a mailbox type as described above:
- Then the mapping for that requirement will be used to determine the mailbox type to be used.

### Priority 6:
- The default mailbox `akka.actor.default-mailbox` will be used.

## Default Mailbox
- When the mailbox is not specified as described above the default mailbox is used. 
- By default it is an unbounded mailbox, which is backed by a `java.util.concurrent.ConcurrentLinkedQueue`.
- `SingleConsumerOnlyUnboundedMailbox` is an even more efficient mailbox:
    - It can be used as the default mailbox.
    - But it cannot be used with a `BalancingDispatcher`.
- Configuration of `SingleConsumerOnlyUnboundedMailbox` as default mailbox:
```hocon
akka.actor.default-mailbox {
  mailbox-type = "akka.dispatch.SingleConsumerOnlyUnboundedMailbox"
}
```

## Which Configuration is passed to the Mailbox Type
- Each mailbox type is implemented by a class which:
    - Extends `MailboxType`.
    - And takes two constructor arguments: a `ActorSystem.Settings` object and a `Config` section. 
- The latter is computed by:
    - Obtaining the named configuration section from the actor system’s configuration.
    - Overriding its `id` key with the configuration path of the mailbox type.
    - And adding a fall-back to the default mailbox configuration section.

# Builtin Mailbox Implementations

- Bounded mailbox implementations which will:
    - Block the sender if the capacity is reached.
    - And configured with non-zero `mailbox-push-timeout-time`.

## `UnboundedMailbox`:
- The default mailbox.
- **Backed by:**  a `java.util.concurrent.ConcurrentLinkedQueue`
- **Blocking:** No
- **Bounded:** No
- **Configuration name:** `unbounded` or `akka.dispatch.UnboundedMailbox`

## `SingleConsumerOnlyUnboundedMailbox`: 
- This queue may or may not be faster than the default one depending on your use-case.
- Be sure to benchmark properly!
- **Backed by:**  a Multiple-Producer Single-Consumer queue, cannot be used with `BalancingDispatcher`.
- **Blocking:** No.
- **Bounded:** No.
- **Configuration name:** `akka.dispatch.SingleConsumerOnlyUnboundedMailbox`.

## `NonBlockingBoundedMailbox`:
- **Backed by:**  a very efficient Multiple-Producer Single-Consumer queue.
- **Blocking:** No (discards overflowing messages into deadLetters).
- **Bounded:** Yes.
- **Configuration name:** `akka.dispatch.NonBlockingBoundedMailbox`.

## `UnboundedControlAwareMailbox`:
- Delivers messages that extend `akka.dispatch.ControlMessage` with higher priority.
- **Backed by:**  two `java.util.concurrent.ConcurrentLinkedQueue`.
- **Blocking:** No.
- **Bounded:** No.
- **Configuration name:** `akka.dispatch.UnboundedControlAwareMailbox`.

## `UnboundedPriorityMailbox`:
- **Backed by:**  a `java.util.concurrent.PriorityBlockingQueue`
    - Delivery order for messages of equal priority is undefined.
    - Contrast with the `UnboundedStablePriorityMailbox`.
- **Blocking:** No.
- **Bounded:** No.
- **Configuration name:** `akka.dispatch.UnboundedPriorityMailbox`.

## `UnboundedStablePriorityMailbox`:
- **Backed by:**  a `java.util.concurrent.PriorityBlockingQueue` wrapped in an `akka.util.PriorityQueueStabilizer`.
    - FIFO order is preserved for messages of equal priority. 
    - Contrast with the `UnboundedPriorityMailbox`.
- **Blocking:** No.
- **Bounded:** No.
- **Configuration name:** `akka.dispatch.UnboundedStablePriorityMailbox`.

------------------------------------------------------------------------------------------------------------------------

- The following mailboxes should only be used with zero `mailbox-push-timeout-time`.

## `BoundedMailbox`:
- **Backed by:** a `java.util.concurrent.LinkedBlockingQueue`.
- **Blocking:**  Yes if used with non-zero `mailbox-push-timeout-time`, otherwise No.
- **Bounded:**  Yes.
- **Configuration name** : `bounded` or `akka.dispatch.BoundedMailbox`.
## `BoundedPriorityMailbox`:
- **Backed by:** a `java.util.PriorityQueue` wrapped in an `akka.util.BoundedBlockingQueue`.
    - Delivery order for messages of equal priority is undefined - contrast with the `BoundedStablePriorityMailbox`.
- **Blocking:**  Yes if used with non-zero `mailbox-push-timeout-time`, otherwise No.
- **Bounded:**  Yes.
- **Configuration name** : `akka.dispatch.BoundedPriorityMailbox`.
## `BoundedStablePriorityMailbox`:
- **Backed by:** a `java.util.PriorityQueue` wrapped in an `akka.util.PriorityQueueStabilizer` and an `akka.util.BoundedBlockingQueue`.
    - FIFO order is preserved for messages of equal priority.
    - Contrast with the BoundedPriorityMailbox.
- **Blocking:**  Yes if used with non-zero `mailbox-push-timeout-time`, otherwise No.
- **Bounded:**  Yes.
- **Configuration name** : `akka.dispatch.BoundedStablePriorityMailbox`.
## `BoundedControlAwareMailbox`:
- Delivers messages that extend `akka.dispatch.ControlMessage` with higher priority.
- **Backed by:** two `java.util.concurrent.ConcurrentLinkedQueue` and blocking on enqueue if capacity has been reached.
- **Blocking:**  Yes if used with non-zero `mailbox-push-timeout-time`, otherwise No.
- **Bounded:**  Yes.
- **Configuration name** : `akka.dispatch.BoundedControlAwareMailbox`.

# Mailbox configuration examples

## `PriorityMailbox`
```scala
// We inherit, in this case, from UnboundedStablePriorityMailbox
// and seed it with the priority generator
class MyPrioMailbox(settings: ActorSystem.Settings, config: Config)
  extends UnboundedStablePriorityMailbox(
    // Create a new PriorityGenerator, lower prio means more important
    PriorityGenerator {
      // 'highpriority messages should be treated first if possible
      case 'highpriority ⇒ 0

      // 'lowpriority messages should be treated last if possible
      case 'lowpriority  ⇒ 2

      // PoisonPill when no other left
      case PoisonPill    ⇒ 3

      // We default to 1, which is in between high and low
      case otherwise     ⇒ 1
    })
```
- And then add it to the configuration:
```hocon
prio-mailbox {
  mailbox-type = "docs.dispatcher.DispatcherDocSpec$MyPrioMailbox"
  //Other dispatcher configuration goes here
}
```
- And then an example on how you would use it:
```scala
// We create a new Actor that just prints out what it processes
class Logger extends Actor {
  val log: LoggingAdapter = Logging(context.system, this)

  self ! 'lowpriority
  self ! 'lowpriority
  self ! 'highpriority
  self ! 'pigdog
  self ! 'pigdog2
  self ! 'pigdog3
  self ! 'highpriority
  self ! PoisonPill

  def receive = {
    case x ⇒ log.info(x.toString)
  }
}
val a = system.actorOf(Props(classOf[Logger]).withDispatcher("prio-mailbox"))

/*
 * Logs:
 * 'highpriority
 * 'highpriority
 * 'pigdog
 * 'pigdog2
 * 'pigdog3
 * 'lowpriority
 * 'lowpriority
 */
```
- It is also possible to configure a mailbox type directly like this:
```hocon
prio-mailbox {
  mailbox-type = "docs.dispatcher.DispatcherDocSpec$MyPrioMailbox"
  //Other mailbox configuration goes here
}

akka.actor.deployment {
  /priomailboxactor {
    mailbox = prio-mailbox
  }
}
```
- And then use it either from deployment like this:
```scala
val myActor = context.actorOf(Props[MyActor], "priomailboxactor")
```
- Or code like this:
```scala
val myActor = context.actorOf(Props[MyActor].withMailbox("prio-mailbox"))
```

## `ControlAwareMailbox`
- A `ControlAwareMailbox` can be very useful if an actor needs to be able to receive control messages immediately.
- No matter how many other messages are already in its mailbox.
- It can be configured like this:
```hocon
control-aware-mailbox {
  mailbox-type = "akka.dispatch.UnboundedControlAwareMailbox"
  //Other dispatcher configuration goes here
}
```
- Control messages need to extend the `ControlMessage` trait:
```scala
case object MyControlMessage extends ControlMessage
```
- And then an example on how you would use it:
```scala
// We create a new Actor that just prints out what it processes
class Logger extends Actor {
  val log: LoggingAdapter = Logging(context.system, this)

  self ! 'foo
  self ! 'bar
  self ! MyControlMessage
  self ! PoisonPill

  def receive = {
    case x ⇒ log.info(x.toString)
  }
}
val a = system.actorOf(Props(classOf[Logger]).withDispatcher("control-aware-mailbox"))

/*
 * Logs:
 * MyControlMessage
 * 'foo
 * 'bar
 */
```





















# Creating your own Mailbox type





# Special Semantics of system.actorOf










