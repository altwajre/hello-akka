package mailboxes.controlawaremailbox

import akka.actor.{ActorSystem, Props}

object LoggerApp1 extends App {

  val system = ActorSystem("LoggerApp1")

  val a = system.actorOf(Props(classOf[Logger]).withDispatcher("control-aware-mailbox"))

  /*
   * Logs:
   * MyControlMessage
   * 'foo
   * 'bar
   */

}