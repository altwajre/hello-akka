package hello.akka.creation

import akka.actor.{Actor, Props}
import hello.akka.creation.MusicController.{Play, Stop}

class MusicController extends Actor {
  override def receive: Receive = {

    case Play =>
      println("Music started")

    case Stop =>
      println("Music stopped")

  }
}

object MusicController {

  def props: Props = Props[MusicController]

  sealed trait ControllerMsg

  case object Play extends ControllerMsg

  case object Stop extends ControllerMsg

}
