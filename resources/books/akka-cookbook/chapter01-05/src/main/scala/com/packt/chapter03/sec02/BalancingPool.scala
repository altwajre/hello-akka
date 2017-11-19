package com.packt.chapter03.sec02

import akka.actor.{Actor, ActorSystem, Props}
import akka.routing.BalancingPool
import com.packt.utils.terminate

// ---------------------------------------------------------------------------------------------------------------------

class BalancingPoolActor extends Actor {
  override def receive = {
    case _ => println(s"I am ${self.path.name}")
  }
}

// ---------------------------------------------------------------------------------------------------------------------

object BalancingPoolApp extends App {
  val actorSystem = ActorSystem("Hello-Akka")
  val router = actorSystem.actorOf(BalancingPool(5).props(Props[BalancingPoolActor]))
  for (i <- 1 to 15) {
    router ! s"Hello $i"
  }

  terminate(actorSystem)

}