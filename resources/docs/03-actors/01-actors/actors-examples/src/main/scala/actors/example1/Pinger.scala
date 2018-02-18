package actors.example1

import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props}

import scala.concurrent.duration._
import scala.language.postfixOps

case object Ping

case object Pong

class Pinger extends Actor {
  var countDown = 100

  def receive = {
    case Pong ⇒
      println(s"${self.path} received pong, count down $countDown")

      if (countDown > 0) {
        countDown -= 1
        sender() ! Ping
      } else {
        sender() ! PoisonPill
        self ! PoisonPill
      }
  }
}

class Ponger(pinger: ActorRef) extends Actor {
  def receive = {
    case Ping ⇒
      println(s"${self.path} received ping")
      pinger ! Pong
  }
}

object PingerApp extends App {
  val system = ActorSystem("pingpong")

  val pinger = system.actorOf(Props[Pinger], "pinger")

  val ponger = system.actorOf(Props(classOf[Ponger], pinger), "ponger")

  import system.dispatcher

  system.scheduler.scheduleOnce(500 millis) {
    ponger ! Ping
  }
}