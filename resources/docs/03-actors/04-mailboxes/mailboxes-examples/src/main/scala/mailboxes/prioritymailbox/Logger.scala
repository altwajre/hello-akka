package mailboxes.prioritymailbox

import akka.actor.{Actor, ActorSystem, PoisonPill, Props}
import akka.event.{Logging, LoggingAdapter}

// We create a new Actor that just prints out what it processes
class Logger extends Actor {
  val log: LoggingAdapter = Logging(context.system, this)

  self ! 'lowpriority
  self ! 'lowpriority
  self ! 'highpriority
  self ! 'pigdog
  self ! 'pigdog2
  self ! 'pigdog3
  self ! 'highpriority
  self ! PoisonPill

  def receive = {
    case x ⇒ log.info(x.toString)
  }
}


