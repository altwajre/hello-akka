package hello.akka.router

import akka.actor.{Actor, Props}
import hello.akka.router.Worker.Work

class Worker extends Actor {
  override def receive: Receive = {
    case msg: Work =>
      println(s"Worker: received work ($self)")
  }
}

object Worker {

  def props: Props = Props[Worker]

  case class Work()
}
