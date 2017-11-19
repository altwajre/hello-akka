package com.packt.chapter6.sec03

import akka.actor.{ActorSystem, PoisonPill, Props}

object SafePersistenceActorShutdownApp extends App {

  val system = ActorSystem("safe-shutdown")

  val persistentActor1 = system.actorOf(Props[SamplePersistenceActor])
  val persistentActor2 = system.actorOf(Props[SamplePersistenceActor])

  persistentActor1 ! UserUpdate("foo", Add)
  persistentActor1 ! UserUpdate("foo", Add)
  persistentActor1 ! PoisonPill

  Thread.sleep(1000)
  println("-----------------------------------------")

  persistentActor2 ! UserUpdate("foo", Add)
  persistentActor2 ! UserUpdate("foo", Add)
  persistentActor2 ! ShutdownPersistentActor

  Thread.sleep(2000)
  system.terminate()

}
