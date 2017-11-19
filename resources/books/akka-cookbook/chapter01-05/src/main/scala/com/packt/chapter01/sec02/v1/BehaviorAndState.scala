package com.packt.chapter01.sec02.v1

import akka.actor.{Actor, ActorSystem, Props}
import com.packt.chapter01.sec02.v2.SummingActorWithConstructor
import com.packt.utils.terminate

// ---------------------------------------------------------------------------------------------------------------------

class SummingActor extends Actor {

  // state inside the actor
  var sum = 0

  // behavior which is applied on the state
  override def receive = {

    // receive message as integer
    case x: Int =>
      sum = sum + x
      println(s"my state as sum is $sum")

    // receives default message
    case _ =>
      println("I don't know what you are talking about")

  }
}

// ---------------------------------------------------------------------------------------------------------------------

object BehaviorAndState extends App {

  val actorSystem = ActorSystem("HelloAkka")

  // creating an actor inside the actor system
  val actor = actorSystem.actorOf(Props[SummingActor])

  println(actor.path)

  terminate(actorSystem)

}
