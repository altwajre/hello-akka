package com.packt.chapter05.sec05

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import com.packt.utils.terminate

// ---------------------------------------------------------------------------------------------------------------------

class LoggingActor extends Actor with ActorLogging {
  def receive = {
    case (a: Int, b: Int) =>
      log.info(s"sum of $a and $b is ${a + b}")
    case msg =>
      log.warning(s"I don't understand this message: $msg")
  }
}

// ---------------------------------------------------------------------------------------------------------------------

object Logging extends App {
  val system = ActorSystem("hello-Akka")
  val actor = system.actorOf(Props[LoggingActor], "SumActor")

  actor ! (10, 12)
  actor ! "Hello"

  terminate(system)

}