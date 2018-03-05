package com.packt.akka

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.packt.akka.Checker._
import com.packt.akka.Recorder._
import com.packt.akka.Storage._

import scala.concurrent.duration._
import scala.language.postfixOps

// ---------------------------------------------------------------------------------------------------------------------

case class User(username: String, email: String)

// ---------------------------------------------------------------------------------------------------------------------

class Checker extends Actor {

  val blackList = List(
    User("Adam", "adam@mail.com")
  )

  def receive = {
    case CheckUser(user) if blackList.contains(user) =>
      println(s"Checker: $user in the blacklist")
      sender() ! BlackUser(user)
    case CheckUser(user) =>
      println(s"Checker: $user not in the blacklist")
      sender() ! WhiteUser(user)
  }
}

object Checker {

  // Checker Messages
  sealed trait CheckerMsg
  case class CheckUser(user: User) extends CheckerMsg

  //Checker Responses
  sealed trait CheckerResponse
  case class BlackUser(user: User) extends CheckerResponse
  case class WhiteUser(user: User) extends CheckerResponse

}

// ---------------------------------------------------------------------------------------------------------------------

class Storage extends Actor {

  var users = List.empty[User]

  def receive = {
    case AddUser(user) =>
      println(s"Storage: $user added")
      users = user :: users
  }
}

object Storage {

  sealed trait StorageMsg

  // Storage Messages
  case class AddUser(user: User) extends StorageMsg

}

// ---------------------------------------------------------------------------------------------------------------------

class Recorder(checker: ActorRef, storage: ActorRef) extends Actor {

  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val timeout = Timeout(5 seconds)

  def receive = {
    case NewUser(user) =>
      checker ? CheckUser(user) map {
        case WhiteUser(user) =>
          storage ! AddUser(user)
        case BlackUser(user) =>
          println(s"Recorder: $user in the blacklist")
      }
  }

}

object Recorder {

  sealed trait RecorderMsg

  // Recorder Messages
  case class NewUser(user: User) extends RecorderMsg

  def props(checker: ActorRef, storage: ActorRef) =
    Props(new Recorder(checker, storage))

}

// ---------------------------------------------------------------------------------------------------------------------

object TalkToActor extends App {

  // Create the 'talk-to-actor' actor system
  val system = ActorSystem("talk-to-actor")

  // Create the 'checker' actor
  val checker = system.actorOf(Props[Checker], "checker")

  // Create the 'storage' actor
  val storage = system.actorOf(Props[Storage], "storage")

  // Create the 'recorder' actor
  val recorder = system.actorOf(Recorder.props(checker, storage), "recorder")

  // send NewUser Message to Recorder
  recorder ! Recorder.NewUser(User("Jon", "jon@packt.com"))

  Thread.sleep(100)

  // shutdown system
  system.terminate()

}
