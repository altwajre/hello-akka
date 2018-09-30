package hello.akka.creation

import akka.actor.Actor
import hello.akka.creation.MusicController.Play
import hello.akka.creation.MusicPlayer.{StartMusic, StopMusic}


// Supervisor for MusicController
class MusicPlayer extends Actor {
  override def receive: Receive = {
    case StartMusic =>

      // NOTE: Never create actors in other actors like this. It's to easy to break encapsulation by passing 'this'.
      // val controller = context.actorOf(Props[MusicController], "controller")

      val controller = context.actorOf(MusicController.props, "controller")

      controller ! Play

    case StopMusic =>
      println("I don't want to stop music")

    case _ =>
      println("Unknown message")

  }
}

object MusicPlayer {

  sealed trait PlayMsg

  case object StopMusic extends PlayMsg

  case object StartMusic extends PlayMsg

}
