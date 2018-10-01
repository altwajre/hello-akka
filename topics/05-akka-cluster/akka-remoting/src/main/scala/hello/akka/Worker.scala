package hello.akka

import akka.actor.Actor
import hello.akka.Worker.Work

class Worker extends Actor {
  override def receive: Receive = {
    case msg: Work =>
      println(s"Worker: received work. My actorRef is $self")
  }
}

object Worker {
  case class Work(message: String)
}
