package com.packt.chapter05.sec04

import akka.actor.{Actor, ActorSystem, Props}
import akka.pattern.{CircuitBreaker, ask}
import akka.util.Timeout
import com.packt.utils.terminate

import scala.concurrent.duration._

// ---------------------------------------------------------------------------------------------------------------------

case class FetchRecord(recordID: Int)

case class Person(name: String, age: Int)

// ---------------------------------------------------------------------------------------------------------------------

object DB {
  val data = Map(
    1 -> Person("John", 26),
    2 -> Person("Peter", 26),
    3 -> Person("James", 26),
    4 -> Person("Alice", 26),
    5 -> Person("Henry", 26),
    6 -> Person("Abby", 26),
    7 -> Person("Jason", 26),
    8 -> Person("Emily", 26),
    9 -> Person("Garth", 26),
    10 -> Person("Leroy", 26),
    11 -> Person("Penny", 26),
    12 -> Person("Agnus", 26),
    13 -> Person("Zander", 26),
    14 -> Person("Howard", 26),
    15 -> Person("Kenny", 26),
    16 -> Person("Paul", 26),
    17 -> Person("Susan", 26),
    18 -> Person("Dirk", 26),
    19 -> Person("Sammy", 26),
    20 -> Person("Floyd", 26)
  )
}

// ---------------------------------------------------------------------------------------------------------------------

class DBActor extends Actor {
  def receive = {
    case FetchRecord(recordID) =>
      if (recordID >= 3 && recordID <= 5)
        Thread.sleep(3000)
      else
        sender ! DB.data.get(recordID)
  }
}

// ---------------------------------------------------------------------------------------------------------------------

object CircuitBreakerApp extends App {

  val system = ActorSystem("hello-Akka")

  implicit val ec = system.dispatcher
  implicit val timeout = Timeout(3 seconds)

  val breaker = new CircuitBreaker(
    scheduler = system.scheduler,
    maxFailures = 3,
    callTimeout = 1 seconds,
    resetTimeout = 2 seconds
  ).onOpen {
    println("===========State is open=============")
  }.onClose {
    println("==========State is closed============")
  }

  val db = system.actorOf(Props[DBActor], "DBActor")

  (1 to 20).foreach {
    recordId => {
      Thread.sleep(1000)
      val askFuture = breaker.withCircuitBreaker(db ? FetchRecord(recordId))
      askFuture.map(record => s"Record is: $record and RecordID $recordId")
        .recover {
          case fail => "Failed with: " + fail.toString
        }
        .foreach(x => println(x))
    }
  }

  terminate(system)

}