package com.packt.chapter02.sec01

import akka.actor.{Actor, ActorSystem, Props}
import com.packt.utils.terminate

// ---------------------------------------------------------------------------------------------------------------------

case object CreateChild

case class Greet(msg: String)

// ---------------------------------------------------------------------------------------------------------------------

class ChildActor extends Actor {
  override def receive = {
    case Greet(msg) =>
      println(s"My parent [${self.path.parent}] greeted me [${self.path} : $msg]")
  }
}

// ---------------------------------------------------------------------------------------------------------------------

class ParentActor extends Actor {
  override def receive = {
    case CreateChild =>
      val child = context.actorOf(Props[ChildActor], "child")
      child ! Greet("Welcome to the world my child")
  }
}

// ---------------------------------------------------------------------------------------------------------------------

object ParentChild extends App {

  val actorSystem = ActorSystem("Supervision")

  val parent = actorSystem.actorOf(Props[ParentActor], "parent")

  parent ! CreateChild

  terminate(actorSystem)

}
