package fsm.simple

import akka.actor.FSM
import akka.util.ByteString
import fsm.simple.Buncher._

import scala.concurrent.duration._

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
