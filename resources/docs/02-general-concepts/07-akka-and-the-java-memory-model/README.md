# Akka and the Java Memory Model - Overview
- A major benefit of using the _Lightbend Platform_, including Scala and Akka, is that it simplifies the process of writing concurrent software. 
- This article discusses how the _Lightbend Platform_, and Akka in particular, approaches shared memory in concurrent applications.

# The Java Memory Model
- Prior to Java 5, the Java Memory Model (JMM) was ill defined. 
- It was possible to get all kinds of strange results when shared memory was accessed by multiple threads, such as:
    - A thread not seeing values written by other threads: a visibility problem.
    - A thread observing 'impossible' behavior of other threads, caused by instructions not being executed in the order expected: an instruction reordering problem.
- With the implementation of JSR 133 in Java 5, a lot of these issues have been resolved. The JMM is a set of rules based on the **“happens-before”** relation, which constrain when one memory access must happen before another, and conversely, when they are allowed to happen out of order. Two examples of these rules are:
    - **The monitor lock rule**: a release of a lock happens before every subsequent acquire of the same lock.
    - **The volatile variable rule**: a write of a volatile variable happens before every subsequent read of the same volatile variable
- Although the JMM can seem complicated, the specification tries to find a balance between ease of use and the ability to write performant and scalable concurrent data structures.

# Actors and the Java Memory Model
- With the Actors implementation in Akka, there are two ways multiple threads can execute actions on shared memory:
    - If a message is sent to an actor (e.g. by another actor). 
        - In most cases messages are immutable, but if that message is not a properly constructed immutable object, without a “happens before” rule, it would be possible for the receiver to see partially initialized data structures and possibly even values out of thin air (longs/doubles).
    - If an actor makes changes to its internal state while processing a message, and accesses that state while processing another message moments later. 
        - It is important to realize that with the actor model you don’t get any guarantee that the same thread will be executing the same actor for different messages.
- To prevent visibility and reordering problems on actors, Akka guarantees the following two “happens before” rules:
    - **The actor send rule**: the send of the message to an actor happens before the receive of that message by the same actor.
    - **The actor subsequent processing rule**: processing of one message happens before processing of the next message by the same actor.
    
## Note    
- In layman’s terms this means that changes to internal fields of the actor are visible when the next message is processed by that actor. 
- So fields in your actor need not be volatile or equivalent.
- Both rules only apply for the same actor instance and are not valid if different actors are used.

# Futures and the Java Memory Model
- The completion of a Future “happens before” the invocation of any callbacks registered to it are executed.
- We recommend not to close over non-final fields (`val` in Scala).
- Ff you do choose to close over non-final fields, they must be marked volatile in order for the current value of the field to be visible to the callback.
- If you close over a reference, you must also ensure that the instance that is referred to is thread safe. 
- We highly recommend staying away from objects that use locking, since it can introduce performance problems and in the worst case, deadlocks. 
- Such are the perils of synchronized.

# Actors and shared mutable state
- Since Akka runs on the JVM there are still some rules to be followed.
- Messages should be immutable, this is to avoid the shared mutable state trap.
- Closing over internal Actor state and exposing it to other threads.
```scala
case class Message(msg: String)

class EchoActor extends Actor {
  def receive = {
    case msg ⇒ sender() ! msg
  }
}

class CleanUpActor extends Actor {
  def receive = {
    case set: mutable.Set[_] ⇒ set.clear()
  }
}

class MyActor(echoActor: ActorRef, cleanUpActor: ActorRef) extends Actor {
  var state = ""
  val mySet = mutable.Set[String]()

  def expensiveCalculation(actorRef: ActorRef): String = {
    // this is a very costly operation
    "Meaning of life is 42"
  }

  def expensiveCalculation(): String = {
    // this is a very costly operation
    "Meaning of life is 42"
  }

  def receive = {
    case _ ⇒
      implicit val ec = context.dispatcher
      implicit val timeout = Timeout(5 seconds) // needed for `?` below

      // Example of incorrect approach
      // Very bad: shared mutable state will cause your
      // application to break in weird ways
      Future { state = "This will race" }
      ((echoActor ? Message("With this other one")).mapTo[Message])
        .foreach { received ⇒ state = received.msg }

      // Very bad: shared mutable object allows
      // the other actor to mutate your own state,
      // or worse, you might get weird race conditions
      cleanUpActor ! mySet

      // Very bad: "sender" changes for every message,
      // shared mutable state bug
      Future { expensiveCalculation(sender()) }

      // Example of correct approach
      // Completely safe: "self" is OK to close over
      // and it's an ActorRef, which is thread-safe
      Future { expensiveCalculation() } foreach { self ! _ }

      // Completely safe: we close over a fixed value
      // and it's an ActorRef, which is thread-safe
      val currentSender = sender()
      Future { expensiveCalculation(currentSender) }
  }
}
```