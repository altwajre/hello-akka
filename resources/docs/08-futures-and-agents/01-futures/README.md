# Futures - Overview

# Introduction

In the Scala Standard Library, a Future is a data structure used to retrieve the result of some concurrent operation. This result can be accessed synchronously (blocking) or asynchronously (non-blocking).

# Execution Contexts

In order to execute callbacks and operations, Futures need something called an ExecutionContext, which is very similar to a java.util.concurrent.Executor. if you have an ActorSystem in scope, it will use its default dispatcher as the ExecutionContext, or you can use the factory methods provided by the ExecutionContext companion object to wrap Executors and ExecutorServices, or even create your own.

Scala

    import scala.concurrent.{ ExecutionContext, Promise }

    implicit val ec = ExecutionContext.fromExecutorService(yourExecutorServiceGoesHere)

    // Do stuff with your brand new shiny ExecutionContext
    val f = Promise.successful("foo")

    // Then shut your ExecutionContext down at some
    // appropriate place in your program/application
    ec.shutdown()

Java

Within Actors

Each actor is configured to be run on a MessageDispatcher, and that dispatcher doubles as an ExecutionContext. If the nature of the Future calls invoked by the actor matches or is compatible with the activities of that actor (e.g. all CPU bound and no latency requirements), then it may be easiest to reuse the dispatcher for running the Futures by importing context.dispatcher.

Scala

    class A extends Actor {
      import context.dispatcher
      val f = Future("hello")
      def receive = {
        case _ ⇒
      }
    }

Java


# Use with Actors

There are generally two ways of getting a reply from an Actor: the first is by a sent message (actor ! msg), which only works if the original sender was an Actor) and the second is through a Future.

Using an Actor’s ? method to send a message will return a Future. To wait for and retrieve the actual result the simplest method is:

Scala

    import scala.concurrent.Await
    import akka.pattern.ask
    import akka.util.Timeout
    import scala.concurrent.duration._

    implicit val timeout = Timeout(5 seconds)
    val future = actor ? msg // enabled by the “ask” import
    val result = Await.result(future, timeout.duration).asInstanceOf[String]

Java

This will cause the current thread to block and wait for the Actor to ‘complete’ the Future with its reply. Blocking is discouraged though as it will cause performance problems. The blocking operations are located in Await.result and Await.ready to make it easy to spot where blocking occurs. Alternatives to blocking are discussed further within this documentation. Also note that the Future returned by an Actor is a Future[Any] since an Actor is dynamic. That is why the asInstanceOf is used in the above sample.
Warning

Await.result and Await.ready are provided for exceptional situations where you must block, a good rule of thumb is to only use them if you know why you must block. For all other cases, use asynchronous composition as described below.

When using non-blocking it is better to use the mapTo method to safely try to cast a Future to an expected type:

import scala.concurrent.Future
import akka.pattern.ask

val future: Future[String] = ask(actor, msg).mapTo[String]

The mapTo method will return a new Future that contains the result if the cast was successful, or a ClassCastException if not. Handling Exceptions will be discussed further within this documentation.

To send the result of a Future to an Actor, you can use the pipe construct:

scala

    import akka.pattern.pipe
    future pipeTo actor

java


# Use Directly

A common use case within Akka is to have some computation performed concurrently without needing the extra utility of an Actor. If you find yourself creating a pool of Actors for the sole reason of performing a calculation in parallel, there is an easier (and faster) way:

Scala

    import scala.concurrent.Await
    import scala.concurrent.Future
    import scala.concurrent.duration._

    val future = Future {
      "Hello" + "World"
    }
    future foreach println

Java

In the above code the block passed to Future will be executed by the default Dispatcher, with the return value of the block used to complete the Future (in this case, the result would be the string: “HelloWorld”). Unlike a Future that is returned from an Actor, this Future is properly typed, and we also avoid the overhead of managing an Actor.

You can also create already completed Futures using the Future companion, which can be either successes:

Scala

    val future = Future.successful("Yay!")

Java

Or failures:

Scala

    val otherFuture = Future.failed[String](new IllegalArgumentException("Bang!"))

Java

It is also possible to create an empty Promise, to be filled later, and obtain the corresponding Future:

Scala

    val promise = Promise[String]()
    val theFuture = promise.future
    promise.success("hello")

Java


# Functional Futures

Scala’s Future has several monadic methods that are very similar to the ones used by Scala’s collections. These allow you to create ‘pipelines’ or ‘streams’ that the result will travel through.
Future is a Monad

The first method for working with Future functionally is map. This method takes a Function which performs some operation on the result of the Future, and returning a new result. The return value of the map method is another Future that will contain the new result:

Scala

    val f1 = Future {
      "Hello" + "World"
    }
    val f2 = f1 map { x ⇒
      x.length
    }
    f2 foreach println

Java

