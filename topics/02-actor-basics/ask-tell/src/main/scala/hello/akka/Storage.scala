package hello.akka

import akka.actor.Actor
import hello.akka.Storage.AddUser

class Storage extends Actor {

  var users = List.empty[User]

  override def receive: Receive = {

    case AddUser(user) =>
      println(s"Storage: $user added")
      users = user :: users

  }
}

object Storage {

  sealed trait StorageMsg

  case class AddUser(user: User) extends StorageMsg

}
