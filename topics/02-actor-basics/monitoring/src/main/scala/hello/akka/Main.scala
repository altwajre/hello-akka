package hello.akka

import akka.actor.{ActorSystem, Props}

object Main extends App {

  val system = ActorSystem("monitor")

  val monitoree = system.actorOf(Props[Monitoree], "monitoree")
  val monitor = system.actorOf(Monitor.props(monitoree), "monitor")

  monitoree ! "Hi"

  Thread.sleep(1000)
  system.terminate()

}
