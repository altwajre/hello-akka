package com.packt.chapter04.sec03

import akka.actor.{Actor, ActorSystem, Props}
import com.packt.utils.terminate

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

// ---------------------------------------------------------------------------------------------------------------------

class FutureActor extends Actor {

  import context.dispatcher

  def receive = {
    case (a: Int, b: Int) =>
      val future = Future(a + b)
      val sum = Await.result(future, 10 seconds) // not recommended, use onComplete instead
      println(s"Future result is $sum")
  }
}

// ---------------------------------------------------------------------------------------------------------------------

object FutureInsideActor extends App {
  val actorSystem = ActorSystem("Hello-Akka")
  val futureActor = actorSystem.actorOf(Props[FutureActor])
  futureActor ! (10, 20)

  terminate(actorSystem)

}