In this example we are joining two strings together within a Future. Instead of waiting for this to complete, we apply our function that calculates the length of the string using the map method. Now we have a second Future, f2, that will eventually contain an Int. When our original Future, f1, completes, it will also apply our function and complete the second Future with its result. When we finally get the result, it will contain the number 10. Our original Future still contains the string “HelloWorld” and is unaffected by the map.

Something to note when using these methods: passed work is always dispatched on the provided ExecutionContext. Even if the Future has already been completed, when one of these methods is called.

The map method is fine if we are modifying a single Future, but if 2 or more Futures are involved map will not allow you to combine them together:

val f1 = Future {
  "Hello" + "World"
}
val f2 = Future.successful(3)
val f3 = f1 map { x ⇒
  f2 map { y ⇒
    x.length * y
  }
}
f3 foreach println

f3 is a Future[Future[Int]] instead of the desired Future[Int]. Instead, the flatMap method should be used:

val f1 = Future {
  "Hello" + "World"
}
val f2 = Future.successful(3)
val f3 = f1 flatMap { x ⇒
  f2 map { y ⇒
    x.length * y
  }
}
f3 foreach println

Composing futures using nested combinators it can sometimes become quite complicated and hard to read, in these cases using Scala’s ‘for comprehensions’ usually yields more readable code. See next section for examples.

If you need to do conditional propagation, you can use filter:

val future1 = Future.successful(4)
val future2 = future1.filter(_ % 2 == 0)

future2 foreach println

val failedFilter = future1.filter(_ % 2 == 1).recover {
  // When filter fails, it will have a java.util.NoSuchElementException
  case m: NoSuchElementException ⇒ 0
}

failedFilter foreach println

For Comprehensions

Since Future has a map, filter and flatMap method it can be easily used in a ‘for comprehension’:

val f = for {
  a ← Future(10 / 2) // 10 / 2 = 5
  b ← Future(a + 1) //  5 + 1 = 6
  c ← Future(a - 1) //  5 - 1 = 4
  if c > 3 // Future.filter
} yield b * c //  6 * 4 = 24

// Note that the execution of futures a, b, and c
// are not done in parallel.

f foreach println

Something to keep in mind when doing this is even though it looks like parts of the above example can run in parallel, each step of the for comprehension is run sequentially. This will happen on separate threads for each step but there isn’t much benefit over running the calculations all within a single Future. The real benefit comes when the Futures are created first, and then combining them together.
Composing Futures

The example for comprehension above is an example of composing Futures. A common use case for this is combining the replies of several Actors into a single calculation without resorting to calling Await.result or Await.ready to block for each result. First an example of using Await.result:


val f1 = ask(actor1, msg1)
val f2 = ask(actor2, msg2)

val a = Await.result(f1, 3 seconds).asInstanceOf[Int]
val b = Await.result(f2, 3 seconds).asInstanceOf[Int]

val f3 = ask(actor3, (a + b))

val result = Await.result(f3, 3 seconds).asInstanceOf[Int]

Here we wait for the results from the first 2 Actors before sending that result to the third Actor. We called Await.result 3 times, which caused our little program to block 3 times before getting our final result. Now compare that to this example:


val f1 = ask(actor1, msg1)
val f2 = ask(actor2, msg2)

val f3 = for {
  a ← f1.mapTo[Int]
  b ← f2.mapTo[Int]
  c ← ask(actor3, (a + b)).mapTo[Int]
} yield c

f3 foreach println

Here we have 2 actors processing a single message each. Once the 2 results are available (note that we don’t block to get these results!), they are being added together and sent to a third Actor, which replies with a string, which we assign to ‘result’.

This is fine when dealing with a known amount of Actors, but can grow unwieldy if we have more than a handful. The sequence and traverse helper methods can make it easier to handle more complex use cases. Both of these methods are ways of turning, for a subclass T of Traversable, T[Future[A]] into a Future[T[A]]. For example:

Scala

    // oddActor returns odd numbers sequentially from 1 as a List[Future[Int]]
    val listOfFutures = List.fill(100)(akka.pattern.ask(oddActor, GetNext).mapTo[Int])

    // now we have a Future[List[Int]]
    val futureList = Future.sequence(listOfFutures)

    // Find the sum of the odd numbers
    val oddSum = futureList.map(_.sum)
    oddSum foreach println

Java

To better explain what happened in the example, Future.sequence is taking the List[Future[Int]] and turning it into a Future[List[Int]]. We can then use map to work with the List[Int] directly, and we aggregate the sum of the List.

The traverse method is similar to sequence, but it takes a sequence of A and applies a function A => Future[B] to return a Future[T[B]] where T is again a subclass of Traversable. For example, to use traverse to sum the first 100 odd numbers:

Scala

    val futureList = Future.traverse((1 to 100).toList)(x ⇒ Future(x * 2 - 1))
    val oddSum = futureList.map(_.sum)
    oddSum foreach println

