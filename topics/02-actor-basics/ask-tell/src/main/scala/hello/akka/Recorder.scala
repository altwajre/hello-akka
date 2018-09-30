package hello.akka

import akka.actor.{Actor, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import hello.akka.Checker.{BlackUser, CheckUser, WhiteUser}
import hello.akka.Recorder.NewUser
import hello.akka.Storage.AddUser

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class Recorder(checker: ActorRef, storage: ActorRef) extends Actor {

  implicit val timeout = Timeout(5.seconds)

  override def receive: Receive = {
    case NewUser(user) =>
      checker ? CheckUser(user) map {
        case WhiteUser(_) =>
          storage ! AddUser(user)
        case BlackUser(_) =>
          println(s"Recorder: $user in blacklist, ignoring...")
      }
  }
}

object Recorder {

  def props(checker: ActorRef, storage: ActorRef) =
    Props(new Recorder(checker, storage))

  sealed trait RecorderMsg

  case class NewUser(user: User) extends RecorderMsg

}
