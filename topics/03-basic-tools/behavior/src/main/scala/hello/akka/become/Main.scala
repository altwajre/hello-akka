package hello.akka.become

import akka.actor.{ActorSystem, Props}
import hello.akka.become.UserStorage.{Connect, Create, Disconnect, Operation}

object Main extends App {

  val system = ActorSystem("behavior-become")

  val userStorage = system.actorOf(Props[UserStorage], "user-storage")

  userStorage ! Operation(Create, Some(User("Admin", "admin@mail.com")))
  userStorage ! Connect
  userStorage ! Disconnect

  Thread.sleep(1000)
  system.terminate()

}
