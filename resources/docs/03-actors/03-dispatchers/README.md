# Dispatchers - Overview
- An Akka `MessageDispatcher` is what makes Akka Actors “tick”, it is the engine of the machine so to speak. 
- All `MessageDispatcher` implementations are also an `ExecutionContext`:
- Which means that they can be used to execute arbitrary code, for instance [`Futures`](../../08-futures-and-agents/01-futures).

# Default dispatcher
- Every `ActorSystem` will have a default dispatcher that will be used in case nothing else is configured for an `Actor`. 
- The default dispatcher can be configured, and is by default a `Dispatcher` with the specified `default-executor`. 
- If an ActorSystem is created with an ExecutionContext passed in:
    - This ExecutionContext will be used as the default executor for all dispatchers in this ActorSystem. 
- If no ExecutionContext is given:
    - It will fallback to the executor specified in `akka.actor.default-dispatcher.default-executor.fallback`. 
- By default this is a `fork-join-executor`, which gives excellent performance in most cases.

# Looking up a Dispatcher
- Dispatchers implement the `ExecutionContext` interface and can thus be used to run `Future` invocations.
```scala
implicit val executionContext = system.dispatchers.lookup("my-dispatcher")
```
- So in case you want to give your `Actor` a different dispatcher than the default:
- You need to do two things, of which the first is to configure the dispatcher:
```hocon
my-dispatcher {
  # Dispatcher is the name of the event-based dispatcher
  type = Dispatcher
  # What kind of ExecutionService to use
  executor = "fork-join-executor"
  # Configuration for the fork join pool
  fork-join-executor {
    # Min number of threads to cap factor-based parallelism number to
    parallelism-min = 2
    # Parallelism (threads) ... ceil(available processors * factor)
    parallelism-factor = 2.0
    # Max number of threads to cap factor-based parallelism number to
    parallelism-max = 10
  }
  # Throughput defines the maximum number of messages to be
  # processed per actor before the thread jumps to the next actor.
  # Set to 1 for as fair as possible.
  throughput = 100
}
```
- The `parallelism-max` does not set the upper bound on the total number of threads allocated by the ForkJoinPool. 
- It is a setting specifically talking about the number of hot threads the pool keep running:
    - In order to reduce the latency of handling a new incoming task. 
    - See JDK’s [ForkJoinPool documentation](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ForkJoinPool.html).
- Another example that uses the `thread-pool-executor`:
```hocon
blocking-io-dispatcher {
  type = Dispatcher
  executor = "thread-pool-executor"
  thread-pool-executor {
    fixed-pool-size = 32
  }
  throughput = 1
}
```
- The thread pool executor dispatcher is implemented using by a `java.util.concurrent.ThreadPoolExecutor`. 
    - See JDK’s [ThreadPoolExecutor documentation](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ThreadPoolExecutor.html).
- For more options, see the `default-dispatcher` section of the [configuration](../../02-general-concepts/09-configuration). 
- Then you create the actor as usual and define the dispatcher in the deployment configuration.
```scala
val myActor = context.actorOf(Props[MyActor], "myactor")
```
```hocon
akka.actor.deployment {
  /myactor {
    dispatcher = my-dispatcher
  }
}
```
- An alternative to the deployment configuration is to define the dispatcher in code. 
- If you define the `dispatcher` in the deployment configuration then this value will be used instead of programmatically provided parameter.
```scala
val myActor =
  context.actorOf(Props[MyActor].withDispatcher("my-dispatcher"), "myactor1")
```
- The dispatcher you specify in `withDispatcher` and the `dispatcher` property in the deployment configuration:
    - Is in fact a path into your configuration. 
- So in this example it’s a top-level section:
    - But you could for instance put it as a sub-section.
    - Where you’d use periods to denote sub-sections, like this: `foo.bar.my-dispatcher`.

# Types of dispatchers
- There are 3 different types of message dispatchers:
#### `Dispatcher`:
- This is an event-based dispatcher that binds a set of Actors to a thread pool. 
- It is the default dispatcher used if one is not specified.
- **Sharability**: Unlimited.
- **Mailboxes**: Any, creates one per Actor.
- **Use cases**: Default dispatcher, Bulkheading.
- **Driven by**: `java.util.concurrent.ExecutorService`. 
    - Specify using `executor` using `fork-join-executor`, `thread-pool-executor` or the FQCN of an `akka.dispatcher.ExecutorServiceConfigurator`.
