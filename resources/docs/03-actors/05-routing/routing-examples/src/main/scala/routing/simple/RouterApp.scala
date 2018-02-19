package routing.simple

import akka.actor.{ActorSystem, Props}
import routing.simple.Master.Work

object RouterApp extends App {

  val system = ActorSystem("LoggerApp1")

  val a = system.actorOf(Props(classOf[Master]))

  a ! Work("task 1")
  a ! Work("task 2")
  a ! Work("task 3")
  a ! Work("task 4")
  a ! Work("task 5")
  a ! Work("task 6")

}
