package hello.akka

import akka.actor.{ActorSystem, Props}
import hello.akka.Recorder.NewUser

object Main extends App {

  val system = ActorSystem("ask-tell")

  val checker = system.actorOf(Props[Checker], "checker")

  val storage = system.actorOf(Props[Storage], "storage")

  val recorder = system.actorOf(Recorder.props(checker, storage), "recorder")

  recorder ! NewUser(User("Jon", "jon@mail.com"))
  Thread.sleep(1000)

  recorder ! NewUser(User("Adam", "adam@mail.com"))
  Thread.sleep(1000)

  system.terminate()

}
