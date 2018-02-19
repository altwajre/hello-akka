package mailboxes.prioritymailbox

import akka.actor.{ActorSystem, Props}

object LoggerApp2 extends App {

  val system = ActorSystem("LoggerApp2")

  val a = system.actorOf(Props[Logger], "priomailboxactor")

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