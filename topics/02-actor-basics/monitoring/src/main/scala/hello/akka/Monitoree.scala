package hello.akka

import akka.actor.Actor

class Monitoree extends Actor{

  override def receive: Receive = {
    case msg =>
      println(s"Monitoree: received $msg")
      context.stop(self)

  }
}

