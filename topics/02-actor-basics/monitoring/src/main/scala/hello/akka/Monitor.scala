package hello.akka

import akka.actor.{Actor, ActorRef, Props, Terminated}

class Monitor(monitoree: ActorRef) extends Actor {

  override def preStart(): Unit = {
    println("Monitor: preStart")
    context.watch(monitoree)
  }

  override def postStop(): Unit = {
    println("Monitor: postStop")
  }

  override def receive: Receive = {
    case terminated @ Terminated(_) =>
      println(s"Monitor: received $terminated")
      context.stop(self)
  }

}

object Monitor {

  def props(monitoree: ActorRef) = Props(new Monitor(monitoree))

}
