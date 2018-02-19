package mailboxes.prioritymailbox

import akka.actor.{ActorSystem, Props}

object LoggerApp1 extends App {

  val system = ActorSystem("LoggerApp1")

  val a = system.actorOf(Props(classOf[Logger]).withDispatcher("prio-mailbox"))

  /*
   * Logs:
   * 'highpriority
   * 'highpriority
   * 'pigdog
   * 'pigdog2
   * 'pigdog3
   * 'lowpriority
   * 'lowpriority
   */

}