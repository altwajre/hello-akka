package hello.akka.router.programmatic1.group

import akka.actor.{Actor, Props}
import hello.akka.router.Worker.Work

import scala.util.Random

class Router(routees: List[String]) extends Actor {

  override def receive: Receive = {
    case msg: Work =>
      println("Router: received work, forwarding to random selected worker...")
      val routee = routees(Random.nextInt(routees.size))
      context.actorSelection(routee) forward msg
  }

}

object Router {

  def props(routees: List[String]) = Props(new Router(routees))

}
