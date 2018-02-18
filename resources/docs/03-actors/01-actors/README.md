# Actors - Overview
- The _Actor Model_ provides a higher level of abstraction for writing concurrent and distributed systems. 
- It alleviates the developer from having to deal with explicit locking and thread management:
    - Making it easier to write correct concurrent and parallel systems. 
- Actors were defined in the 1973 paper by Carl Hewitt but have been popularized by the Erlang language.
    - And used for example at Ericsson with great success to build highly concurrent and reliable telecom systems.
- The API of Akka’s Actors is similar to Scala Actors which has borrowed some of its syntax from Erlang.

# Creating Actors

## Defining an Actor class
- Actors are implemented by extending the `Actor` base trait and implementing the `receive` method. 
- The `receive` method:
    - Has the type `PartialFunction[Any, Unit]`.
    - Should define a series of case statements.
    - Defines which messages your Actor can handle.
    - Using standard Scala pattern matching.
    - Implementation of how the messages should be processed.
- Here is an example:
```scala
class MyActor extends Actor {
  val log = Logging(context.system, this)

  def receive = {
    case "test" ⇒ log.info("received test")
    case _      ⇒ log.info("received unknown message")
  }
}
```
- The Akka Actor `receive` message loop is exhaustive. 
- This means that you need to provide a pattern match for all messages that it can accept.
    - If you want to be able to handle unknown messages then you need to have a default case as in the example above. 
    - Otherwise an `akka.actor.UnhandledMessage(message, sender, recipient)` will be published to the `ActorSystem`’s `EventStream`.
