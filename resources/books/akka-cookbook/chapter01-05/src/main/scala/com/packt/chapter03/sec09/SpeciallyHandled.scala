package com.packt.chapter03.sec09

import akka.actor.{Actor, ActorSystem, PoisonPill, Props}
import akka.routing.{Broadcast, RandomPool}
import com.packt.utils.terminate

// ---------------------------------------------------------------------------------------------------------------------

case object Handle

// ---------------------------------------------------------------------------------------------------------------------

class SpeciallyHandledActor extends Actor {
  override def receive = {
    case Handle => println(s"${self.path.name} says hello")
  }
}

// ---------------------------------------------------------------------------------------------------------------------

object SpeciallyHandled extends App {
  val actorSystem = ActorSystem("Hello-Akka")
  val router = actorSystem.actorOf(RandomPool(5).props(Props[SpeciallyHandledActor]))
  router ! Broadcast(Handle)
  router ! Broadcast(PoisonPill)
  router ! Handle

  terminate(actorSystem)

}