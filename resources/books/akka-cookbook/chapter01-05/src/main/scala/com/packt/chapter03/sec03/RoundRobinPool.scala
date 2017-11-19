package com.packt.chapter03.sec03

import akka.actor.{Actor, ActorSystem, Props}
import akka.routing.RoundRobinPool
import com.packt.utils.terminate

// ---------------------------------------------------------------------------------------------------------------------

class RoundRobinPoolActor extends Actor {
  override def receive = {
    case _ => println(s"I am ${self.path.name}")
  }
}

// ---------------------------------------------------------------------------------------------------------------------

object RoundRobinPoolApp extends App {
  val actorSystem = ActorSystem("Hello-Akka")
  val router = actorSystem.actorOf(RoundRobinPool(5).props(Props[RoundRobinPoolActor]))
  for (i <- 1 to 5) {
    router ! s"Hello $i"
  }

  terminate(actorSystem)

}