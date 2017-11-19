package com.packt.chapter03.sec08

import akka.actor.{Actor, ActorSystem, Props}
import akka.routing.RandomPool
import com.packt.utils.terminate

// ---------------------------------------------------------------------------------------------------------------------

class RandomPoolActor extends Actor {
  override def receive = {
    case msg: String => println(s"I am ${self.path.name}")
    case _ =>
  }
}

// ---------------------------------------------------------------------------------------------------------------------

object RandomPoolApp extends App {
  val actorSystem = ActorSystem("Hello-Akka")
  val router = actorSystem.actorOf(RandomPool(5).props(Props[RandomPoolActor]))
  for (i <- 1 to 5) {
    router ! s"Hello $i"
  }

  terminate(actorSystem)

}
