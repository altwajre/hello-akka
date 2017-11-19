package com.packt.chapter01.sec03

import akka.actor.{Actor, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.packt.utils.terminate

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

// ---------------------------------------------------------------------------------------------------------------------

class FibonacciActor extends Actor {

  override def receive = {
    case num: Int =>
      val fibonacciNumber = fib(num)
      sender ! fibonacciNumber
  }

  def fib(n: Int): Int = n match {
    case 0 | 1 => n
    case _ => fib(n - 1) + fib(n - 2)
  }

}

// ---------------------------------------------------------------------------------------------------------------------

object FibonacciActorApp extends App {

  implicit val timeout = Timeout(10 seconds)

  val actorSystem = ActorSystem("HelloAkka")

  val actor = actorSystem.actorOf(Props[FibonacciActor])

  // asking for result from actor
  val future: Future[Int] = (actor ? 10).mapTo[Int]
  val fibonacciNumber: Int = Await.result(future, 10 seconds)

  println(fibonacciNumber)

  terminate(actorSystem)

}