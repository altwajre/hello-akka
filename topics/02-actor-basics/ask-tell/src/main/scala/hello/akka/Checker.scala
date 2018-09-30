package hello.akka

import akka.actor.Actor
import hello.akka.Checker.{BlackUser, CheckUser, WhiteUser}

class Checker extends Actor {

  val blackList = List(
    User("Adam", "adam@mail.com")
  )

  override def receive: Receive = {
    case CheckUser(user) if blackList.contains(user) =>
      println(s"Checker: $user in the blacklist")
      sender() ! BlackUser(user)
    case CheckUser(user) =>
      println(s"Checker: $user not in the blacklist")
      sender() ! WhiteUser(user)

  }
}

object Checker {

  sealed trait CheckerMsg

  case class CheckUser(user: User) extends CheckerMsg

  sealed trait CheckerResponse

  case class BlackUser(user: User) extends CheckerResponse
  case class WhiteUser(user: User) extends CheckerResponse

}
