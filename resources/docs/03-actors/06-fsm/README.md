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

```scala
// states
sealed trait State
case object Idle extends State
case object Active extends State

sealed trait Data
case object Uninitialized extends Data
final case class Todo(target: ActorRef, queue: immutable.Seq[Any]) extends Data
```
- The actor can be in two states:
    - `Idle`: no message queued.
    - `Active`: some message queued. 
- It will stay in the `Active` state as long as:
    - Messages keep arriving.
    - And no flush is requested. 
- The internal state data of the actor is made up of:
    - The target actor reference to send the batches to.
    - And the actual queue of messages.

- Now let’s take a look at the skeleton for our FSM actor:
```scala
class Buncher extends FSM[State, Data] {

  startWith(Idle, Uninitialized)

  when(Idle) {
    case Event(SetTarget(ref), Uninitialized) ⇒
      stay using Todo(ref, Vector.empty)
  }

  onTransition {
    case Active -> Idle ⇒
      stateData match {
        case Todo(ref, queue) ⇒ ref ! Batch(queue)
        case _                ⇒ // nothing to do
      }
  }

  when(Active, stateTimeout = 1 second) {
    case Event(Flush | StateTimeout, t: Todo) ⇒
      goto(Idle) using t.copy(queue = Vector.empty)
  }

  whenUnhandled {
    // common code for both states
    case Event(Queue(obj), t @ Todo(_, v)) ⇒
      goto(Active) using t.copy(queue = v :+ obj)

    case Event(e, s) ⇒
      log.warning("received unhandled request {} in state {}/{}", e, stateName, s)
      stay
  }

  initialize()
}
```
- The basic strategy is to:
    - Declare the actor.
    - Mixing in the FSM trait.
    - Specifying the possible states and data values as type parameters. 
- Within the body of the actor a DSL is used for declaring the state machine:
    - `startWith` defines the initial state and initial data.
    - Then there is one `when(<state>) { ... }` declaration per state to be handled:
        - There could potentially be multiple ones.
        - The passed `PartialFunction` will be concatenated using `orElse`.
    - Finally starting it up using initialize:
        - Which performs the transition into the initial state.
        - And sets up timers (if required).
- We start out in the `Idle` state with `Uninitialized` data:
    - Where only the `SetTarget()` message is handled.
- `stay` prepares to end this event’s processing for not leaving the current state.
- The `using` modifier makes the FSM replace the internal state:
    - Which is `Uninitialized` at this point.
    - With a fresh `Todo()` object containing the target actor reference. 
- The `Active` state has a state timeout declared:
    - Which means that if no message is received for 1 second, a `FSM.StateTimeout` message will be generated. 
- This has the same effect as receiving the `Flush` command:
    - To transition back into the `Idle` state.
    - And resetting the internal queue to the empty vector. 
- But how do messages get queued? 
- Since this shall work identically in both states:
- We make use of the fact that:
    - Any event which is not handled by the `when()` block is passed to the `whenUnhandled()` block:
```scala
whenUnhandled {
  // common code for both states
  case Event(Queue(obj), t @ Todo(_, v)) ⇒
    goto(Active) using t.copy(queue = v :+ obj)

  case Event(e, s) ⇒
    log.warning("received unhandled request {} in state {}/{}", e, stateName, s)
    stay
}
```
- The **first case** handled here is:
- Adding `Queue()` requests to the internal queue.
- And going to the `Active` state:
    - This does the obvious thing of staying in the `Active` state if already there.
- But only if the FSM data are not `Uninitialized` when the `Queue()` event is received. 
- Otherwise (and in all other non-handled cases):
- The **second case** just logs a warning and does not change the internal state.
- The only missing piece is where the `Batches` are actually sent to the target:
- For which we use the `onTransition` mechanism:
    - You can declare multiple such blocks.
    - And all of them will be tried for matching behavior in case a state transition occurs.
    - I.e. only when the state actually changes.
```scala
onTransition {
  case Active -> Idle ⇒
    stateData match {
      case Todo(ref, queue) ⇒ ref ! Batch(queue)
      case _                ⇒ // nothing to do
    }
}
```
- The transition callback is a partial function, which:
    - Takes as input a pair of states: the current and the next state. 
- The FSM trait includes a convenience extractor for these in form of an arrow operator.
- Which conveniently reminds you of the direction of the state change which is being matched. 
- During the state change:
    - The old state data is available via `stateData` as shown.
    - And the new state data would be available as `nextStateData`.

#### Note
- Same-state transitions can be implemented (when currently in state S) using `goto(S)` or `stay()`. 
- The difference between those being that:
    - `goto(S)` will emit an event `S->S` that can be handled by `onTransition`.
    - Whereas `stay()` will not.

- To verify that this buncher actually works, it is quite easy to write a test using the Testing Actor Systems which is conveniently bundled with ScalaTest traits into AkkaSpec:



















# Reference





# Testing and Debugging Finite State Machines





# Examples










