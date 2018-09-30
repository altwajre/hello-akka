package hello.akka

import akka.actor.ActorLogging
import akka.persistence._
import hello.akka.Counter._

class Counter extends PersistentActor with ActorLogging {

  println("Counter: starting...")

  override def persistenceId: String = "counter-example"

  var state: State = State(count = 0)

  def updateState(evt: Evt): Unit = evt match {
    case Evt(Increment(count)) =>
      state = State(count = state.count + count)
      takeSnapshot()
    case Evt(Decrement(count)) =>
      state = State(count = state.count - count)
      takeSnapshot()
  }

  override def receiveRecover: Receive = {

    case evt: Evt =>
      println(s"Counter: received $evt on receiveRecover")
      updateState(evt)

    case SnapshotOffer(_, snapshot: State) =>
      println(s"Counter: received snapshot on receiveRecover with $snapshot")
      state = snapshot

    case RecoveryCompleted =>
      println("Counter: Recovery completed, switching to receiveCommand mode")

  }

  override def receiveCommand: Receive = {

    case cmd@Cmd(op) =>
      println(s"Counter: received $cmd on receiveCommand")
      persist(Evt(op)) { evt =>
        updateState(evt)
      }

    case "print" =>
      println(s"Counter: current state is $state")

    case SaveSnapshotSuccess(metadata) =>
      println(s"Counter: Snapshot saved successfully: $metadata")

    case SaveSnapshotFailure(metadata, cause) =>
      println(s"Counter: Snapshot save failed: $metadata ($cause)")

  }

  //override def recovery: Recovery = Recovery.none

  def takeSnapshot(): Unit = {
    if (state.count % 5 == 0) {
      println(s"Counter: taking snapshot")
      saveSnapshot(state)
    }
  }

}

object Counter {

  sealed trait Operation {
    val count: Int
  }

  case class Increment(count: Int) extends Operation

  case class Decrement(count: Int) extends Operation

  case class Cmd(op: Operation)

  case class Evt(op: Operation)

  case class State(count: Int)

}
