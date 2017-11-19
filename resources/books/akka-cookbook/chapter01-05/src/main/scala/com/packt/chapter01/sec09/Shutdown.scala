package com.packt.chapter01.sec09

import akka.actor.{Actor, ActorSystem, PoisonPill, Props}
import com.packt.utils.terminate

// ---------------------------------------------------------------------------------------------------------------------

case object Stop

// ---------------------------------------------------------------------------------------------------------------------

class ShutdownActor extends Actor {
  override def receive = {

    case msg: String =>
      println(msg)

    case Stop =>
      context.stop(self)

  }
}

// ---------------------------------------------------------------------------------------------------------------------

object ShutdownApp extends App {

  val actorSystem = ActorSystem("HelloAkka")

  val shutdownActor1 = actorSystem.actorOf(Props[ShutdownActor], "shutdownActor1")

  shutdownActor1 ! "Hello, take this poison"
  shutdownActor1 ! PoisonPill
  shutdownActor1 ! "Did the poison work?"

  val shutdownActor2 = actorSystem.actorOf(Props[ShutdownActor], "shutdownActor2")

  shutdownActor2 ! "Hello, please stop"
  shutdownActor2 ! Stop
  shutdownActor2 ! "Did you stop?"

  terminate(actorSystem)

}
