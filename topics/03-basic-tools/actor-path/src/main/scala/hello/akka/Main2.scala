package hello.akka

import akka.actor.{ActorSystem, PoisonPill, Props}

object Main2 extends App {

  val system = ActorSystem("actor-path")

  val watcher1 = system.actorOf(Props[Watcher], "watcher1")

  Thread.sleep(1000)

  val counter = system.actorOf(Props[Counter], "counter")
  val watcher2 = system.actorOf(Props[Watcher], "watcher2")

  Thread.sleep(2000)
  system.terminate()

}
