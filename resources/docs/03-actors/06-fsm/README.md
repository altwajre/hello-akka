# FSM - Overview
- The FSM (Finite State Machine) is available as a mixin for the Akka Actor.
- A FSM can be described as a set of relations of the form:
    - **State(S) x Event(E) -> Actions(A), State(S’)**
- These relations are interpreted as meaning:
    - _If we are in state S and the event E occurs, we should perform the actions A and make a transition to the state S’_.

# A Simple Example
- To demonstrate most of the features of the FSM trait:
    - Consider an actor which shall receive and queue messages while they arrive in a burst.
    - And send them on after the burst ended or a flush request is received.
- The contract of our “Buncher” actor is that it accepts or produces the following messages:
```scala
// received events
final case class SetTarget(ref: ActorRef)
final case class Queue(obj: Any)
case object Flush

// sent events
final case class Batch(obj: immutable.Seq[Any])
```
- `SetTarget`:
    - Starting it up.
    - Setting the destination for the `Batches` to be passed on.
- `Queue`:
    - Add to the internal queue.
- `Flush`:
    - Mark the end of a burst.

# Reference





# Testing and Debugging Finite State Machines





# Examples










