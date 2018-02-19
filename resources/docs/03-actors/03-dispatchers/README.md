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
#### Dispatcher:
- This is an event-based dispatcher that binds a set of Actors to a thread pool. It is the default dispatcher used if one is not specified.
    - Sharability: Unlimited
    - Mailboxes: Any, creates one per Actor
    - Use cases: Default dispatcher, Bulkheading
    - Driven by: java.util.concurrent.ExecutorService. Specify using “executor” using “fork-join-executor”, “thread-pool-executor” or the FQCN of an akka.dispatcher.ExecutorServiceConfigurator.
#### PinnedDispatcher:
- This dispatcher dedicates a unique thread for each actor using it; i.e. each actor will have its own thread pool with only one thread in the pool.
    - Sharability: None
    - Mailboxes: Any, creates one per Actor
    - Use cases: Bulkheading
    - Driven by: Any akka.dispatch.ThreadPoolExecutorConfigurator. By default a “thread-pool-executor”.
#### CallingThreadDispatcher:
- This dispatcher runs invocations on the current thread only. This dispatcher does not create any new threads, but it can be used from different threads concurrently for the same actor. See CallingThreadDispatcher for details and restrictions.
    - Sharability: Unlimited
    - Mailboxes: Any, creates one per Actor per Thread (on demand)
    - Use cases: Testing
    - Driven by: The calling thread (duh)





# Blocking Needs Careful Management










