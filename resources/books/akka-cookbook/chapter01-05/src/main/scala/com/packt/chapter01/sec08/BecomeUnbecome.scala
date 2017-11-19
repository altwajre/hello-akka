package com.packt.chapter01.sec08

import akka.actor.{Actor, ActorSystem, Props}
import com.packt.utils.terminate

// ---------------------------------------------------------------------------------------------------------------------

class BecomeUnbecomeActor extends Actor {

  override def receive = initialState

  def initialState: Receive = {
    case true =>
      context.become(trueState)
    case false =>
      context.become(falseState)
    case _ =>
      println("don't understand, must send true or false to put into valid state")
  }

  def trueState: Receive = {
    case msg: String =>
      println(s"In true state: $msg")
    case false =>
      context.become(falseState)
  }

  def falseState: Receive = {
    case msg: Int =>
      println(s"In false state: $msg")
    case true =>
      context.become(trueState)
  }

}

// ---------------------------------------------------------------------------------------------------------------------

object BecomeUnbecomeApp extends App {

  val actorSystem = ActorSystem("HelloAkka")

  val becomeUnbecome = actorSystem.actorOf(Props[BecomeUnbecomeActor])

  becomeUnbecome ! "Initial message"
  becomeUnbecome ! true
  becomeUnbecome ! "Hello how are you?"
  becomeUnbecome ! false
  becomeUnbecome ! 1100
  becomeUnbecome ! true
  becomeUnbecome ! "What do you do?"

  terminate(actorSystem)

}