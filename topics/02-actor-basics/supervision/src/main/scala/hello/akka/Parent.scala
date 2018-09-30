package hello.akka

import akka.actor.SupervisorStrategy.{Escalate, Restart, Resume, Stop}
import akka.actor.{Actor, ActorRef, OneForOneStrategy, SupervisorStrategy}
import hello.akka.Child.{RestartException, ResumeException, StopException}

import scala.concurrent.duration._

class Parent extends Actor {

  var childRef: ActorRef = _

  override val supervisorStrategy: SupervisorStrategy = {
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1.second) {
      case ResumeException => Resume
      case RestartException => Restart
      case StopException => Stop
      case _ => Escalate
    }
  }

  override def preStart(): Unit = {
    childRef = context.actorOf(Child.props, "child")
    Thread.sleep(100) // FIXME: Why?
  }

  override def receive: Receive = {
    case msg =>
      println(s"Parent: received $msg, telling child...")
      childRef ! msg
      Thread.sleep(100) // FIXME: Why?
  }

}
