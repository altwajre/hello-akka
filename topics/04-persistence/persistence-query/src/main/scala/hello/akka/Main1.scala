package hello.akka

import akka.actor.{ActorSystem, Props}
import hello.akka.Account.{CR, DR, Operation}

object Main1 extends App {

  val system = ActorSystem("persistence-fsm")

  val account = system.actorOf(Props[Account], "account")

  account ! Operation(1000, CR)
  account ! Operation(10, DR)

  Thread.sleep(1000)
  system.terminate()

}