#### `PinnedDispatcher`:
- This dispatcher dedicates a unique thread for each actor using it.
- Each actor will have its own thread pool with only one thread in the pool.
- **Sharability**: None.
- **Mailboxes**: Any, creates one per Actor.
- **Use cases**: Bulkheading.
- **Driven by**: Any `akka.dispatch.ThreadPoolExecutorConfigurator`. By default a `thread-pool-executor`.
#### `CallingThreadDispatcher`:
- This dispatcher runs invocations on the current thread only. 
- It does not create any new threads, but it can be used from different threads concurrently for the same actor. 
- See [`CallingThreadDispatcher`](../11-testing-actor-systems#callingthreaddispatcher).
- **Sharability**: Unlimited.
- **Mailboxes**: Any, creates one per Actor per Thread (on demand).
- **Use cases**: Testing.
- **Driven by**: The calling thread.

## More dispatcher configuration examples

### For actors that perform blocking IO
- Configuring a dispatcher with fixed thread pool size:
```hocon
blocking-io-dispatcher {
  type = Dispatcher
  executor = "thread-pool-executor"
  thread-pool-executor {
    fixed-pool-size = 32
  }
  throughput = 1
}
```
```scala
val myActor =
  context.actorOf(Props[MyActor].withDispatcher("blocking-io-dispatcher"), "myactor2")
```

### For CPU bound tasks
- Uses the thread pool based on the number of cores:
```hocon
my-thread-pool-dispatcher {
  # Dispatcher is the name of the event-based dispatcher
  type = Dispatcher
  # What kind of ExecutionService to use
  executor = "thread-pool-executor"
  # Configuration for the thread pool
  thread-pool-executor {
    # minimum number of threads to cap factor-based core number to
    core-pool-size-min = 2
    # No of core threads ... ceil(available processors * factor)
    core-pool-size-factor = 2.0
    # maximum number of threads to cap factor-based number to
    core-pool-size-max = 10
  }
  # Throughput defines the maximum number of messages to be
  # processed per actor before the thread jumps to the next actor.
  # Set to 1 for as fair as possible.
  throughput = 100
}
```

### Small number of actors with state
- A different kind of dispatcher that uses an **affinity pool** may increase throughput.
- The affinity pool tries its best to ensure that an actor is always scheduled to run on the same thread. 
- This actor to thread pinning aims to decrease CPU cache misses which can result in significant throughput improvement:
```hocon
affinity-pool-dispatcher {
  # Dispatcher is the name of the event-based dispatcher
  type = Dispatcher
  # What kind of ExecutionService to use
  executor = "affinity-pool-executor"
  # Configuration for the thread pool
  affinity-pool-executor {
    # Min number of threads to cap factor-based parallelism number to
    parallelism-min = 8
    # Parallelism (threads) ... ceil(available processors * factor)
    parallelism-factor = 1
    # Max number of threads to cap factor-based parallelism number to
    parallelism-max = 16
  }
  # Throughput defines the maximum number of messages to be
  # processed per actor before the thread jumps to the next actor.
  # Set to 1 for as fair as possible.
  throughput = 100
}
```

### Configuring a `PinnedDispatcher`
```hocon
my-pinned-dispatcher {
  executor = "thread-pool-executor"
  type = PinnedDispatcher
}
```
```scala
val myActor =
  context.actorOf(Props[MyActor].withDispatcher("my-pinned-dispatcher"), "myactor3")
```
- Note that `thread-pool-executor` configuration as per the above `my-thread-pool-dispatcher` example is **NOT applicable**. 
- This is because every actor will have its own thread pool when using `PinnedDispatcher`, and that pool will have only one thread.
- It’s not guaranteed that the same thread is used over time:
    - Since the core pool timeout is used for `PinnedDispatcher` to keep resource usage down in case of idle actors. 
- To use the same thread all the time you need to add `thread-pool-executor.allow-core-timeout=off` to the configuration of the `PinnedDispatcher`.

# Blocking Needs Careful Management
- In some cases it is unavoidable to do blocking operations.
- I.e. to put a thread to sleep for an indeterminate time, waiting for an external event to occur. 
- Examples are **legacy RDBMS drivers** or **messaging APIs**.
- The underlying reason is typically that network I/O occurs under the covers.
```scala
class BlockingActor extends Actor {
   def receive = {
     case i: Int ⇒
       Thread.sleep(5000) //block for 5 seconds, representing blocking I/O, etc
       println(s"Blocking operation finished: ${i}")
   }
}
```
- When facing this, you may be tempted to just wrap the blocking call inside a `Future` and work with that instead.
- But this strategy is too simple: 
- You are quite likely to find bottlenecks or run out of memory or threads when the application runs under increased load.
```scala
class BlockingFutureActor extends Actor {
   implicit val executionContext: ExecutionContext = context.dispatcher
 
   def receive = {
     case i: Int ⇒
       println(s"Calling blocking Future: ${i}")
       Future {
         Thread.sleep(5000) //block for 5 seconds
         println(s"Blocking future finished ${i}")
       }
   }
}
```

## Problem: Blocking on default dispatcher
- The key here is this line:
```scala
implicit val executionContext: ExecutionContext = context.dispatcher
```
- Using `context.dispatcher` as the dispatcher on which the blocking `Future` executes can be a problem.
- This dispatcher is by default used for all other actor processing unless you set up a separate dispatcher for the actor.
- If all of the available threads are blocked:
    - Then all the actors on the same dispatcher will starve for threads and will not be able to process incoming messages.
- Blocking APIs should be avoided if possible. 
- Try to find or build **Reactive APIs**, such that blocking is minimised, or moved over to dedicated dispatchers.
- Often when integrating with existing libraries or systems it is not possible to avoid blocking APIs. 
- The [following solution](#solution-dedicated-dispatcher-for-blocking-operations) explains how to handle blocking operations properly.
- Note that the same hints apply to managing blocking operations anywhere in Akka:
    - Including Streams, Http and other reactive libraries built on top of it.
- Let’s set up an application with the above `BlockingFutureActor` and the following `PrintActor`.
- See [Example 1](./dispatchers-examples/src/main/scala/dispatchers/example1)  
```scala
class PrintActor extends Actor {
   def receive = {
     case i: Int ⇒
       println(s"PrintActor: ${i}")
   }
}
```
```scala
val actor1 = system.actorOf(Props(new BlockingFutureActor))
val actor2 = system.actorOf(Props(new PrintActor))

for (i ← 1 to 100) {
  actor1 ! i
  actor2 ! i
}
```
- Here the app is:
    - Sending 100 messages to `BlockingFutureActor` and `PrintActor`.
    - Large numbers of `akka.actor.default-dispatcher` threads are handling requests. 
- When you run the above code, you will likely to see the entire application gets stuck somewhere like this:
```
>　PrintActor: 44
>　PrintActor: 45
```
- `PrintActor` is considered non-blocking, however it is not able to proceed with handling the remaining messages.
- All the threads are occupied and blocked by the other blocking actor - thus leading to thread starvation.
- In the thread state diagrams below the colours have the following meaning:
    - **Turquoise**: Sleeping state.
    - **Orange**: Waiting state.
    - **Green**: Runnable state.
- The thread information was recorded using the [YourKit profiler](https://www.yourkit.com/java/profiler/features/).
- However any good JVM profiler has this feature:
    - Oracle JDK VisualVM.
    - Oracle Flight Recorder.
- The orange portion of the thread shows that it is idle. 
- Idle threads are fine - they’re ready to accept new work. 
- However, large amount of turquoise (blocked, or sleeping as in our example) threads is very bad and leads to thread starvation.

#### Note
- If you own a Lightbend subscription you can use the commercial [Thread Starvation Detector](http://developer.lightbend.com/docs/akka-commercial-addons/current/starvation-detector.html?_ga=2.65045102.1383436497.1519017755-542223074.1518507267).
- It will issue warning log statements if it detects any of your dispatchers suffering from starvation and other. 
- It is a helpful first step to identify the problem is occurring in a production system.
- Then you can apply the proposed solutions as explained below.

![Thread State Diagram - Bad](https://doc.akka.io/docs/akka/current/images/dispatcher-behaviour-on-bad-code.png)

- In the above example we put the code under load by sending hundreds of messages to the blocking actor.
- This causes threads of the default dispatcher to be blocked. 
- The fork join pool based dispatcher in Akka then attempts to compensate for this blocking by adding more threads to the pool.
- This however is not able to help if those too will immediately get blocked.
- Eventually the blocking operations will dominate the entire dispatcher.
- In essence, the `Thread.sleep` operation has dominated all threads.
- This caused anything executing on the default dispatcher to starve for resources.
- Including any actor that you have not configured an explicit dispatcher for.

## Solution: Dedicated dispatcher for blocking operations
- One of the most efficient methods of isolating the blocking behaviour:
    - Is to prepare and use a dedicated dispatcher for all those blocking operations. 
- This will prevent the blocking behaviour from impacting the rest of the system
- This technique is often referred to as as “bulk-heading” or simply “isolating blocking”.
- In `application.conf`, the dispatcher dedicated to blocking behaviour should be configured as follows:
```hocon
my-blocking-dispatcher {
  type = Dispatcher
  executor = "thread-pool-executor"
  thread-pool-executor {
    fixed-pool-size = 16
  }
  throughput = 1
}
```
- A `thread-pool-executor` based dispatcher allows us to set a limit on the number of threads it will host.
- This way we gain tight control over how at-most-how-many blocked threads will be in the system.
- The exact size should be fine tuned depending on:
    - The workload you’re expecting to run on this dispatcher.
    - And the number of cores of the machine you’re running the application on. 
- **Usually a small number around the number of cores is a good default to start from**.
- Whenever blocking has to be done, use the above configured dispatcher instead of the default one:
```scala
class SeparateDispatcherFutureActor extends Actor {
  implicit val executionContext: ExecutionContext = context.system.dispatchers.lookup("my-blocking-dispatcher")

  def receive = {
    case i: Int ⇒
      println(s"Calling blocking Future: ${i}")
      Future {
        Thread.sleep(5000) //block for 5 seconds
        println(s"Blocking future finished ${i}")
      }
  }
}
```
- The thread pool behaviour is shown in the below diagram:
![Thread State Diagram - Good](https://doc.akka.io/docs/akka/current/images/dispatcher-behaviour-on-good-code.png)

- Messages sent to `SeparateDispatcherFutureActor` and `PrintActor` are easily handled by the default dispatcher.
    - The green lines, which represent the actual execution.
- When blocking operations are run on the `my-blocking-dispatcher`:
    - It uses the threads (up to the configured limit) to handle these operations. 
    - The sleeping in this case is nicely isolated to just this dispatcher, and the default one remains unaffected.
    - This allows the rest of the application to proceed as if nothing bad was happening. 
    - After a certain period idleness, threads started by this dispatcher will be shut down.
- The throughput of other actors was not impacted - they were still served on the default dispatcher.
- This is the recommended way of dealing with any kind of blocking in reactive applications.
- See also this [Akka HTTP example](https://doc.akka.io/docs/akka-http/current/handling-blocking-operations-in-akka-http-routes.html?language=scala#handling-blocking-operations-in-akka-http).

## Available solutions to blocking operations
- The non-exhaustive list of adequate solutions to the “blocking problem” includes the following suggestions:

#### Solution 1:
- Do the blocking call within an actor (or a set of actors) managed by a router, making sure to configure a thread pool which is either dedicated for this purpose or sufficiently sized.
- This especially well-suited for resources which are single-threaded in nature, like database handles which traditionally can only execute one outstanding query at a time and use internal synchronization to ensure this. A common pattern is to create a router for N actors, each of which wraps a single DB connection and handles queries as sent to the router. The number N must then be tuned for maximum throughput, which will vary depending on which DBMS is deployed on what hardware.

#### Solution 2:
- Do the blocking call within a Future, ensuring an upper bound on the number of such calls at any point in time (submitting an unbounded number of tasks of this nature will exhaust your memory or thread limits).

#### Solution 3:
- Do the blocking call within a Future, providing a thread pool with an upper limit on the number of threads which is appropriate for the hardware on which the application runs, as explained in detail in this section.

#### Solution 4:
- Dedicate a single thread to manage a set of blocking resources (e.g. a NIO selector driving multiple channels) and dispatch events as they occur as actor messages.

# Note
- Configuring thread pools is a task best delegated to Akka.
- Simply configure in the `application.conf` and instantiate through an `ActorSystem`.
