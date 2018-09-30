package hello.akka

import akka.actor.{ActorSystem, Props}
import hello.akka.Counter.{Cmd, Decrement, Increment}

object Main extends App {

  val system = ActorSystem("persistent-actor")

  val counter = system.actorOf(Props[Counter], "counter")

  counter ! Cmd(Increment(3))
  counter ! Cmd(Increment(5))
  counter ! Cmd(Decrement(3))

  counter ! "print"

  Thread.sleep(1000)
  system.terminate()

}
