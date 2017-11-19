package com.packt.chapter8.sec08

import akka.actor.Actor

class SinkActor extends Actor {

  import SinkActor._

  def receive = {
    case InitSinkActor =>
      println("SinkActor initialized")
      sender ! AckSinkActor
    case something =>
      println(s"Received [$something] in SinkActor")
      sender ! AckSinkActor
  }
}

object SinkActor {

  case object CompletedSinkActor

  case object AckSinkActor

  case object InitSinkActor

}
