package hello.akka.router.programmatic1.pool

import akka.actor.{Actor, ActorRef}
import hello.akka.router.Worker
import hello.akka.router.Worker.Work

import scala.util.Random

class Router extends Actor {

  var routees: List[ActorRef] = _

  override def preStart(): Unit = {
    routees = List.range(1, 5).map { id =>
      context.actorOf(Worker.props, s"worker-$id")
    }
  }

  override def receive: Receive = {
    case work: Work =>
      println("Router: received work, forwarding to random worker...")
      routees(Random.nextInt(routees.size)) forward work
  }
}
