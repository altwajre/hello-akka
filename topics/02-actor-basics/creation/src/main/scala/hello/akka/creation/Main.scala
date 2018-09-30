package hello.akka.creation

import akka.actor.{ActorSystem, Props}
import hello.akka.creation.MusicPlayer.StartMusic

object Main extends App {

  val system = ActorSystem("creation")

  val player = system.actorOf(Props[MusicPlayer], "player")

  player ! StartMusic

  system.terminate()

}
