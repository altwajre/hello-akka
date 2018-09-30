package hello.akka

import akka.actor.{Actor, ActorSystem, Props}

case class WhoToGreet(who: String)

class Greeter extends Actor {
  override def receive: Receive = {
    case WhoToGreet(who) => println(s"Hello $who")
  }
}

object HelloAkkaScala extends App {

  val system = ActorSystem("hello-akka")

  val greeter = system.actorOf(Props[Greeter], "greeter")

  greeter ! WhoToGreet("World")

  Thread.sleep(1000)
  system.terminate()

}
