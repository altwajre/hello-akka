package mailboxes.controlawaremailbox

import akka.actor.{Actor, PoisonPill}
import akka.event.{Logging, LoggingAdapter}

// We create a new Actor that just prints out what it processes
class Logger extends Actor {
  val log: LoggingAdapter = Logging(context.system, this)

  self ! 'foo
  self ! 'bar
  self ! MyControlMessage
  self ! PoisonPill

  def receive = {
    case x ⇒ log.info(x.toString)
  }
}

