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
- See [Example 1](./actors-examples/src/main/scala/actors/example1)

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
- The recommended approach to create the actor `Props` is not supported for cases when the actor constructor takes [value classes](https://docs.scala-lang.org/overviews/core/value-classes.html) as arguments.

### Dangerous Variants





### Edge cases





### Recommended Practices












## Creating Actors with Props





## Dependency Injection




Dangerous Variants
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










