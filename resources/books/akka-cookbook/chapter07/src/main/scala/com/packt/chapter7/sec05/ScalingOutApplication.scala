package com.packt.chapter7.sec05

import java.util.UUID

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.routing.RoundRobinPool

import scala.concurrent.duration._

object ScalingOutMaster extends App {

  val actorSystem = ActorSystem("MasterActorSystem")
  val masterActor = actorSystem.actorOf(Props[MasterActor], "masterActor")

  (1 to 1000).foreach(i => {
    masterActor ! Work(s"$i")
    Thread.sleep(1000) // Simulates sending work to the master actor every 5 seconds
  })

}

object ScalingOutWorker extends App {

  val actorSystem = ActorSystem("WorkerActorSystem")
  implicit val dispatcher = actorSystem.dispatcher

  val masterSelection = actorSystem.actorSelection("akka.tcp://MasterActorSystem@127.0.0.1:2552/user/masterActor")
  masterSelection.resolveOne(3 seconds).onSuccess {
    case masterActor: ActorRef =>
      println("We got the ActorRef for the master actor")
      val pool = RoundRobinPool(10)
      val workerPool = actorSystem.actorOf(Props[WorkerActor].withRouter(pool), s"workerActor-${UUID.randomUUID().toString}")
      masterActor ! RegisterWorker(workerPool)
  }

}
