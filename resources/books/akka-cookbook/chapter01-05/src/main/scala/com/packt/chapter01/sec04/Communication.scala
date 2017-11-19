package com.packt.chapter01.sec04

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import com.packt.utils.terminate

import scala.util.Random._

// ---------------------------------------------------------------------------------------------------------------------

object Messages {

  case class Start(actorRef: ActorRef)

  case object GiveMeRandomNumber

  case class Done(randomNumber: Int)

}

// ---------------------------------------------------------------------------------------------------------------------

class RandomNumberGeneratorActor extends Actor {

  import Messages._

  override def receive = {

    case GiveMeRandomNumber =>
      println("received a message to generate a random integer")
      val randomNumber = nextInt
      sender ! Done(randomNumber)

  }
}

// ---------------------------------------------------------------------------------------------------------------------

class QueryActor extends Actor {

  import Messages._

  override def receive = {

    case Start(actorRef) =>
      println("send me the next random number")
      actorRef ! GiveMeRandomNumber

    case Done(randomNumber) =>
      println(s"received a random number $randomNumber")

  }
}

// ---------------------------------------------------------------------------------------------------------------------

object Communication extends App {

  import Messages._

  val actorSystem = ActorSystem("HelloAkka")

  val randomNumberGenerator = actorSystem.actorOf(Props[RandomNumberGeneratorActor], "randomNumberGeneratorActor")

  val queryActor = actorSystem.actorOf(Props[QueryActor], "queryActor")

  queryActor ! Start(randomNumberGenerator)

  terminate(actorSystem)

}
