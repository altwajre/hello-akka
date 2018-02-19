package mailboxes.myownmailbox

import akka.actor.{ActorSystem, Props}

object MyUnboundedMailboxApp extends App {

  val system = ActorSystem("LoggerApp1")

  val a = system.actorOf(Props(classOf[Logger]).withDispatcher("custom-dispatcher-mailbox"))

}
