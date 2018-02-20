# FSM - Overview
- The FSM (Finite State Machine) is available as a mixin for the Akka Actor.
- A FSM can be described as a set of relations of the form:
    - **State(S) x Event(E) -> Actions(A), State(S’)**
- These relations are interpreted as meaning:
    - _If we are in state S and the event E occurs, we should perform the actions A and make a transition to the state S’_.

# A Simple Example
- See [Example](./fsm-examples/src/test/scala/fsm/simple/FSMDocSpec.scala)
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

##  
- To verify that this buncher actually works:
    - It is quite easy to write a test using the [Testing Actor Systems](../11-testing-actor-systems).
    - Which is conveniently bundled with _ScalaTest_ traits into `AkkaSpec`:
```scala
import akka.actor.Props
import scala.collection.immutable

object FSMDocSpec {
  // messages and data types
}

class FSMDocSpec extends MyFavoriteTestFrameWorkPlusAkkaTestKit {
  import FSMDocSpec._

  import akka.actor.{ ActorRef, FSM }
  import scala.concurrent.duration._
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
  object DemoCode {
    trait StateType
    case object SomeState extends StateType
    case object Processing extends StateType
    case object Error extends StateType
    case object Idle extends StateType
    case object Active extends StateType

    class Dummy extends FSM[StateType, Int] {
      class X
      val newData = 42
      object WillDo
      object Tick

      when(SomeState) {
        case Event(msg, _) ⇒
          goto(Processing) using (newData) forMax (5 seconds) replying (WillDo)
      }

      onTransition {
        case Idle -> Active ⇒ setTimer("timeout", Tick, 1 second, repeat = true)
        case Active -> _    ⇒ cancelTimer("timeout")
        case x -> Idle      ⇒ log.info("entering Idle from " + x)
      }

      onTransition(handler _)

      def handler(from: StateType, to: StateType) {
        // handle it here ...
      }

      when(Error) {
        case Event("stop", _) ⇒
          // do cleanup ...
          stop()
      }

      when(SomeState)(transform {
        case Event(bytes: ByteString, read) ⇒ stay using (read + bytes.length)
      } using {
        case s @ FSM.State(state, read, timeout, stopReason, replies) if read > 1000 ⇒
          goto(Processing)
      })

      val processingTrigger: PartialFunction[State, State] = {
        case s @ FSM.State(state, read, timeout, stopReason, replies) if read > 1000 ⇒
          goto(Processing)
      }

      when(SomeState)(transform {
        case Event(bytes: ByteString, read) ⇒ stay using (read + bytes.length)
      } using processingTrigger)

      onTermination {
        case StopEvent(FSM.Normal, state, data)         ⇒ // ...
        case StopEvent(FSM.Shutdown, state, data)       ⇒ // ...
        case StopEvent(FSM.Failure(cause), state, data) ⇒ // ...
      }

      whenUnhandled {
        case Event(x: X, data) ⇒
          log.info("Received unhandled event: " + x)
          stay
        case Event(msg, _) ⇒
          log.warning("Received unknown event: " + msg)
          goto(Error)
      }

    }

    import akka.actor.LoggingFSM
    class MyFSM extends LoggingFSM[StateType, Data] {
      override def logDepth = 12
      onTermination {
        case StopEvent(FSM.Failure(_), state, data) ⇒
          val lastEvents = getLog.mkString("\n\t")
          log.warning("Failure in state " + state + " with data " + data + "\n" +
            "Events leading up to this point:\n\t" + lastEvents)
      }
      // ...
    }

  }

  "simple finite state machine" must {

    "demonstrate NullFunction" in {
      class A extends FSM[Int, Null] {
        val SomeState = 0
        when(SomeState)(FSM.NullFunction)
      }
    }

    "batch correctly" in {
      val buncher = system.actorOf(Props(classOf[Buncher], this))
      buncher ! SetTarget(testActor)
      buncher ! Queue(42)
      buncher ! Queue(43)
      expectMsg(Batch(immutable.Seq(42, 43)))
      buncher ! Queue(44)
      buncher ! Flush
      buncher ! Queue(45)
      expectMsg(Batch(immutable.Seq(44)))
      expectMsg(Batch(immutable.Seq(45)))
    }

    "not batch if uninitialized" in {
      val buncher = system.actorOf(Props(classOf[Buncher], this))
      buncher ! Queue(42)
      expectNoMsg
    }
  }
}
```

# Reference

## The FSM Trait and Object
- The `FSM` trait inherits directly from `Actor`.
- When you extend `FSM` you must be aware that an actor is actually created: 
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

