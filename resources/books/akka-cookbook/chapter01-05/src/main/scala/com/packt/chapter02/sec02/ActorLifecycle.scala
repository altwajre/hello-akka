package com.packt.chapter02.sec02

import akka.actor.SupervisorStrategy.{Escalate, Restart}
import akka.actor.{Actor, ActorRef, ActorSystem, OneForOneStrategy, Props}
import akka.util.Timeout

import scala.concurrent.Await
import scala.concurrent.duration._
import akka.pattern.ask
import com.packt.utils.terminate

// ---------------------------------------------------------------------------------------------------------------------

case object Error

case class StopActor(actorRef: ActorRef)

// ---------------------------------------------------------------------------------------------------------------------

class LifecycleActor extends Actor {

  var state: Int = {
    println("initializing state")
    0
  }

  def updateState(description: String) = {
    state += 1
    println(s"updating state to $state: $description")
    state
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    updateState("preRestart")
  }

  override def preStart(): Unit = {
    updateState("preStart")
  }

  override def receive = {
    case Error =>
      updateState("receive Error")
      throw new ArithmeticException(s"got ERROR")
    case msg =>
      updateState("receive msg")
  }

  override def postStop(): Unit = {
    updateState("postStop")
  }

  override def postRestart(reason: Throwable): Unit = {
    updateState("postRestart")
  }
}

// ---------------------------------------------------------------------------------------------------------------------

class Supervisor extends Actor {

  override def supervisorStrategy = OneForOneStrategy(
    maxNrOfRetries = 10,
    withinTimeRange = 1 minute
  ) {

    case _: ArithmeticException =>
      Restart

    case t =>
      // combine your own strategy with the default strategy
      super.supervisorStrategy.decider.applyOrElse(t, (_: Any) => Escalate)

  }

  override def receive = {

    case (props: Props, name: String) =>
      sender ! context.actorOf(props, name)

    case StopActor(actorRef) =>
      context.stop(actorRef)

  }
}

// ---------------------------------------------------------------------------------------------------------------------

object ActorLifecycle extends App {

  implicit val timeout = Timeout(2 seconds)

  val actorSystem = ActorSystem("Supervision")

  val supervisor = actorSystem.actorOf(Props[Supervisor], "supervisor")

  val childFuture = supervisor ? (Props(new LifecycleActor), "LifecycleActor")

  val child = Await.result(childFuture.mapTo[ActorRef], 2 seconds)

  child ! "hello!"

  child ! Error

  Thread.sleep(1000)

  supervisor ! StopActor(child)

  terminate(actorSystem)

}



























