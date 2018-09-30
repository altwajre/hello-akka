package hello.akka

import akka.actor.{ActorSystem, Props}

object Main extends App {

  val system = ActorSystem("supervision")

  val parent = system.actorOf(Props[Parent], "parent")

//  parent ! "Resume"
//  Thread.sleep(1000)
//  println()

  parent ! "Restart"
  Thread.sleep(1000)
  println()

//  parent ! "Stop"
//  Thread.sleep(1000)
//  println()

  system.terminate()

}
