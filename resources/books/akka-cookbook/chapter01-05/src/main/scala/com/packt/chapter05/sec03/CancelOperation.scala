package com.packt.chapter05.sec03

import akka.actor.{Actor, ActorSystem, Cancellable, Props}
import com.packt.utils.terminate

import scala.concurrent.duration._

// ---------------------------------------------------------------------------------------------------------------------

class CancelOperation extends Actor {
  var i = 3

  def receive = {
    case "tick" => {
      println(s"Hi, Do you know i ($i) do the same task again and again")
      i = i - 1
      if (i == 0) {
        Scheduler.cancellable.cancel()
        println("cancelled timer")
      }
    }
  }
}

// ---------------------------------------------------------------------------------------------------------------------

object Scheduler extends App {
  val system = ActorSystem("Hello-Akka")

  import system.dispatcher

  val actor = system.actorOf(Props[CancelOperation])

  val cancellable: Cancellable = system.scheduler.schedule(
    initialDelay = 0 seconds,
    interval = 1 seconds,
    receiver = actor,
    message = "tick"
  )

  terminate(system, 5000)

}