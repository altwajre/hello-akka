package hello.akka

import akka.actor.{ActorSystem, PoisonPill, Props}

object Main1 extends App {

  val system = ActorSystem("actor-path")

  val counter1 = system.actorOf(Props[Counter], "counter")
  println(s"Actor reference for counter1: $counter1")

  val counterSelection1 = system.actorSelection("/user/counter")
  println(s"Actor selection for counter1: $counterSelection1")

  counter1 ! PoisonPill
  Thread.sleep(1000)

  // -------------------------------------------------------------

  val counter2 = system.actorOf(Props[Counter], "counter")
  println(s"Actor reference for counter2: $counter2")

  val counterSelection2 = system.actorSelection("/user/counter")
  println(s"Actor selection for counter2: $counterSelection2")

  Thread.sleep(2000)
  system.terminate()

}
