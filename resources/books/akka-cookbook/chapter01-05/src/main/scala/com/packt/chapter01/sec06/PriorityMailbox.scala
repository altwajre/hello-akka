package com.packt.chapter01.sec06

import akka.actor.{Actor, ActorSystem, Props}
import akka.dispatch.{PriorityGenerator, UnboundedPriorityMailbox}
import com.packt.utils.terminate
import com.typesafe.config.Config

// ---------------------------------------------------------------------------------------------------------------------

class MyPriorityActor extends Actor {

  override def receive = {

    // Int messages
    case x: Int => println(x)

    // String messages
    case x: String => println(x)

    // Long messages
    case x: Long => println(x)

    // Other messages
    case x => println(x)

  }
}

// ---------------------------------------------------------------------------------------------------------------------

class MyPriorityActorMailbox(settings: ActorSystem.Settings, config: Config) extends UnboundedPriorityMailbox(

  // create a new PriorityGenerator, lower prio means more important
  PriorityGenerator {

    // Int messages
    case x: Int => 1

    // String messages
    case x: String => 0

    // Long messages
    case x: Long => 2

    // Other messages
    case _ => 3

  }

)

// ---------------------------------------------------------------------------------------------------------------------

object PriorityMailboxApp extends App {

  val actorSystem = ActorSystem("HelloAkka")

  val myPriorityActor = actorSystem.actorOf(Props[MyPriorityActor].withDispatcher("prio-dispatcher"))

  myPriorityActor ! 6.0
  myPriorityActor ! 1
  myPriorityActor ! 5.0
  myPriorityActor ! 3
  myPriorityActor ! "Hello"
  myPriorityActor ! 5
  myPriorityActor ! "I am priority actor"
  myPriorityActor ! "I process string messages first, then integer, long and other"

  terminate(actorSystem)

}