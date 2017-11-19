package com.packt.chapter01.sec07

import akka.actor.{Actor, ActorSystem, Props}
import akka.dispatch.ControlMessage
import com.packt.utils.terminate

// ---------------------------------------------------------------------------------------------------------------------

case object MyControlMessage extends ControlMessage

// ---------------------------------------------------------------------------------------------------------------------

class Logger extends Actor {
  override def receive = {

    case MyControlMessage =>
      println("Oh, I have to process control message first")

    case x =>
      println(x.toString)

  }
}

// ---------------------------------------------------------------------------------------------------------------------

object ControlAwareMailbox extends App {

  val actorSystem = ActorSystem("HelloAkka")

  val actor = actorSystem.actorOf(Props[Logger].withDispatcher("control-aware-dispatcher"))

  actor ! "hello"
  actor ! "how are"
  actor ! "you?"
  actor ! MyControlMessage

  terminate(actorSystem)

}
