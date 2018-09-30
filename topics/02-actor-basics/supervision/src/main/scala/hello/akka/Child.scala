package hello.akka

import akka.actor.{Actor, Props}
import hello.akka.Child.{RestartException, ResumeException, StopException}

class Child extends Actor {

  override def preStart(): Unit = {
    println("Child: preStart")
    super.preStart()
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    println("Child: preRestart")
    super.preRestart(reason, message)
  }

  override def postRestart(reason: Throwable): Unit = {
    println("Child: postRestart")
    super.postRestart(reason)
  }

  override def postStop(): Unit = {
    println("Child: postStop")
    super.postStop()
  }

  override def receive: Receive = {
    case "Resume" =>
      println("Child: throwing ResumeException")
      throw ResumeException
    case "Stop" =>
      println("Child: throwing StopException")
      throw StopException
    case "Restart" =>
      println("Child: throwing RestartException")
      throw RestartException
    case _ =>
      throw new Exception
  }
}

object Child {

  def props: Props = Props[Child]

  case object ResumeException extends Exception

  case object StopException extends Exception

  case object RestartException extends Exception

}
