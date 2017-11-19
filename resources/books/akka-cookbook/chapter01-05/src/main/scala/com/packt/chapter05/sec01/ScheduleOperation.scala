package com.packt.chapter05.sec01

import akka.actor.ActorSystem
import com.packt.utils.terminate

import scala.concurrent.duration._

object ScheduleOperation extends App {
  val system = ActorSystem("Hello-Akka")

  import system.dispatcher

  println("things should start to happen in about 2 seconds...")

  system.scheduler.scheduleOnce(2 seconds) {
    println(s"Sum of (1 + 2) is ${1 + 2}")
  }

  system.scheduler.schedule(2 seconds, 1 seconds) {
    println(s"Hello, Sorry for disturbing you every second")
  }

  terminate(system, 5000)

}