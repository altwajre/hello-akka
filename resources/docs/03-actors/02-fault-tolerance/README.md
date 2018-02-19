# Fault Tolerance - Overview
- Each actor is the supervisor of its children, and as such each actor defines fault handling supervisor strategy. 
- This strategy cannot be changed afterwards as it is an integral part of the actor system’s structure.

# Fault Handling in Practice
- First, let us look at a sample that illustrates one way to handle data store errors:
    - Which is a typical source of failure in real world applications. 
- Of course it depends on the actual application what is possible to do when the data store is unavailable:
    - But in this sample we use a best effort re-connect approach.
- [Read the following source code](./fault-tolerance-examples/src/main/scala/faulttolerance/example1/FaultHandlingDocSample.scala). 
- [View the diagrams here](https://doc.akka.io/docs/akka/2.5.9/fault-tolerance-sample.html?language=scala)
- The inlined comments explain the different pieces of the fault handling and why they are added. 
- It is also highly recommended to run this sample as it is easy to follow the log output to understand what is happening at runtime.

# Creating a Supervisor Strategy
- The following sections explain the fault handling mechanism and alternatives in more depth.
- For the sake of demonstration let us consider the following strategy:
```scala
override val supervisorStrategy =
  OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
    case _: ArithmeticException      ⇒ Resume
    case _: NullPointerException     ⇒ Restart
    case _: IllegalArgumentException ⇒ Stop
    case _: Exception                ⇒ Escalate
  }
```
- We have chosen a few well-known exception types:
    - In order to demonstrate the application of the fault handling directives described in supervision. 
- First off, it is a **one-for-one strategy**:
    - Meaning that each child is treated separately.
- An **all-for-one strategy** works very similarly:
    - The only difference is that any decision is applied to all children of the supervisor, not only the failing one. 
- In the above example, `10` and `1 minute` are passed to the `maxNrOfRetries` and `withinTimeRange` parameters respectively.
- Which means that the strategy restarts a child up to 10 restarts per minute. 
- The child actor is stopped if the restart count exceeds `maxNrOfRetries` during the `withinTimeRange` duration.
- There are special values for these parameters. If you specify:
    - `-1` to `maxNrOfRetries`, and `Duration.inf` to `withinTimeRange`:
        - Then the child is always restarted without any limit.
    - `-1` to `maxNrOfRetries`, and a non-infinite `Duration` to `withinTimeRange`:
        - `maxNrOfRetries` is treated as `1`.
    - A non-negative number to `maxNrOfRetries` and `Duration.inf` to `withinTimeRange`:
        - `withinTimeRange` is treated as infinite duration.
        - No matter how long it takes, once the restart count exceeds `maxNrOfRetries`, the child actor is stopped.
- The match statement which forms the bulk of the body is of type `Decider`:
    - Which is a `PartialFunction[Throwable, Directive]`. 
- This is the piece which maps child failure types to their corresponding directives.
- If the strategy is declared inside the supervising actor (as opposed to within a companion object):
    - Its decider has access to all internal state of the actor in a thread-safe fashion.
    - And can obtain a reference to the currently failed child.
        - Available as the sender of the failure message.

# Supervision of Top-Level Actors





# Test Application










