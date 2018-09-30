package hello.akka.become

import akka.actor.{Actor, Stash}
import hello.akka.become.UserStorage.{Connect, Disconnect, Operation}

class UserStorage extends Actor with Stash {

  def connected: Receive = {

    case Disconnect =>
      println("UserStorage: disconnected from DB")
      context.unbecome()

    case Operation(op, user) =>
      println(s"UserStorage: received $op to do in user $user")

  }

  def disconnected: Receive = {

    case Connect =>
      println("UserStorage: connected to DB")
      unstashAll()
      context.become(connected)

    case x =>
      println(s"UserStorage: disconnected behavior does not understand $x, stashing...")
      stash()
  }

  override def receive: Receive = disconnected

}

object UserStorage {

  trait DatabaseOperation

  case object Create extends DatabaseOperation

  case object Read extends DatabaseOperation

  case object Update extends DatabaseOperation

  case object Delete extends DatabaseOperation

  case object Connect

  case object Disconnect

  case class Operation(operation: DatabaseOperation, user: Option[User])

}
