package com.packt.chapter05.sec09

import akka.actor.{Actor, ActorSystem, Props}
import com.packt.utils.terminate
import com.typesafe.config.{Config, ConfigFactory}

// ---------------------------------------------------------------------------------------------------------------------

class MyActor extends Actor {
  def receive = {
    case msg: String => println(msg)
  }
}

// ---------------------------------------------------------------------------------------------------------------------

object ActorWithConfig extends App {
  val config: Config = ConfigFactory.load("akka.conf")
  val actorSystem = ActorSystem(config.getString("myactor.actorsystem"))
  val actorName = config.getString("myactor.actorname")
  val actor = actorSystem.actorOf(Props[MyActor], actorName)
  println(actor.path)

  terminate(actorSystem)

}