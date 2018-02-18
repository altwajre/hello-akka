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



















# Actor API





# Identifying Actors via Actor Selection





# Messages and immutability





# Send messages





# Receive messages





# Reply to messages





# Receive timeout





# Timers, scheduled messages





# Stopping actors





# Become/Unbecome





# Stash





# Actors and exceptions





# Extending Actors using PartialFunction chaining





# Initialization patterns










