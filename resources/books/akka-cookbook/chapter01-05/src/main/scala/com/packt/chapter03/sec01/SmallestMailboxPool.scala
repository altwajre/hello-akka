package com.packt.chapter03.sec01

import akka.actor.{Actor, ActorSystem, Props}
import akka.routing.SmallestMailboxPool
import com.packt.utils.terminate

// ---------------------------------------------------------------------------------------------------------------------

class SmallestMailboxActor extends Actor {
  override def receive = {
    case _ => println(s"I am ${self.path.name}")
  }
}

// ---------------------------------------------------------------------------------------------------------------------

object SmallestMailboxApp extends App {
  val actorSystem = ActorSystem("Hello-Akka")
  val router = actorSystem.actorOf(SmallestMailboxPool(5).props(Props[SmallestMailboxActor]))
  for (i <- 1 to 15) {
    router ! s"Hello $i"
  }

  terminate(actorSystem)

}
