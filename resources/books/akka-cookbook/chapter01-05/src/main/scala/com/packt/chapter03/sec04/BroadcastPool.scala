package com.packt.chapter03.sec04

import akka.actor.{Actor, ActorSystem, Props}
import akka.routing.BroadcastPool
import com.packt.utils.terminate

// ---------------------------------------------------------------------------------------------------------------------

class BroadcastPoolActor extends Actor {
  override def receive = {
    case msg: String => println(s"$msg, I am ${self.path.name}")
    case _ =>
  }
}

// ---------------------------------------------------------------------------------------------------------------------

object BroadcastPoolApp extends App {
  val actorSystem = ActorSystem("Hello-Akka")
  val router = actorSystem.actorOf(BroadcastPool(5).props(Props[BroadcastPoolActor]))
  router ! "hello"

  terminate(actorSystem)

}