#### Note
- The `FSM` trait defines a `receive` method:
- Which handles internal messages and passes everything else through to the FSM logic:
- According to the current state. 
- When overriding the `receive` method:
- State timeout handling depends on actually passing the messages through the FSM logic.
##
- The `FSM` trait takes two type parameters:
    - The supertype of all state names, usually a sealed trait with case objects extending it.
    - The type of the state data which are tracked by the `FSM` module itself.

#### Note
- The state data together with the state name describe the internal state of the state machine.
- If you stick to this scheme and do not add mutable fields to the `FSM` class:
- You have the advantage of making all changes of the internal state explicit in a few well-known places.

## Defining States
- A state is defined by one or more invocations of the method:
```
when(<name>[, stateTimeout = <timeout>])(stateFunction)
```
- The given name must be an object:
- Which is type-compatible with the first type parameter given to the `FSM` trait. 
- This object is used as a hash key:
- So you must ensure that it properly implements `equals` and `hashCode`.
- In particular it must not be mutable. 
- The easiest fit for these requirements are case objects.
- If the `stateTimeout` parameter is given:
- Then all transitions into this state:
    - Including staying:
    - Receive this timeout by default. 
- Initiating the transition with an explicit timeout may be used to override this default.
    - See [Initiating Transitions](#initiating-transitions). 
- The state timeout of any state may be changed during action processing:
    - With `setStateTimeout(state, duration)`. 
- This enables runtime configuration:
    - E.g. via external message.
- The `stateFunction` argument is a `PartialFunction[Event, State]`:
- Which is conveniently given using the partial function literal syntax:
```scala
when(Idle) {
  case Event(SetTarget(ref), Uninitialized) ⇒
    stay using Todo(ref, Vector.empty)
}

when(Active, stateTimeout = 1 second) {
  case Event(Flush | StateTimeout, t: Todo) ⇒
    goto(Idle) using t.copy(queue = Vector.empty)
}
```
- The `Event(msg: Any, data: D)` case class:
- Is parameterized with the data type held by the FSM for convenient pattern matching.

#### Warning
- It is required that you define handlers for each of the possible FSM states:
- Otherwise there will be failures when trying to switch to undeclared states.
##
- It is recommended practice:
- To declare the states as objects extending a sealed trait:
- And then verify that there is a when clause for each of the states. 
- If you want to leave the handling of a state “unhandled”:
- It still needs to be declared like this:
```scala
when(SomeState)(FSM.NullFunction)
```

## Defining the Initial State
- Each FSM needs a starting point, which is declared using:
```scala
startWith(state, data[, timeout])
```
- The optionally given timeout argument overrides any specification given for the desired initial state. 
- If you want to cancel a default timeout, use `None`.

## Unhandled Events
- If a state doesn’t handle a received event a warning is logged. 
- If you want to do something else in this case:
- You can specify that with `whenUnhandled(stateFunction)`:
```scala
whenUnhandled {
  case Event(x: X, data) ⇒
    log.info("Received unhandled event: " + x)
    stay
  case Event(msg, _) ⇒
    log.warning("Received unknown event: " + msg)
    goto(Error)
}
```
- Within this handler the state of the FSM may be queried using the `stateName` method.

#### Important
- This handler is not stacked:
- Meaning that each invocation of `whenUnhandled` replaces the previously installed handler.

## Initiating Transitions
- The result of any `stateFunction` must be a definition of the next state:
- Unless terminating the FSM.
- See [Termination from Inside](#termination-from-inside). 
- The state definition can either be the current state:
- As described by the stay directive:
- Or it is a different state as given by `goto(state)`. 
- The resulting object allows further qualification by way of the modifiers described in the following:
- **`forMax(duration)`**: 
    - This modifier sets a state timeout on the next state. 
    - This means that a timer is started which upon expiry sends a `StateTimeout` message to the FSM. 
    - This timer is canceled upon reception of any other message.
    - You can rely on the fact that the `StateTimeout` message will not be processed after an intervening message. 
    - This modifier can also be used to override any default timeout:
    - Which is specified for the target state. 
    - If you want to cancel the default timeout, 
    - use `Duration.Inf`.
- **`using(data)`**: 
    - This modifier replaces the old state data with the new data given. 
    - If you follow the advice above, 
    - this is the only place where internal state data are ever modified.
- **`replying(msg)`**: 
    - This modifier sends a reply to the currently processed message:
    - and otherwise does not modify the state transition.
- All modifiers can be chained to achieve a nice and concise description:
```scala
when(SomeState) {
  case Event(msg, _) ⇒
    goto(Processing) using (newData) forMax (5 seconds) replying (WillDo)
}
```
- The parentheses are not actually needed in all cases, 
- but they visually distinguish between modifiers and their arguments,
- and therefore make the code even more pleasant to read.
  
#### Note
- The return statement may not be used in `when` blocks or similar; 
- this is a Scala restriction. 
- Either refactor your code using `if () ... else ...`,
- or move it into a method definition.
##

## Monitoring Transitions
- Transitions occur “between states” conceptually, 
- which means after any actions you have put into the event handling block.
- This is obvious since the next state is only defined by the value returned by the event handling logic. 
- You do not need to worry about the exact order with respect to setting the internal state variable, 
- as everything within the `FSM` actor is running single-threaded anyway.

### Internal Monitoring
- Up to this point, 
- the FSM DSL has been centered on states and events. 
- The dual view is to describe it as a series of transitions. 
- This is enabled by the method:
```scala
onTransition(handler)
```
- Which associates actions with a transition instead of with a state and event. 
- The handler is a partial function which takes a pair of states as input.
- No resulting state is needed as it is not possible to modify the transition in progress.
```scala
onTransition {
  case Idle -> Active ⇒ setTimer("timeout", Tick, 1 second, repeat = true)
  case Active -> _    ⇒ cancelTimer("timeout")
  case x -> Idle      ⇒ log.info("entering Idle from " + x)
}
```
- The convenience extractor `->` enables decomposition of the pair of states,
- with a clear visual reminder of the transition’s direction. 
- As usual in pattern matches, an underscore may be used for irrelevant parts.
- Alternatively you could bind the unconstrained state to a variable, 
- E.g. for logging as shown in the last case.
- It is also possible to pass a function object accepting two states to `onTransition`, 
- in case your transition handling logic is implemented as a method:
```scala
onTransition(handler _)

def handler(from: StateType, to: StateType) {
  // handle it here ...
}
```
- The handlers registered with this method are stacked, 
- so you can intersperse `onTransition` blocks with when blocks as suits your design. 
- However, all handlers will be invoked for each transition, 
- not only the first matching one. 
- This is designed specifically,
- so you can put all transition handling for a certain aspect,
- into one place without having to worry about earlier declarations shadowing later ones.
- The actions are still executed in declaration order.

#### Note
- This kind of internal monitoring may be used to structure your FSM according to transitions, 
- so that for example the cancellation of a timer upon leaving a certain state,
- cannot be forgot when adding new target states.

### External Monitoring
- External actors may be registered to be notified of state transitions,
- by sending a message `SubscribeTransitionCallBack(actorRef)`. 
- The named actor will be sent a `CurrentState(self, stateName)` message immediately,
- and will receive `Transition(actorRef, oldState, newState)` messages whenever a state change is triggered.
- Please note that a state change includes the action of performing an `goto(S)`, 
- while already being state `S`. 
- In that case the monitoring actor will be notified with a `Transition(ref,S,S)` message. 
- This may be useful if your FSM should react on all (also same-state) transitions. 
- In case you’d rather not emit events for same-state transitions use `stay()` instead of `goto(S)`.
- External monitors may be unregistered:
    - by sending `UnsubscribeTransitionCallBack(actorRef)` to the `FSM` actor.
- Stopping a listener without unregistering:
    - will not remove the listener from the subscription list; 
    - use `UnsubscribeTransitionCallback` before stopping the listener.

## Transforming State
- The partial functions supplied as argument to the `when()` blocks:
- can be transformed using Scala’s full supplement of functional programming tools. 
- In order to retain type inference, 
- there is a helper function which may be used,
- in case some common handling logic shall be applied to different clauses:
```scala
when(SomeState)(transform {
  case Event(bytes: ByteString, read) ⇒ stay using (read + bytes.length)
} using {
  case s @ FSM.State(state, read, timeout, stopReason, replies) if read > 1000 ⇒
    goto(Processing)
})
```
- The arguments to this method may also be stored, 
- to be used several times, 
- e.g. when applying the same transformation to several `when()` blocks:
```scala
val processingTrigger: PartialFunction[State, State] = {
  case s @ FSM.State(state, read, timeout, stopReason, replies) if read > 1000 ⇒
    goto(Processing)
}

when(SomeState)(transform {
  case Event(bytes: ByteString, read) ⇒ stay using (read + bytes.length)
} using processingTrigger)
```

## Timers
- Besides state timeouts, 
- FSM manages timers identified by `String` names. 
- You may set a timer using:
```scala
setTimer(name, msg, interval, repeat)
```
- where msg is the message object which will be sent after the duration interval has elapsed. 
- If repeat is true, 
- then the timer is scheduled at fixed rate given by the interval parameter. 
- Any existing timer with the same name will automatically be canceled before adding the new timer.
- Timers may be canceled using:
```scala
cancelTimer(name)
```
- which is guaranteed to work immediately, 
- meaning that the scheduled message will not be processed after this call,
- even if the timer already fired and queued it. 
- The status of any timer may be inquired with:
```scala
isTimerActive(name)
```
- These named timers complement state timeouts,
- because they are not affected by intervening reception of other messages.

## Termination from Inside
- The FSM is stopped by specifying the result state as:
```
stop([reason[, data]])
```
- The reason must be one of:
- `Normal` (which is the default), `Shutdown` or `Failure(reason)`, 
- and the second argument may be given to change the state data which is available during termination handling.

#### Note
- `stop` does not abort the actions and stop the FSM immediately. 
- The stop action must be returned from the event handler in the same way as a state transition,
- but the `return` statement may not be used within a `when` block.
```scala
when(Error) {
  case Event("stop", _) ⇒
    // do cleanup ...
    stop()
}
```
- You can use `onTermination(handler)` to specify custom code that is executed when the FSM is stopped. 
- The handler is a partial function which takes a `StopEvent(reason, stateName, stateData)` as argument:
```scala
onTermination {
  case StopEvent(FSM.Normal, state, data)         ⇒ // ...
  case StopEvent(FSM.Shutdown, state, data)       ⇒ // ...
  case StopEvent(FSM.Failure(cause), state, data) ⇒ // ...
}
```
- As for the `whenUnhandled` case, this handler is not stacked, 
- so each invocation of `onTermination` replaces the previously installed handler.

## Termination from Outside
- When an `ActorRef` associated to a FSM is stopped using the `stop()` method, 
- its `postStop` hook will be executed. 
- The default implementation by the `FSM` trait:
- is to execute the `onTermination` handler,
- if that is prepared to handle a `StopEvent(Shutdown, ...)`.

#### Warning
- In case you override `postStop` and want to have your `onTermination` handler called, 
- do not forget to call super.postStop.

# Testing and Debugging Finite State Machines
- During development and for trouble shooting,
- FSMs need care just as any other actor. 
- There are specialized tools available as described in [Testing and Finite State Machines](../11-testing-actor-systems#testing-finite-state-machines) and in the following.

## Event Tracing
- The setting `akka.actor.debug.fsm` in [configuration](../../02-general-concepts/09-configuration) enables logging of an event trace by `LoggingFSM` instances:
```scala
class MyFSM extends LoggingFSM[StateType, Data] {
  override def logDepth = 12
  onTermination {
    case StopEvent(FSM.Failure(_), state, data) ⇒
      val lastEvents = getLog.mkString("\n\t")
      log.warning("Failure in state " + state + " with data " + data + "\n" +
        "Events leading up to this point:\n\t" + lastEvents)
  }
  // ...
}
```
- This FSM will log at DEBUG level:
    - All processed events, including `StateTimeout` and scheduled timer messages.
    - Every setting and cancellation of named timers.
    - All state transitions.
- Life cycle changes and special messages can be logged as described for [Actors](../11-testing-actor-systems).

## Rolling Event Log
- The `LoggingFSM` trait adds one more feature to the FSM: 
- a rolling event log which may be used during debugging,
- for tracing how the FSM entered a certain failure state,
- or for other creative uses:
```scala
class MyFSM extends LoggingFSM[StateType, Data] {
  override def logDepth = 12
  onTermination {
    case StopEvent(FSM.Failure(_), state, data) ⇒
      val lastEvents = getLog.mkString("\n\t")
      log.warning("Failure in state " + state + " with data " + data + "\n" +
        "Events leading up to this point:\n\t" + lastEvents)
  }
  // ...
}
```
- The `logDepth` defaults to zero, which turns off the event log.

#### Warning
- The log buffer is allocated during actor creation, 
- which is why the configuration is done using a virtual method call. 
- If you want to override with a `val`, 
- make sure that its initialization happens before the initializer of `LoggingFSM` runs, 
- and do not change the value returned by `logDepth` after the buffer has been allocated.
##

- The contents of the event log are available using method `getLog`, 
- which returns an `IndexedSeq[LogEntry]` where the oldest entry is at index zero.

# Examples
- A bigger FSM example contrasted with Actor’s `become`/`unbecome`,
- can be downloaded as a ready to run [Akka FSM sample](https://example.lightbend.com/v1/download/akka-samples-fsm-scala?_ga=2.36208611.231864662.1519093539-542223074.1518507267) together with a tutorial. 
- The source code of this sample can be found in the [Akka Samples Repository](https://github.com/akka/akka-samples/tree/2.5/akka-sample-fsm-scala).
