package com.packt.chapter03.sec05

import akka.actor.{Actor, ActorSystem, Props}
import akka.pattern.ask
import akka.routing.ScatterGatherFirstCompletedPool
import akka.util.Timeout
import com.packt.utils.terminate

import scala.concurrent.Await
import scala.concurrent.duration._

// ---------------------------------------------------------------------------------------------------------------------

class ScatterGatherFirstCompletedPoolActor extends Actor {
  override def receive = {
    case msg: String =>
      println(s"got $msg on ${self.path}")
      sender ! s"I say hello back to you from ${self.path}"
    case _ =>
  }
}

// ---------------------------------------------------------------------------------------------------------------------

object ScatterGatherFirstCompletedPoolApp extends App {
  implicit val timeout = Timeout(10 seconds)
  val actorSystem = ActorSystem("Hello-Akka")
  val router = actorSystem.actorOf(
    ScatterGatherFirstCompletedPool(5, within = 10.seconds)
      .props(Props[ScatterGatherFirstCompletedPoolActor])
  )
  println(Await.result((router ? "hello").mapTo[String], 10 seconds))

  terminate(actorSystem)

}
