package com.packt.chapter01.sec02.v2

import akka.actor.{Actor, ActorSystem, Props}
import com.packt.utils.terminate

// ---------------------------------------------------------------------------------------------------------------------

class SummingActorWithConstructor(initialSum: Int) extends Actor {

  // state inside the actor
  var sum = initialSum

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
  val actor = actorSystem.actorOf(Props(classOf[SummingActorWithConstructor], 10), "summingactor")

  println(actor.path)

  actor ! "Hello"
  for (x <- 1 to 4) {
    Thread.sleep(1000)
    actor ! 1
  }

  terminate(actorSystem)

}
