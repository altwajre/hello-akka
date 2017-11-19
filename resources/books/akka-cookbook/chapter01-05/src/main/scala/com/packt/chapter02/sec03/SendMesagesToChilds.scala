package com.packt.chapter02.sec03

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import com.packt.utils.terminate

// ---------------------------------------------------------------------------------------------------------------------

case class DoubleValue(x: Int)

case object CreateChild

case object Send

case class Response(x: Int)

// ---------------------------------------------------------------------------------------------------------------------

class DoubleActor extends Actor {
  def receive = {
    case DoubleValue(number) =>
      println(s"${self.path.name} Got the number $number")
      sender ! Response(number * 2)
  }
}

// ---------------------------------------------------------------------------------------------------------------------

class ParentActor extends Actor {
  val random = new scala.util.Random
  val children = scala.collection.mutable.ListBuffer[ActorRef]()

  def receive = {
    case CreateChild =>
      children ++= List(context.actorOf(Props[DoubleActor]))
    case Send =>
      println(s"Sending messages to child")
      children.foreach { child =>
        child ! DoubleValue(random.nextInt(10))
      }
    case Response(x) => println(s"Parent: Response from child ${sender.path.name} is $x")
  }
}

// ---------------------------------------------------------------------------------------------------------------------

object SendMessagesToChild extends App {
  val actorSystem = ActorSystem("Hello-Akka")
  val parent = actorSystem.actorOf(Props[ParentActor], "parent")
  parent ! CreateChild
  parent ! CreateChild
  parent ! CreateChild
  parent ! Send

  terminate(actorSystem)

}