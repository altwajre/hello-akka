# Agents - Overview

Agents in Akka are inspired by agents in Clojure.
Deprecation warning

Agents have been deprecated and are scheduled for removal in the next major version. We have found that their leaky abstraction (they do not work over the network) make them inferior to pure Actors, and in face of the soon inclusion of Akka Typed we see little value in maintaining the current Agents.

Agents provide asynchronous change of individual locations. Agents are bound to a single storage location for their lifetime, and only allow mutation of that location (to a new state) to occur as a result of an action. Update actions are functions that are asynchronously applied to the Agent’s state and whose return value becomes the Agent’s new state. The state of an Agent should be immutable.

While updates to Agents are asynchronous, the state of an Agent is always immediately available for reading by any thread (using get or apply) without any messages.

Agents are reactive. The update actions of all Agents get interleaved amongst threads in an ExecutionContext. At any point in time, at most one send action for each Agent is being executed. Actions dispatched to an agent from another thread will occur in the order they were sent, potentially interleaved with actions dispatched to the same agent from other threads.
Note

Agents are local to the node on which they are created. This implies that you should generally not include them in messages that may be passed to remote Actors or as constructor parameters for remote Actors; those remote Actors will not be able to read or update the Agent.

# Creating Agents

Agents are created by invoking Agent(value) passing in the Agent’s initial value and providing an implicit ExecutionContext to be used for it, for these examples we’re going to use the default global one, but YMMV:

Scala

    import scala.concurrent.ExecutionContext.Implicits.global
    import akka.agent.Agent
    val agent = Agent(5)

Java


# Reading an Agent’s value

Agents can be dereferenced (you can get an Agent’s value) by invoking the Agent with parentheses like this:

Scala

    val result = agent()
    // Or by using the get method:
    val result = agent.get

Java

Reading an Agent’s current value does not involve any message passing and happens immediately. So while updates to an Agent are asynchronous, reading the state of an Agent is synchronous.

# Updating Agents (send & alter)

You update an Agent by sending a function that transforms the current value or by sending just a new value. The Agent will apply the new value or function atomically and asynchronously. The update is done in a fire-forget manner and you are only guaranteed that it will be applied. There is no guarantee of when the update will be applied but dispatches to an Agent from a single thread will occur in order. You apply a value or a function by invoking the send function.

Scala

    // send a value, enqueues this change
    // of the value of the Agent
    agent send 7

    // send a function, enqueues this change
    // to the value of the Agent
    agent send (_ + 1)
    agent send (_ * 2)

Java

You can also dispatch a function to update the internal state but on its own thread. This does not use the reactive thread pool and can be used for long-running or blocking operations. You do this with the sendOff method. Dispatches using either sendOff or send will still be executed in order.

Scala

    // the ExecutionContext you want to run the function on
    implicit val ec = someExecutionContext()
    // sendOff a function
    agent sendOff longRunningOrBlockingFunction

Java

All send methods also have a corresponding alter method that returns a Future. See Futures for more information on Futures.

Scala

    // alter a value
    val f1: Future[Int] = agent alter 7

    // alter a function
    val f2: Future[Int] = agent alter (_ + 1)
    val f3: Future[Int] = agent alter (_ * 2)
    // the ExecutionContext you want to run the function on
    implicit val ec = someExecutionContext()
    // alterOff a function
    val f4: Future[Int] = agent alterOff longRunningOrBlockingFunction

Java


# Awaiting an Agent’s value

You can also get a Future to the Agents value, that will be completed after the currently queued updates have completed:

val future = agent.future

See Futures for more information on Futures.

# Monadic usage

Agents are also monadic, allowing you to compose operations using for-comprehensions. In monadic usage, new Agents are created leaving the original Agents untouched. So the old values (Agents) are still available as-is. They are so-called ‘persistent’.

Example of monadic usage:

import scala.concurrent.ExecutionContext.Implicits.global
val agent1 = Agent(3)
val agent2 = Agent(5)

// uses foreach
for (value ← agent1)
  println(value)

// uses map
val agent3 = for (value ← agent1) yield value + 1

// or using map directly
val agent4 = agent1 map (_ + 1)

// uses flatMap
val agent5 = for {
  value1 ← agent1
  value2 ← agent2
} yield value1 + value2


# Configuration

There are several configuration properties for the agents module, please refer to the reference configuration.

# Deprecated Transactional Agents

Agents participating in enclosing STM transaction is a deprecated feature in 2.3.

If an Agent is used within an enclosing transaction, then it will participate in that transaction. If you send to an Agent within a transaction then the dispatch to the Agent will be held until that transaction commits, and discarded if the transaction is aborted. Here’s an example:

import scala.concurrent.ExecutionContext.Implicits.global
import akka.agent.Agent
import scala.concurrent.duration._
import scala.concurrent.stm._

def transfer(from: Agent[Int], to: Agent[Int], amount: Int): Boolean = {
  atomic { txn ⇒
    if (from.get < amount) false
    else {
      from send (_ - amount)
      to send (_ + amount)
      true
    }
  }
}

val from = Agent(100)
val to = Agent(20)
val ok = transfer(from, to, 50)

val fromValue = from.future // -> 50
val toValue = to.future // -> 70

