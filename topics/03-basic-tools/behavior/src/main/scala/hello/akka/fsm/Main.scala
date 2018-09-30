package hello.akka.fsm

import akka.actor.{ActorSystem, Props}
import hello.akka.fsm.UserStorage.{Connect, Create, Disconnect, Operation}

object Main extends App {

  val system = ActorSystem("behavior-fsm")

  val userStorage = system.actorOf(Props[UserStorage], "user-storage")

  userStorage ! Operation(Create, Some(User("Admin", "admin@mail.com")))
  userStorage ! Connect
  userStorage ! Disconnect

  Thread.sleep(1000)
  system.terminate()

}