- The return type of the behavior defined above is `Unit`.
- If the actor shall reply to the received message then this must be done explicitly as explained below.
- The result of the `receive` method is a `PartialFunction` object.
    - It is stored within the actor as its “initial behavior”.
    - See [Become/Unbecome(TODO).

## Props
- `Props` is a configuration class to specify options for the creation of actors.
- Think of it as an immutable and thus freely shareable recipe for creating an actor.
- Including associated deployment information (e.g. which dispatcher to use, see more below). 
- Here are some examples of how to create a `Props` instance:
```scala
val props1 = Props[MyActor]
val props2 = Props(new ActorWithArgs("arg")) // careful, see below
val props3 = Props(classOf[ActorWithArgs], "arg") // no support for value class arguments
```
- The second variant shows how to pass constructor arguments to the `Actor` being created.
    - It should only be used outside of actors as explained below.
- The last line shows a possibility to pass constructor arguments regardless of the context it is being used in. 
    - The presence of a matching constructor is verified during construction of the `Props` object.
    - This will result in an `IllegalArgumentException` if no or multiple matching constructors are found.
- See [Example 1](./actors-examples/src/main/scala/actors/example1)

### Dangerous Variants
```scala
// NOT RECOMMENDED within another actor:
// encourages to close over enclosing class
val props7 = Props(new MyActor)
```
- This method is not recommended to be used within another actor because it encourages to close over the enclosing scope.
    - Which will result in non-serializable `Props` and possibly race conditions (breaking the actor encapsulation). 
- On the other hand using this variant in a `Props` factory in the actor’s companion object is completely fine.
- Declaring one actor within another is very dangerous and breaks actor encapsulation.
    - Never pass an actor’s `this` reference into `Props`!

### Edge cases
- There are two edge cases in actor creation with `Props`:

#### An actor with `AnyVal` arguments
```scala
case class MyValueClass(v: Int) extends AnyVal
```
```scala
class ValueActor(value: MyValueClass) extends Actor {
  def receive = {
    case multiplier: Long ⇒ sender() ! (value.v * multiplier)
  }
}
val valueClassProp = Props(classOf[ValueActor], MyValueClass(5)) // Unsupported
```

#### An actor with default constructor values
```scala
class DefaultValueActor(a: Int, b: Int = 5) extends Actor {
  def receive = {
    case x: Int ⇒ sender() ! ((a + x) * b)
  }
}

val defaultValueProp1 = Props(classOf[DefaultValueActor], 2.0) // Unsupported

class DefaultValueActor2(b: Int = 5) extends Actor {
  def receive = {
    case x: Int ⇒ sender() ! (x * b)
  }
}
val defaultValueProp2 = Props[DefaultValueActor2] // Unsupported
val defaultValueProp3 = Props(classOf[DefaultValueActor2]) // Unsupported
```
- In both cases an `IllegalArgumentException` will be thrown stating no matching constructor could be found.
- The next section explains the recommended ways to create `Actor` props, which safe-guards against these edge cases.

### Recommended Practices
- It is a good idea to provide factory methods on the companion object of each `Actor`.
    - Which help keeping the creation of suitable `Props` as close to the actor definition as possible. 
- This also avoids the pitfalls associated with using the `Props.apply(...)` method which takes a _by-name argument_.
- Within a companion object, the given code block will not retain a reference to its enclosing scope:
```scala
object DemoActor {
  /**
   * Create Props for an actor of this type.
   *
   * @param magicNumber The magic number to be passed to this actor’s constructor.
   * @return a Props for creating this actor, which can then be further configured
   *         (e.g. calling `.withDispatcher()` on it)
   */
  def props(magicNumber: Int): Props = Props(new DemoActor(magicNumber))
}

class DemoActor(magicNumber: Int) extends Actor {
  def receive = {
    case x: Int ⇒ sender() ! (x + magicNumber)
  }
}

class SomeOtherActor extends Actor {
  // Props(new DemoActor(42)) would not be safe
  context.actorOf(DemoActor.props(42), "demo")
  // ...
}
```
- Another good practice is to declare what messages an `Actor` can receive in the companion object of the `Actor`.
- This makes it easier to know what it can receive:
```scala
object MyActor {
  case class Greeting(from: String)
  case object Goodbye
}
class MyActor extends Actor with ActorLogging {
  import MyActor._
  def receive = {
    case Greeting(greeter) ⇒ log.info(s"I was greeted by $greeter.")
    case Goodbye           ⇒ log.info("Someone said goodbye to me.")
  }
}
```

## Creating Actors with Props
- Actors are created by passing a `Props` instance into the `actorOf` factory method which is available on `ActorSystem` and `ActorContext`.
```scala
// ActorSystem is a heavy object: create only one per application
val system = ActorSystem("mySystem")
val myActor = system.actorOf(Props[MyActor], "myactor2")
```
- Using the `ActorSystem` will create top-level actors, supervised by the actor system’s provided guardian actor.
- Using an actor’s context will create a child actor.
```scala
class FirstActor extends Actor {
  val child = context.actorOf(Props[MyActor], name = "myChild")
  def receive = {
    case x ⇒ sender() ! x
  }
}
```
- It is recommended to create a hierarchy of children, grand-children and so on.
    - Such that it fits the logical failure-handling structure of the application.
    - See [Actor Systems](../../02-general-concepts/02-actor-system).
- The call to `actorOf` returns an instance of `ActorRef`. 
- This is a handle to the actor instance and the only way to interact with it. 
- The `ActorRef` is :
    - Immutable and has a one to one relationship with the `Actor` it represents. 
    - Serializable and network-aware. 
- This means that you can serialize it, send it over the wire and use it on a remote host.
    - It will still be representing the same `Actor` on the original node, across the network.
- The `name` parameter is optional, but you should preferably name your actors.
    - That is used in log messages and for identifying actors. 
    - It must not be empty or start with `$`.
    - It may contain URL encoded characters (eg. `%20` for a blank space). 
    - If the given name is already in use by another child to the same parent an `InvalidActorNameException` is thrown.
- Actors are automatically started asynchronously when created.

### Value classes as constructor arguments
- The recommended way to instantiate actor props uses reflection at runtime to determine the correct actor constructor to be invoked.
- Due to technical limitations it is not supported when said constructor takes arguments that are value classes. 
- In these cases you should either unpack the arguments or create the props by calling the constructor manually:
```scala
class Argument(val value: String) extends AnyVal
class ValueClassActor(arg: Argument) extends Actor {
  def receive = { case _ ⇒ () }
}

object ValueClassActor {
  def props1(arg: Argument) = Props(classOf[ValueClassActor], arg) // fails at runtime
  def props2(arg: Argument) = Props(classOf[ValueClassActor], arg.value) // ok
  def props3(arg: Argument) = Props(new ValueClassActor(arg)) // ok
}
```

## Dependency Injection
- If your `Actor` has a constructor that takes parameters then those need to be part of the `Props` as well.
- There are cases when a factory method must be used.
- For example when the actual constructor arguments are determined by a dependency injection framework:
```scala
class DependencyInjector(applicationContext: AnyRef, beanName: String)
  extends IndirectActorProducer {

  override def actorClass = classOf[Actor]
  override def produce =
    new Echo(beanName)

  def this(beanName: String) = this("", beanName)
}

val actorRef = system.actorOf(
  Props(classOf[DependencyInjector], applicationContext, "hello"),
  "helloBean")
```
- You might be tempted at times to offer an `IndirectActorProducer` which always returns the same instance.
    - E.g. by using a `lazy val`. 
    - This is not supported, as it goes against the meaning of an actor restart, see [What Restarting Means](../../02-general-concepts/04-supervision-and-monitoring#what-restarting-means).
- When using a dependency injection framework, actor beans **MUST NOT** have singleton scope.
- See [Using Akka with Dependency Injection](http://letitcrash.com/post/55958814293/akka-dependency-injection).

## The Inbox
- When writing code outside of actors which shall communicate with actors:
    - The `ask` pattern can be a solution (see below).
    - There are two things it cannot do: 
        - Receiving multiple replies (e.g. by subscribing an `ActorRef` to a notification service).
        - Watching other actors’ lifecycle. 
- For these purposes there is the Inbox class:
```scala
implicit val i = inbox()
echo ! "hello"
i.receive() should ===("hello")
```
- There is an _implicit conversion_ from inbox to actor reference.
- In this example the sender reference will be that of the actor hidden away within the inbox. 
- This allows the reply to be received on the last line. 
- Watching an actor is quite simple as well:
```scala
val target = // some actor
val i = inbox()
i watch target
```

# Actor API
- The `Actor` trait defines only one abstract method, the above mentioned `receive`, which implements the behavior of the actor.
- If the current actor behavior does not match a received message, `unhandled` is called.
    - Which by default publishes an `akka.actor.UnhandledMessage(message, sender, recipient)` on the actor system’s event stream.
    - Set configuration item `akka.actor.debug.unhandled` to `on` to have them converted into actual Debug messages.
- In addition, it offers:
    - `self` reference: To the `ActorRef` of the actor.
    - `sender` reference: Sender Actor of the last received message, typically used as described in [`Reply to messages`](#reply-to-messages).
    - `supervisorStrategy` user overridable definition: The strategy to use for supervising child actors.
- This strategy is typically declared inside the actor:
    - In order to have access to the actor’s internal state within the decider function. 
    - Since failure is communicated as a message sent to the supervisor and processed like other messages.
    - All values and variables within the actor are available.
    - As is the sender reference.
    - Which will be the immediate child reporting the failure.
    - If the original failure occurred within a distant descendant it is still reported one level up at a time.
- `context` exposes contextual information for the actor and the current message, such as:
    - Factory methods to create child actors (actorOf).
    - System that the actor belongs to.
    - Parent supervisor.
    - Supervised children.
    - Lifecycle monitoring.
    - Hotswap behavior stack as described in [Become/Unbecome](#become/unbecome).
- You can import the members in the `context` to avoid prefixing access with `context`:
```scala
class FirstActor extends Actor {
  import context._
  val myActor = actorOf(Props[MyActor], name = "myactor")
  def receive = {
    case x ⇒ myActor ! x
  }
}
```
- The remaining visible methods are user-overridable life-cycle hooks which are described in the following:
```scala
def preStart(): Unit = ()

def postStop(): Unit = ()

def preRestart(reason: Throwable, message: Option[Any]): Unit = {
  context.children foreach { child ⇒
    context.unwatch(child)
    context.stop(child)
  }
  postStop()
}

def postRestart(reason: Throwable): Unit = {
  preStart()
}
```
- The implementations shown above are the defaults provided by the `Actor` trait. 

## Actor Lifecycle
![Actor Lifecycle](https://doc.akka.io/docs/akka/current/images/actor_lifecycle.png)
- A path in an actor system represents a “place” which might be occupied by a living actor. 
- Initially (apart from system initialized actors) a path is empty. 
- When `actorOf()` is called it assigns an incarnation of the actor described by the passed `Props` to the given path. 
- An actor incarnation is identified by the **path and a UID**.
- It is worth noting about the difference between **restart** and **stop, followed by re-creation of actor**.
- **Restart**:
    - Only swaps the `Actor` instance defined by the `Props`.
    - The incarnation and hence the UID remains the same. 
        - As long as the incarnation is the same, you can keep using the same `ActorRef`. 
    - Restart is handled by the [Supervision Strategy](TODO) of the actor’s parent actor.
    - See [What Restart Means](TODO).
- **Stop and re-create**:
    - Ends the lifecycle of an incarnation.
    - At that point the appropriate lifecycle events are called and watching actors are notified of the termination. 
    - After the incarnation is stopped, the path can be reused again by creating an actor with `actorOf()`. 
    - In this case the name of the new incarnation will be the same as the previous one but the UIDs will differ. 
    - An actor can be stopped by the actor itself, another actor or the `ActorSystem`.
    - See [Stopping actors](TODO).
- **It is important to note** that Actors do not stop automatically when no longer referenced.
- Every Actor that is created must also explicitly be destroyed. 
- The only simplification is that stopping a parent Actor will also recursively stop all the child Actors that this parent has created.
- An `ActorRef` always represents an incarnation (path and UID) not just a given path. 
- Therefore if an actor is stopped and a new one with the same name is created:
    - An `ActorRef` of the old incarnation will not point to the new one.
- `ActorSelection` on the other hand points to the path (or multiple paths if wildcards are used).
    - It is completely oblivious to which incarnation is currently occupying it. 
    - `ActorSelection` cannot be watched for this reason. 
    - It is possible to resolve the current incarnation’s `ActorRef` living under the path.
    - This can also be done with the `resolveOne` method of the `ActorSelection`:
    - See [Identifying Actors via Actor Selection](#identifying-actors-via-actor-selection). 

## Lifecycle Monitoring aka DeathWatch
- In order to be notified when another actor terminates:
    - An actor may register itself for reception of the `Terminated` message dispatched by the other actor upon termination.
    - This service is provided by the `DeathWatch` component of the actor system.

- Registering a monitor is easy:
```scala
class WatchActor extends Actor {
  val child = context.actorOf(Props.empty, "child")
  context.watch(child) // <-- this is the only call needed for registration
  var lastSender = context.system.deadLetters

  def receive = {
    case "kill" ⇒
      context.stop(child); lastSender = sender()
    case Terminated(`child`) ⇒ lastSender ! "finished"
  }
}
```
- The `Terminated` message is generated independent of the order in which registration and termination occur. 
- In particular, the watching actor will receive a `Terminated` message even if the watched actor has already been terminated at the time of registration.
- Registering multiple times does not necessarily lead to multiple messages being generated:
    - There is no guarantee that only exactly one such message is received:
        - If termination of the watched actor has generated and queued the message.
        - And another registration is done before this message has been processed.
        - Then a second message will be queued.
    - Because registering for monitoring of an already terminated actor leads to the immediate generation of the `Terminated` message.
- It is also possible to deregister from watching another actor’s liveliness using `context.unwatch(target)`. 
    - This works even if the `Terminated` message has already been enqueued in the mailbox.
    - After calling unwatch no `Terminated` message for that actor will be processed anymore.

## Start Hook
- Right after starting the actor, its `preStart` method is invoked.
```scala
override def preStart() {
  child = context.actorOf(Props[MyActor], "child")
}
```
- This method is called when the actor is first created. 
- During restarts it is called by the default implementation of `postRestart`.
- This means that by overriding that method, you can choose whether the initialization code in this method:
    - Is called only exactly once for this actor, or for every restart. 
- Initialization code which is part of the actor’s constructor:
    - Will always be called when an instance of the actor class is created.
    - This happens at every restart.

## Restart Hooks
- All actors are supervised, i.e. linked to another actor with a fault handling strategy. 
- Actors may be restarted in case an exception is thrown while processing a message. 
- This restart involves the hooks mentioned above:
1. The old actor is informed by calling `preRestart`:
    - With the exception which caused the restart and the message which triggered that exception.
    - The latter may be `None` if the restart was not caused by processing a message:
        - When a supervisor does not trap the exception and is restarted in turn by its supervisor.
        - Or if an actor is restarted due to a sibling’s failure. 
    - If the message is available, then that message’s sender is also accessible. 
    - This method is the best place for cleaning up, preparing hand-over to the fresh actor instance, etc. 
    - By default it stops all children and calls `postStop`. 
2. The initial factory from the `actorOf` call is used to produce the fresh instance. 
3. The new actor’s `postRestart` method is invoked with the exception which caused the restart. 
    - By default the `preStart` is called, just as in the normal start-up case.
- An actor restart replaces only the actual actor object.
- The contents of the mailbox is unaffected by the restart.
- Processing of messages will resume after the `postRestart` hook returns. 
- The message that triggered the exception will not be received again. 
- Any message sent to an actor while it is being restarted will be queued to its mailbox as usual.
- **Be aware that** the ordering of failure notifications relative to user messages is not deterministic. 
- In particular, a parent might restart its child before it has processed the last messages sent by the child before the failure. 
- See [Message Ordering](../../02-general-concepts/08-message-delivery-reliability#discussion-message-ordering).

## Stop Hook
- After stopping an actor, its `postStop` hook is called.
- This may be used e.g. for deregistering this actor from other services. 
- This hook is guaranteed to run after message queuing has been disabled for this actor.
    - I.e. messages sent to a stopped actor will be redirected to the `deadLetters` of the `ActorSystem`.

# Identifying Actors via Actor Selection
- Each actor has:
    - A unique logical path.
        - Which is obtained by following the chain of actors from child to parent until reaching the root of the actor system.
    - A physical path.
        - Which may differ if the supervision chain includes any remote supervisors. 
- See [Actor References, Paths and Addresses](../../02-general-concepts/05-actor-references-paths-and-addresses)
- These paths are used by the system to look up actors.
    - E.g. when a remote message is received and the recipient is searched.
- But they are also useful more directly.
    - Actors may look up other actors by specifying absolute or relative paths (logical or physical).
    - And receive back an `ActorSelection` with the result:
```scala
// will look up this absolute path
context.actorSelection("/user/serviceA/aggregator")
// will look up sibling beneath same supervisor
context.actorSelection("../joe")
```
- It is always preferable to communicate with other Actors using their `ActorRef` instead of relying upon `ActorSelection`.
- Exceptions are:
    - Sending messages using the [At-Least-Once Delivery](../07-persistence#at-least-once-delivery) facility.
    - Initiating first contact with a remote system.
- In all other cases `ActorRef`s can be provided during Actor creation or initialization.
    - Passing them from parent to child or introducing Actors by sending their `ActorRef`s to other Actors within messages.
- The supplied path is parsed as a `java.net.URI`, which basically means that it is split on `/` into path elements. 
- If the path starts with `/`:
    - It is absolute and the look-up starts at the root guardian (which is the parent of `/user`).
- Otherwise it starts at the current actor. 
- If a path element equals `..`:
    - The look-up will take a step “up” towards the supervisor of the currently traversed actor.
- Otherwise it will step “down” to the named child. 
- It should be noted that the `..` in actor paths here always means the logical structure, i.e. the supervisor.
- The path elements of an actor selection may contain wildcard patterns allowing for broadcasting of messages to that section:
```scala
// will look all children to serviceB with names starting with worker
context.actorSelection("/user/serviceB/worker*")
// will look up all siblings beneath same supervisor
context.actorSelection("../*")
```
- Messages can be sent via the `ActorSelection` and the path of the `ActorSelection` is looked up when delivering each message. 
- If the selection does not match any actors the message will be dropped.
- To acquire an `ActorRef` for an `ActorSelection`:
    - You need to send a message to the selection.
    - And use the `sender()` reference of the reply from the actor. 
- There is a built-in `Identify` message that all Actors will understand.
- And automatically reply to with a `ActorIdentity` message containing the `ActorRef`. 
- This message is handled specially by the actors which are traversed in the sense that:
    - If a concrete name lookup fails (i.e. a non-wildcard path element does not correspond to a live actor).
    - Then a negative result is generated. 
- Please note that this does not mean that delivery of that reply is guaranteed, it still is a normal message.
```scala
class Follower extends Actor {
  val identifyId = 1
  context.actorSelection("/user/another") ! Identify(identifyId)

  def receive = {
    case ActorIdentity(`identifyId`, Some(ref)) ⇒
      context.watch(ref)
      context.become(active(ref))
    case ActorIdentity(`identifyId`, None) ⇒ context.stop(self)

  }

  def active(another: ActorRef): Actor.Receive = {
    case Terminated(`another`) ⇒ context.stop(self)
  }
}
```
- You can also acquire an `ActorRef` for an `ActorSelection` with the `resolveOne` method of the `ActorSelection`. 
- It returns a `Future` of the matching `ActorRef` if such an actor exists. 
- It is completed with failure `akka.actor.ActorNotFound` if:
    - No such actor exists.
    - Or the identification didn’t complete within the supplied timeout.
- Remote actor addresses may also be looked up, if [remoting](TODO) is enabled:
```scala
context.actorSelection("akka.tcp://app@otherhost:1234/user/serviceB")
```
- See [Remoting Sample](../../07-networking/01-remoting#remoting-sample).

# Messages and immutability
- Messages can be any kind of object but have to be immutable. 
- Scala can’t enforce immutability (yet) so this has to be by convention. 
- Primitives like `String`, `Int`, `Boolean` are always immutable. 
- Apart from these the recommended approach is to use **Scala case classes** which are immutable.
- And works great with pattern matching at the receiver side.
- Here is an example: 
```scala
// define the case class
case class User(name: String)
case class Register(user: User)

val user = User("Mike")
// create a new case class message
val message = Register(user)
```

# Send messages
- Messages are sent to an Actor through one of the following methods:
    - `!` means “fire-and-forget”, e.g. send a message asynchronously and return immediately. Also known as `tell`.
    - `?` sends a message asynchronously and returns a Future representing a possible reply. Also known as `ask`.
- Message ordering is guaranteed on a per-sender basis.
- There are performance implications of using `ask`:
    - Something needs to keep track of when it times out.
    - There needs to be something that bridges a `Promise` into an `ActorRef`.
    - It also needs to be reachable through remoting. 
- So always prefer `tell` for performance, and only `ask` if you must.

## Tell: Fire-forget
- This is the preferred way of sending messages. 
- No blocking waiting for a message. 
- This gives the best concurrency and scalability characteristics.
```scala
actorRef ! message
```
- If invoked from **within an Actor**:
    - Then the sending actor reference will be implicitly passed along with the message.
    - This will be available to the receiving Actor in its `sender(): ActorRef` member method. 
    - The target actor can use this to reply to the original sender, by using `sender() ! replyMsg`.
- If invoked from an instance that is **not an Actor**:
    - The sender will be `deadLetters` actor reference by default.

## Ask: Send-And-Receive-Future
- The `ask` pattern involves `Actor`s as well as `Future`s.
- Hence it is offered as a use pattern rather than a method on `ActorRef`:
```scala
import akka.pattern.{ ask, pipe }
import system.dispatcher // The ExecutionContext that will be used

final case class Result(x: Int, s: String, d: Double)
case object Request

implicit val timeout = Timeout(5 seconds) // needed for `?` below

val f: Future[Result] =
  for {
    x ← ask(actorA, Request).mapTo[Int] // call pattern directly
    s ← (actorB ask Request).mapTo[String] // call by implicit conversion
    d ← (actorC ? Request).mapTo[Double] // call by symbolic name
  } yield Result(x, s, d)

f pipeTo actorD // .. or ..
pipe(f) to actorD
```
- This example demonstrates `ask` together with the `pipeTo` pattern on futures.
- This is likely to be a common combination. 
- All of the above is completely non-blocking and asynchronous: 
    - `ask` produces a `Future`.
    - three of which are composed into a new future using the for-comprehension.
    - and then `pipeTo` installs an `onComplete`-handler on the future to affect the submission of the aggregated `Result` to another actor.
- Using `ask` will send a message to the receiving Actor as with `tell`.
- And the receiving actor must reply with `sender() ! reply` in order to complete the returned `Future` with a value. 
- The `ask` operation involves creating an internal actor for handling this reply:
    - Which needs to have a timeout after which it is destroyed in order not to leak resources.
- To complete the future with an **exception** you need to send an `akka.actor.Status.Failure` message to the sender. 
- This is not done automatically when an actor throws an exception while processing a message.
- Scala’s `Try` subtypes `scala.util.Failure` and `scala.util.Success` are not treated specially.
    - And would complete the ask `Future` with the given value.
- Only the `akka.actor.Status` messages are treated specially by the ask pattern. 
```scala
try {
  val result = operation()
  sender() ! result
} catch {
  case e: Exception ⇒
    sender() ! akka.actor.Status.Failure(e)
    throw e
}
```
- If the actor does not complete the future, it will expire after the timeout period, completing it with an `AskTimeoutException`. 
- The timeout is taken from one of the following locations in order of precedence: 
1. Explicitly given timeout as in:
```scala
import scala.concurrent.duration._
import akka.pattern.ask
val future = myActor.ask("hello")(5 seconds)
```
2. Implicit argument of `type akka.util.Timeout`:
```scala
import scala.concurrent.duration._
import akka.util.Timeout
import akka.pattern.ask
implicit val timeout = Timeout(5 seconds)
val future = myActor ? "hello"
```
- The `onComplete`, `onSuccess`, or `onFailure` methods of the `Future` can be used to register a callback to get a notification when the `Future` completes.
    - Giving you a way to avoid blocking.
- When using future callbacks, such as `onComplete`, `onSuccess`, and `onFailure`, inside actors:
    - You need to carefully avoid closing over the containing actor’s reference.
    - I.e. do not call methods or access mutable state on the enclosing actor from within the callback. 
    - This would break the actor encapsulation and may introduce synchronization bugs and race conditions.
    - Because the callback will be scheduled concurrently to the enclosing actor. 
    - Unfortunately there is not yet a way to detect these illegal accesses at compile time.     
- See [Futures](../../08-futures-and-agents/01-futures) on how to await or query a future.
- See [Actors and shared mutable state](../../02-general-concepts/07-akka-and-the-java-memory-model#actors-and-shared-mutable-state)

## Forward message
- You can forward a message from one actor to another. 
- This means that the original sender address/reference is maintained even though the message is going through a ‘mediator’. 
- This can be useful when writing actors that work as routers, load-balancers, replicators etc.
```scala
target forward message
```

# Receive messages
- An Actor has to implement the `receive` method to receive messages: 
```scala
type Receive = PartialFunction[Any, Unit]

def receive: Actor.Receive
```
- This method returns a `PartialFunction`.
- E.g. a ‘match/case’ clause in which the message can be matched against the different case clauses using Scala pattern matching. 
- Here is an example:
```scala
class MyActor extends Actor {
  val log = Logging(context.system, this)

  def receive = {
    case "test" ⇒ log.info("received test")
    case _      ⇒ log.info("received unknown message")
  }
}
```

# Reply to messages
- If you want to have a handle for replying to a message, you can use `sender()`, which gives you an `ActorRef`. 
- You can reply by sending to that `ActorRef` with `sender() ! replyMsg`. 
- You can also store the `ActorRef` for replying later, or passing on to other actors. 
- If there is no sender (a message was sent without an actor or future context):
    - Then the sender defaults to a ‘dead-letter’ actor ref.
```scala
sender() ! x // replies will go to this actor
```

# Receive timeout
- The `ActorContext` `setReceiveTimeout` defines the inactivity timeout after which the sending of a `ReceiveTimeout` message is triggered. 
- When specified, the `receive` function should be able to handle an `akka.actor.ReceiveTimeout` message. 
- 1 millisecond is the minimum supported timeout.
- The receive timeout might fire and enqueue the `ReceiveTimeout` message right after another message was enqueued.
- Hence it is **not guaranteed** that upon reception of the receive timeout:
    - There must have been an idle period beforehand as configured via this method.
- Once set, the receive timeout stays in effect.
    - I.e. continues firing repeatedly after inactivity periods. 
- Pass in `Duration.Undefined` to switch off this feature.
```scala
class MyActor extends Actor {
  // To set an initial delay
  context.setReceiveTimeout(30 milliseconds)
  def receive = {
    case "Hello" ⇒
      // To set in a response to a message
      context.setReceiveTimeout(100 milliseconds)
    case ReceiveTimeout ⇒
      // To turn it off
      context.setReceiveTimeout(Duration.Undefined)
      throw new RuntimeException("Receive timed out")
  }
}
```
- Messages marked with `NotInfluenceReceiveTimeout` will not reset the timer. 
- This can be useful when:
    - `ReceiveTimeout` should be fired by external inactivity.
    - But not influenced by internal activity, e.g. scheduled tick messages.

# Timers, scheduled messages
- Messages can be scheduled to be sent at a later point by using the [`Scheduler`](../../09-utilities/03-scheduler) directly.
- But when scheduling periodic or single messages in an actor to itself it’s more convenient and safe to use the support for named timers. 
- The lifecycle of scheduled messages can be difficult to manage when the actor is restarted and that is taken care of by the timers.
```scala
object MyActor {
  private case object TickKey
  private case object FirstTick
  private case object Tick
}

class MyActor extends Actor with Timers {
  import MyActor._
  timers.startSingleTimer(TickKey, FirstTick, 500.millis)

  def receive = {
    case FirstTick ⇒
      // do something useful here
      timers.startPeriodicTimer(TickKey, Tick, 1.second)
    case Tick ⇒
    // do something useful here
  }
}
```
- Each timer has a key and can be replaced or cancelled. 
- It’s guaranteed that a message from the previous incarnation of the timer with the same key is not received.
- Even though it might already be enqueued in the mailbox when it was cancelled or the new timer was started.
- The timers are bound to the lifecycle of the actor that owns it.
- And thus are cancelled automatically when it is restarted or stopped. 
- Note that the TimerScheduler is not thread-safe, i.e. it must only be used within the actor that owns it.

# Stopping actors
- Actors are stopped by invoking the `stop` method of a `ActorRefFactory`, i.e. `ActorContext` or `ActorSystem`. 
- Typically the **context** is used for stopping the actor itself or child actors.
- And the **system** for stopping top level actors. 
- The actual termination of the actor is performed asynchronously, i.e. `stop` may return before the actor is stopped.
```scala
class MyActor extends Actor {

  val child: ActorRef = ???

  def receive = {
    case "interrupt-child" ⇒
      context stop child

    case "done" ⇒
      context stop self
  }

}
```
- Processing of the current message, if any, will continue before the actor is stopped.
- But additional messages in the mailbox will not be processed. 
- By default these messages are sent to the `deadLetters` of the `ActorSystem`, depending on the mailbox implementation.
- Termination of an actor proceeds in two steps: 
    - **Step 1:** The actor suspends its mailbox processing and sends a stop command to all its children.
      - Then it keeps processing the internal termination notifications from its children until the last one is gone.
    - **Step 2:** Terminates itself (invoking `postStop`, dumping mailbox, publishing `Terminated` on the `DeathWatch`, telling its supervisor). 
- This procedure ensures that actor system sub-trees terminate in an orderly fashion.
- Propagating the stop command to the leaves and collecting their confirmation back to the stopped supervisor. 
- If one of the actors does not respond (i.e. processing a message for extended periods of time and therefore not receiving the stop command):
    - This whole process will be stuck.
- Upon `ActorSystem.terminate()`, the system guardian actors will be stopped.
- And the aforementioned process will ensure proper termination of the whole system.
- The `postStop()` hook is invoked after an actor is fully stopped. 
- This enables cleaning up of resources:
```scala
override def postStop() {
  ()
}
```
- Since stopping an actor is asynchronous, you cannot immediately reuse the name of the child you just stopped.
    - This will result in an `InvalidActorNameException`. 
- Instead, `watch()` the terminating actor and create its replacement in response to the `Terminated` message which will eventually arrive.

## PoisonPill
- You can also send an actor the `akka.actor.PoisonPill` message.
- Which will stop the actor when the message is processed. 
- `PoisonPill` is enqueued as ordinary messages and will be handled after messages that were already queued in the mailbox.
```scala
watch(victim)
victim ! PoisonPill
```

## Killing an Actor
- You can also “kill” an actor by sending a `Kill` message. 
- Unlike `PoisonPill` this will cause the actor to throw a `ActorKilledException`, triggering a failure. 
- The actor will suspend operation and its supervisor will be asked how to handle the failure.
    - Which may mean resuming the actor, restarting it or terminating it completely. 
- See [What Supervision Means](../../02-general-concepts/04-supervision-and-monitoring#what-supervision-means).
- Use `Kill` like this:
```scala
context.watch(victim) // watch the Actor to receive Terminated message once it dies

victim ! Kill

expectMsgPF(hint = "expecting victim to terminate") {
  case Terminated(v) if v == victim ⇒ v // the Actor has indeed terminated
}
```
- In general though it is **not recommended** to overly rely on either `PoisonPill` or `Kill` in designing your actor interactions.
- Often times a protocol-level message like `PleaseCleanupAndStop` which the actor knows how to handle is encouraged. 
- The messages are there for being able to stop actors over which design you do not have control over.

## Graceful Stop
- `gracefulStop` is useful if you need to wait for termination or compose ordered termination of several actors:
```scala
try {
  val stopped: Future[Boolean] = gracefulStop(actorRef, 5 seconds, Manager.Shutdown)
  Await.result(stopped, 6 seconds)
  // the actor has been stopped
} catch {
  // the actor wasn't stopped within 5 seconds
  case e: akka.pattern.AskTimeoutException ⇒
}
```
- When `gracefulStop()` returns successfully, the actor’s `postStop()` hook will have been executed: 
    - There exists a _happens-before_ edge between the end of `postStop()` and the return of `gracefulStop()`.
- In the above example a custom `Manager.Shutdown` message is sent to the target actor to initiate the process of stopping the actor. 
- You can use `PoisonPill` for this:
    - But then you have limited possibilities to perform interactions with other actors before stopping the target actor. 
- Simple cleanup tasks can be handled in `postStop`.
- **Note that** an actor stopping and its name being deregistered are separate events which happen asynchronously from each other. 
- Therefore it may be that you will find the name still in use after `gracefulStop()` returned. 
- In order to guarantee proper deregistration:
    - Only reuse names from within a supervisor you control.
    - And only in response to a `Terminated` message, i.e. not for top-level actors.

## Coordinated Shutdown
- There is an extension named `CoordinatedShutdown` that will:
    - Stop certain actors and services in a specific order.
    - And perform registered tasks during the shutdown process.
- The order of the shutdown phases is defined in configuration `akka.coordinated-shutdown.phases`. 
- The default phases are defined as:
```hocon
# CoordinatedShutdown will run the tasks that are added to these
# phases. The phases can be ordered as a DAG by defining the
# dependencies between the phases.
# Each phase is defined as a named config section with the
# following optional properties:
# - timeout=15s: Override the default-phase-timeout for this phase.
# - recover=off: If the phase fails the shutdown is aborted
#                and depending phases will not be executed.
# depends-on=[]: Run the phase after the given phases
phases {

  # The first pre-defined phase that applications can add tasks to.
  # Note that more phases can be added in the application's
  # configuration by overriding this phase with an additional
  # depends-on.
  before-service-unbind {
  }

  # Stop accepting new incoming requests in for example HTTP.
  service-unbind {
    depends-on = [before-service-unbind]
  }

  # Wait for requests that are in progress to be completed.
  service-requests-done {
    depends-on = [service-unbind]
  }

  # Final shutdown of service endpoints.
  service-stop {
    depends-on = [service-requests-done]
  }

  # Phase for custom application tasks that are to be run
  # after service shutdown and before cluster shutdown.
  before-cluster-shutdown {
    depends-on = [service-stop]
  }

  # Graceful shutdown of the Cluster Sharding regions.
  cluster-sharding-shutdown-region {
    timeout = 10 s
    depends-on = [before-cluster-shutdown]
  }

  # Emit the leave command for the node that is shutting down.
  cluster-leave {
    depends-on = [cluster-sharding-shutdown-region]
  }

  # Shutdown cluster singletons
  cluster-exiting {
    timeout = 10 s
    depends-on = [cluster-leave]
  }

  # Wait until exiting has been completed
  cluster-exiting-done {
    depends-on = [cluster-exiting]
  }

  # Shutdown the cluster extension
  cluster-shutdown {
    depends-on = [cluster-exiting-done]
  }

  # Phase for custom application tasks that are to be run
  # after cluster shutdown and before ActorSystem termination.
  before-actor-system-terminate {
    depends-on = [cluster-shutdown]
  }

  # Last phase. See terminate-actor-system and exit-jvm above.
  # Don't add phases that depends on this phase because the
  # dispatcher and scheduler of the ActorSystem have been shutdown.
  actor-system-terminate {
    timeout = 10 s
    depends-on = [before-actor-system-terminate]
  }
}
```
- More phases can be added in the application’s configuration if needed by overriding a phase with an additional `depends-on`. 
- The phases `before-service-unbind`, `before-cluster-shutdown` and `before-actor-system-terminate`:
    - Are intended for application specific phases or tasks.
- The default phases are defined in a single linear order.
    - But the phases can be ordered as a directed acyclic graph (DAG) by defining the dependencies between the phases. 
    - The phases are ordered with [topological](https://en.wikipedia.org/wiki/Topological_sorting) sort of the DAG.
- Tasks can be added to a phase with:
```scala
CoordinatedShutdown(system).addTask(
  CoordinatedShutdown.PhaseBeforeServiceUnbind, "someTaskName") { () ⇒
    import akka.pattern.ask
    import system.dispatcher
    implicit val timeout = Timeout(5.seconds)
    (someActor ? "stop").map(_ ⇒ Done)
  }
```
- The returned `Future[Done]` should be completed when the task is completed. 
- The task name parameter is only used for debugging/logging.
- Tasks added to the same phase are executed in parallel without any ordering assumptions. 
- The next phase will not start until all tasks of previous the phase have been completed.
- If tasks are not completed within a [configured timeout](https://doc.akka.io/docs/akka/current/general/configuration.html#config-akka-actor) the next phase will be started anyway. 
- It is possible to configure `recover=off` for a phase to abort the rest of the shutdown process if a task fails or is not completed within the timeout.
- Tasks should typically be registered as early as possible after system startup. 
- When running the coordinated shutdown tasks that have been registered will be performed but tasks that are added too late will not be run.
- To start the coordinated shutdown process you can invoke `run` on the `CoordinatedShutdown` extension:
```scala
val done: Future[Done] = CoordinatedShutdown(system).run(CoordinatedShutdown.UnknownReason)
```
- It’s safe to call the `run` method multiple times. It will only run once.
- That also means that the `ActorSystem` will be terminated in the last phase. 
- By default, the JVM is not forcefully stopped (it will be stopped if all non-daemon threads have been terminated). 
- To enable a hard `System.exit` as a final action you can configure:
```hocon
akka.coordinated-shutdown.exit-jvm = on
```
- When using [Akka Cluster](../../05-clustering/02-cluster-usage) the `CoordinatedShutdown` will automatically run when the cluster node sees itself as `Exiting`.
    - I.e. leaving from another node will trigger the shutdown process on the leaving node. 
- Tasks for graceful leaving of cluster are added automatically when _Akka Cluster_ is used.
    - I.e. running the shutdown process will also trigger the graceful leaving if it’s not already in progress.
    - This includes graceful shutdown of _Cluster Singletons_ and _Cluster Sharding_.
- By default, the `CoordinatedShutdown` will be run when the JVM process exits.
    - E.g. via `kill SIGTERM` signal (`SIGINT` ctrl-c doesn’t work). 
- This behavior can be disabled with:
```hocon
akka.coordinated-shutdown.run-by-jvm-shutdown-hook=off
```
- If you have application specific JVM shutdown hooks:
    - It’s recommended that you register them via the `CoordinatedShutdown`.
    - So that they are running before Akka internal shutdown hooks.
    - E.g. those shutting down Akka Remoting (Artery).
```scala
CoordinatedShutdown(system).addJvmShutdownHook {
  println("custom JVM shutdown hook...")
}
```
- For some tests it might be undesired to terminate the `ActorSystem` via `CoordinatedShutdown`. 
- You can disable that by adding the following to the configuration of the `ActorSystem` that is used in the test:
```hocon
# Don't terminate ActorSystem via CoordinatedShutdown in tests
akka.coordinated-shutdown.terminate-actor-system = off
akka.coordinated-shutdown.run-by-jvm-shutdown-hook = off
akka.cluster.run-coordinated-shutdown-when-down = off
```

# Become/Unbecome

## Upgrade
- Akka supports hotswapping the Actor’s message loop (e.g. its implementation) at runtime: 
    - Invoke the `context.become` method from within the Actor. 
- `become` takes a `PartialFunction[Any, Unit]` that implements the new message handler. 
- The hotswapped code is kept in a Stack which can be pushed and popped.
- **Please note** that the actor will revert to its original behavior when restarted by its Supervisor.
- To hotswap the Actor behavior using `become`:
```scala
class HotSwapActor extends Actor {
  import context._
  def angry: Receive = {
    case "foo" ⇒ sender() ! "I am already angry?"
    case "bar" ⇒ become(happy)
  }

  def happy: Receive = {
    case "bar" ⇒ sender() ! "I am already happy :-)"
    case "foo" ⇒ become(angry)
  }

  def receive = {
    case "foo" ⇒ become(angry)
    case "bar" ⇒ become(happy)
  }
}
```
- This variant of the `become` method is useful for many different things:
    - Such as to implement a **Finite State Machine** (FSM, for an example see [Dining Hakkers](http://www.dalnefre.com/wp/2010/08/dining-philosophers-in-humus/)). 
- It will replace the current behavior (i.e. the top of the behavior stack).
    - Which means that you do not use `unbecome`, instead the next behavior is explicitly installed.
- The other way of using `become` does not replace but add to the top of the behavior stack. 
- In this case care must be taken to ensure that:
    - The number of “pop” operations (i.e. `unbecome`) matches the number of “push” ones in the long run.
    - Otherwise this amounts to a memory leak (which is why this behavior is not the default).
```scala
case object Swap
class Swapper extends Actor {
  import context._
  val log = Logging(system, this)

  def receive = {
    case Swap ⇒
      log.info("Hi")
      become({
        case Swap ⇒
          log.info("Ho")
          unbecome() // resets the latest 'become' (just for fun)
      }, discardOld = false) // push on top instead of replace
  }
}

object SwapperApp extends App {
  val system = ActorSystem("SwapperSystem")
  val swap = system.actorOf(Props[Swapper], name = "swapper")
  swap ! Swap // logs Hi
  swap ! Swap // logs Ho
  swap ! Swap // logs Hi
  swap ! Swap // logs Ho
  swap ! Swap // logs Hi
  swap ! Swap // logs Ho
}
```

## Encoding Scala Actors nested receives without accidentally leaking memory
- See [Unnested receive example](https://github.com/akka/akka/blob/v2.5.9/akka-docs/src/test/scala/docs/actor/UnnestedReceives.scala).

# Stash
- The `Stash` trait enables an actor to temporarily stash away messages that can not or should not be handled using the actor’s current behavior. 
- Upon changing the actor’s message handler:
    - All stashed messages can be “unstashed”, thereby prepending them to the actor’s mailbox. 
- This way, the stashed messages can be processed in the same order as they have been received originally. 
- The trait `Stash` extends the marker trait `RequiresMessageQueue[DequeBasedMessageQueueSemantics]`:
    - Which requests the system to automatically choose a **deque based mailbox** implementation for the actor. 
- If you want more control over the mailbox, see the [Mailboxes documentation](../04-mailboxes).
- Here is an example of the `Stash` in action:
```scala
class ActorWithProtocol extends Actor with Stash {
  def receive = {
    case "open" ⇒
      unstashAll()
      context.become({
        case "write" ⇒ // do writing...
        case "close" ⇒
          unstashAll()
          context.unbecome()
        case msg ⇒ stash()
      }, discardOld = false) // stack on top instead of replacing
    case msg ⇒ stash()
  }
}
```
- Invoking `stash()` adds the current message (the message that the actor received last) to the actor’s stash. 
- It is typically invoked when handling the default case in the actor’s message handler:
    - To stash messages that aren’t handled by the other cases. 
- It is illegal to stash the same message twice; to do so results in an `IllegalStateException` being thrown. 
- The stash may also be bounded in which case invoking `stash()` may lead to a capacity violation.
    - Which results in a `StashOverflowException`. 
- The capacity of the stash can be configured using the `stash-capacity` setting of the mailbox’s configuration.
- Invoking `unstashAll()` enqueues messages from the stash to the actor’s mailbox.
    - Until the capacity of the mailbox (if any) has been reached.
    - Messages from the stash are prepended to the mailbox. 
- In case a bounded mailbox overflows, a `MessageQueueAppendFailedException` is thrown. 
- The stash is guaranteed to be empty after calling `unstashAll()`.
- The stash is backed by a `scala.collection.immutable.Vector`. 
- As a result, even a very large number of messages may be stashed without a major impact on performance.
- The `Stash` trait must be mixed into (a subclass of) the `Actor` trait before any trait/class that overrides the `preRestart` callback. 
- This means it’s not possible to write `Actor with MyActor with Stash` if `MyActor` overrides `preRestart`.
- The stash is part of the ephemeral actor state, unlike the mailbox. 
- Therefore, it should be managed like other parts of the actor’s state which have the same property. 
- The `Stash` trait’s implementation of `preRestart` will call `unstashAll()`, which is usually the desired behavior.
- If you want to enforce that your actor can only work with an unbounded stash:
    - Then you should use the `UnboundedStash` trait instead.

# Actors and exceptions
- It can happen that while a message is being processed by an actor, that some kind of exception is thrown, e.g. a database exception.

## What happens to the Message
- If an exception is thrown while a message is being processed:
    - I.e. taken out of its mailbox and handed over to the current behavior.
    - Then this message will be lost. 
- It is important to understand that it is not put back on the mailbox. 
- So if you want to retry processing of a message:
    - You need to deal with it yourself.
    - By catching the exception and retry your flow. 
- Make sure that you put a bound on the number of retries since you don’t want a system to livelock:
    - Consuming a lot of CPU cycles without making progress.

## What happens to the mailbox
- If an exception is thrown while a message is being processed, nothing happens to the mailbox. 
- If the actor is restarted, the same mailbox will be there. 
- So all messages on that mailbox will be there as well.

## What happens to the actor
- If code within an actor throws an exception, that actor is suspended and the supervision process is started.
    - See [Supervision](../../02-general-concepts/04-supervision-and-monitoring). 
- Depending on the supervisor’s decision:
    - The actor is resumed (as if nothing happened).
    - Restarted (wiping out its internal state and starting from scratch)
    - Or terminated.

# Extending Actors using PartialFunction chaining
- Sometimes it can be useful to:
    - Share common behavior among a few actors.
    - Or compose one actor’s behavior from multiple smaller functions. 
- This is possible because an actor’s `receive` method returns an `Actor.Receive`:
    - Which is a type alias for `PartialFunction[Any,Unit]`.
- Partial functions can be chained together using the `PartialFunction#orElse` method. 
- You can chain as many functions as you need:
    - However you should keep in mind that “first match” wins.
    - This may be important when combining functions that both can handle the same type of message.
- For example, imagine you have a set of actors which are either `Producers` or `Consumers`:
    - Yet sometimes it makes sense to have an actor share both behaviors. 
- This can be achieved without having to duplicate code by:
    - Extracting the behaviors to traits.
    - And implementing the actor’s `receive` as combination of these partial functions.
```scala
trait ProducerBehavior {
  this: Actor ⇒

  val producerBehavior: Receive = {
    case GiveMeThings ⇒
      sender() ! Give("thing")
  }
}

trait ConsumerBehavior {
  this: Actor with ActorLogging ⇒

  val consumerBehavior: Receive = {
    case ref: ActorRef ⇒
      ref ! GiveMeThings

    case Give(thing) ⇒
      log.info("Got a thing! It's {}", thing)
  }
}

class Producer extends Actor with ProducerBehavior {
  def receive = producerBehavior
}

class Consumer extends Actor with ActorLogging with ConsumerBehavior {
  def receive = consumerBehavior
}

class ProducerConsumer extends Actor with ActorLogging
  with ProducerBehavior with ConsumerBehavior {

  def receive = producerBehavior.orElse[Any, Unit](consumerBehavior)
}

// protocol
case object GiveMeThings
final case class Give(thing: Any)
```
- Instead of inheritance the same pattern can be applied via composition.
- Simply compose the receive method using partial functions from delegates.

# Initialization patterns
- The rich lifecycle hooks of Actors provide a useful toolkit to implement various initialization patterns. 
- During the lifetime of an `ActorRef`, an actor can potentially go through several restarts:
    - Where the old instance is replaced by a fresh one.
    - Invisibly to the outside observer who only sees the `ActorRef`.
- Initialization might be necessary every time an actor is instantiated.
- But sometimes one needs initialization to happen only at the birth of the first instance.
- The following sections provide patterns for different initialization needs.

## Initialization via constructor
- Using the constructor for initialization has various benefits. 
- First of all, it makes it possible to use `val` fields to store any state that does not change during the life of the actor instance.
    - Making the implementation of the actor more robust. 
- The constructor is invoked when an actor instance is created calling `actorOf` and also on restart:
    - Therefore the internals of the actor can always assume that proper initialization happened. 
- This is also the drawback of this approach.
- There are cases when one would like to avoid reinitializing internals on restart. 
- For example, it is often useful to preserve child actors across restarts. 
- The following section provides a pattern for this case.

## Initialization via `preStart`
- The method `preStart()` of an actor is only called once directly during the initialization of the first instance.
    - That is, at creation of its `ActorRef`. 
- In the case of restarts, `preStart()` is called from `postRestart()`.
    - Therefore if not overridden, `preStart()` is called on every restart. 
- However, by overriding `postRestart()` one can disable this behavior, and ensure that there is only one call to `preStart()`.
- One useful usage of this pattern is to disable creation of new `ActorRefs` for children during restarts. 
- This can be achieved by overriding `preRestart()`. 
- Below is the default implementation of these lifecycle hooks:
```scala
override def preStart(): Unit = {
  // Initialize children here
}

// Overriding postRestart to disable the call to preStart()
// after restarts
override def postRestart(reason: Throwable): Unit = ()

// The default implementation of preRestart() stops all the children
// of the actor. To opt-out from stopping the children, we
// have to override preRestart()
override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
  // Keep the call to postStop(), but no stopping of children
  postStop()
}
```
- The child actors are still restarted, but no new `ActorRef` is created. 
- One can recursively apply the same principles for the children:
    - Ensuring that their `preStart()` method is called only at the creation of their refs.
- See [What Restarting Means](../../02-general-concepts/04-supervision-and-monitoring#what-restarting-means).

## Initialization via message passing
- There are cases when it is impossible to pass all the information needed for actor initialization in the constructor:
    - For example in the presence of circular dependencies. 
- In this case the actor should listen for an initialization message:
    - And use `become()` or a finite state-machine state transition to encode the initialized and uninitialized states of the actor.
```scala
var initializeMe: Option[String] = None

override def receive = {
  case "init" ⇒
    initializeMe = Some("Up and running")
    context.become(initialized, discardOld = true)

}

def initialized: Receive = {
  case "U OK?" ⇒ initializeMe foreach { sender() ! _ }
}
```
- If the actor may receive messages before it has been initialized:
    - A useful tool can be the `Stash` to save messages until the initialization finishes.
    - And replaying them after the actor became initialized.
- **This pattern should be used with care**, and applied only when none of the patterns above are applicable. 
- One of the potential issues is that messages might be lost when sent to remote actors. 
- Also, publishing an `ActorRef` in an uninitialized state:
    - Might lead to the condition that it receives a user message before the initialization has been done.
