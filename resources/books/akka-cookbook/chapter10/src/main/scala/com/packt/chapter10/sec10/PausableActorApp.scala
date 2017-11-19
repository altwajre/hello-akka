package com.packt.chapter10.sec10

import akka.actor.{ActorSystem, Props}

object PausableActorApp extends App {

  import PausableActor._

  val actorSystem = ActorSystem()
  val hardWorker = actorSystem.actorOf(Props[HardWorker], "hardWorker")
  val pausableHardWorker = actorSystem.actorOf(Props(classOf[PausableActor], hardWorker), "pausableActor")

  (1 to 100).foreach { i =>
    pausableHardWorker ! Work(i)
  }

}