Java

This is the same result as this example:

val futureList = Future.sequence((1 to 100).toList.map(x ⇒ Future(x * 2 - 1)))
val oddSum = futureList.map(_.sum)
oddSum foreach println

But it may be faster to use traverse as it doesn’t have to create an intermediate List[Future[Int]].

Then there’s a method that’s called fold that takes a start-value, a sequence of Futures and a function from the type of the start-value, a timeout, and the type of the futures and returns something with the same type as the start-value, and then applies the function to all elements in the sequence of futures, non-blockingly, the execution will be started when the last of the Futures is completed.

Scala

    // Create a sequence of Futures
    val futures = for (i ← 1 to 1000) yield Future(i * 2)
    val futureSum = Future.fold(futures)(0)(_ + _)
    futureSum foreach println

Java

That’s all it takes!

If the sequence passed to fold is empty, it will return the start-value, in the case above, that will be 0. In some cases you don’t have a start-value and you’re able to use the value of the first completing Future in the sequence as the start-value, you can use reduce, it works like this:

Scala

    // Create a sequence of Futures
    val futures = for (i ← 1 to 1000) yield Future(i * 2)
    val futureSum = Future.reduce(futures)(_ + _)
    futureSum foreach println

Java

Same as with fold, the execution will be done asynchronously when the last of the Future is completed, you can also parallelize it by chunking your futures into sub-sequences and reduce them, and then reduce the reduced results again.

# Callbacks

Sometimes you just want to listen to a Future being completed, and react to that not by creating a new Future, but by side-effecting. For this Future supports onComplete, onSuccess and onFailure, of which the last two are specializations of the first.

Scala

    future onSuccess {
      case "bar"     ⇒ println("Got my bar alright!")
      case x: String ⇒ println("Got some random string: " + x)
    }

Java

Scala

    future onFailure {
      case ise: IllegalStateException if ise.getMessage == "OHNOES" ⇒
      //OHNOES! We are in deep trouble, do something!
      case e: Exception ⇒
      //Do something else
    }

Java

Scala

    future onComplete {
      case Success(result)  ⇒ doSomethingOnSuccess(result)
      case Failure(failure) ⇒ doSomethingOnFailure(failure)
    }

Java


# Define Ordering

Since callbacks are executed in any order and potentially in parallel, it can be tricky at the times when you need sequential ordering of operations. But there’s a solution and its name is andThen. It creates a new Future with the specified callback, a Future that will have the same result as the Future it’s called on, which allows for ordering like in the following sample:

Scala

    val result = Future { loadPage(url) } andThen {
      case Failure(exception) ⇒ log(exception)
    } andThen {
      case _ ⇒ watchSomeTV()
    }
    result foreach println

Java


# Auxiliary Methods

Future fallbackTo combines 2 Futures into a new Future, and will hold the successful value of the second Future if the first Future fails.

Scala

    val future4 = future1 fallbackTo future2 fallbackTo future3
    future4 foreach println

Java

You can also combine two Futures into a new Future that will hold a tuple of the two Futures successful results, using the zip operation.

Scala

    val future3 = future1 zip future2 map { case (a, b) ⇒ a + " " + b }
    future3 foreach println

Java


# Exceptions

Since the result of a Future is created concurrently to the rest of the program, exceptions must be handled differently. It doesn’t matter if an Actor or the dispatcher is completing the Future, if an Exception is caught the Future will contain it instead of a valid result. If a Future does contain an Exception, calling Await.result will cause it to be thrown again so it can be handled properly.

It is also possible to handle an Exception by returning a different result. This is done with the recover method. For example:

Scala

    val future = akka.pattern.ask(actor, msg1) recover {
      case e: ArithmeticException ⇒ 0
    }
    future foreach println

Java

In this example, if the actor replied with a akka.actor.Status.Failure containing the ArithmeticException, our Future would have a result of 0. The recover method works very similarly to the standard try/catch blocks, so multiple Exceptions can be handled in this manner, and if an Exception is not handled this way it will behave as if we hadn’t used the recover method.

You can also use the recoverWith method, which has the same relationship to recover as flatMap has to map, and is use like this:

Scala

    val future = akka.pattern.ask(actor, msg1) recoverWith {
      case e: ArithmeticException ⇒ Future.successful(0)
      case foo: IllegalArgumentException ⇒
        Future.failed[Int](new IllegalStateException("All br0ken!"))
    }
    future foreach println

Java


# After

akka.pattern.after makes it easy to complete a Future with a value or exception after a timeout.

Scala

    // import akka.pattern.after

    val delayed = akka.pattern.after(200 millis, using = system.scheduler)(Future.failed(
      new IllegalStateException("OHNOES")))
    val future = Future { Thread.sleep(1000); "foo" }
    val result = Future firstCompletedOf Seq(future, delayed)

Java

