package com.packt.chapter05.sec02

import akka.actor.{Actor, ActorSystem, Props}
import com.packt.utils.terminate

import scala.concurrent.duration._

// ---------------------------------------------------------------------------------------------------------------------

class RandomIntAdder extends Actor {
  val r = scala.util.Random

  def receive = {
    case "tick" =>
      val randomA = r.nextInt(10)
      val randomB = r.nextInt(10)
      println(s"sum of $randomA and $randomB is ${randomA + randomB}")

  }
}

// ---------------------------------------------------------------------------------------------------------------------

object ScheduleActor extends App {
  val system = ActorSystem("Hello-Akka")

  import system.dispatcher

  println("things should start to happen in about 2 seconds...")

  val actor = system.actorOf(Props[RandomIntAdder])
  system.scheduler.scheduleOnce(2 seconds, actor, "tick")
  system.scheduler.schedule(3 seconds, 1 seconds, actor, "tick")

  terminate(system, 5000)

}