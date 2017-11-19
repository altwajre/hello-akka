package com.packt.chapter10.sec11

import java.util.UUID

import akka.actor.{ActorSystem, Props}
import com.packt.chapter10.sec11.Envelope.Headers

object EnvelopingActorApp extends App {

  val actorSystem = ActorSystem()
  val envelopReceived = actorSystem.actorOf(Props[EnvelopeReceiver], "receiver")
  val envelopingActor = actorSystem.actorOf(Props(classOf[EnvelopingActor], envelopReceived, headers _))

  envelopingActor ! "Hello!"

  def headers(msg: Any): Headers = {
    Map(
      "timestamp" -> System.currentTimeMillis(),
      "correlationId" -> UUID.randomUUID().toString
    )
  }

}
