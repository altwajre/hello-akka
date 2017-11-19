package com.packt.chapter03.sec10

import akka.actor.{Actor, ActorSystem, Props}
import akka.routing.{DefaultResizer, RoundRobinPool}
import com.packt.utils.terminate

// ---------------------------------------------------------------------------------------------------------------------

case object Load

// ---------------------------------------------------------------------------------------------------------------------

class LoadActor extends Actor {
  def receive = {
    case Load =>
      println(s"Handing load on ${self.path}")
      Thread.sleep(10)
  }
}

// ---------------------------------------------------------------------------------------------------------------------

object ResizablePool extends App {
  val system = ActorSystem("Hello-Akka")
  val resizer = DefaultResizer(pressureThreshold = 0)
  val router = system.actorOf(RoundRobinPool(
    nrOfInstances = 1,
    resizer = Some(resizer)
  ).props(Props[LoadActor]))

  Thread.sleep(1000)

  for (i <- 1 to 5) {
    router ! Load
  }

  Thread.sleep(1000)

  for (i <- 1 to 100) {
    router ! Load
  }

  terminate(system)

}
