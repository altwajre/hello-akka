package com.packt.chapter03.sec06

import akka.actor.{Actor, ActorSystem, Props}
import akka.pattern.ask
import akka.routing.TailChoppingPool
import akka.util.Timeout
import com.packt.utils.terminate

import scala.concurrent.Await
import scala.concurrent.duration._

// ---------------------------------------------------------------------------------------------------------------------

class TailChoppingActor extends Actor {

  override def receive = {
    case msg: String =>
      println(s"got $msg from ${self.path}")
      if (self.path.name == "$e")
        sender ! s"I say hello back to you from ${self.path}"
    case _ =>
  }
}

// ---------------------------------------------------------------------------------------------------------------------

object ScatterGatherFirstCompletedPoolApp extends App {
  implicit val timeout = Timeout(10 seconds)
  val actorSystem = ActorSystem("Hello-Akka")
  val router = actorSystem.actorOf(
    TailChoppingPool(
      nrOfInstances = 5,
      within = 10.seconds,
      interval = 20.millis
    ).props(Props[TailChoppingActor])
  )
  println(Await.result((router ? "hello").mapTo[String], 10 seconds))

  terminate(actorSystem)

}


