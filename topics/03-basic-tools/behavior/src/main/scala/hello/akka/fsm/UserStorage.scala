package hello.akka.fsm

import akka.actor.{FSM, Stash}
import hello.akka.fsm.UserStorage._

class UserStorage extends FSM[State, Data] with Stash {

  startWith(Disconnected, EmptyData)

  when(Disconnected) {

    case Event(Connect, _) =>
      println("UserStorage: connected to DB")
      unstashAll()
      goto(Connected) using EmptyData

    case Event(_, _ ) =>
      stash()
      stay using EmptyData

  }

  when (Connected) {

    case Event(Disconnect, _) =>
      println("UserStorage: disconnected from DB")
      goto(Disconnected) using EmptyData

    case Event(Operation(op, user), _) =>
      println(s"UserStorage: received $op to do in user $user")
      stay using EmptyData

  }

  initialize()

}

object UserStorage {

  sealed trait State
  case object Connected extends State
  case object Disconnected extends State

  sealed trait Data
  case object EmptyData extends Data

  trait DatabaseOperation

  case object Create extends DatabaseOperation

  case object Read extends DatabaseOperation

  case object Update extends DatabaseOperation

  case object Delete extends DatabaseOperation

  case object Connect

  case object Disconnect

  case class Operation(operation: DatabaseOperation, user: Option[User])

}
