package com.packt.chapter6.sec05

import akka.actor.ActorSystem

object PersistentFSMApp extends App {

  val system = ActorSystem("test")

  val actor1 = createActor("uid1")
  actor1 ! Initialize(4)
  actor1 ! Mark
  actor1 ! Mark

  Thread.sleep(1000)
  system.stop(actor1)
  Thread.sleep(1000)
  println("---------------------------------------------")

  val actor2 = createActor("uid1")
  actor2 ! Mark
  actor2 ! Mark

  Thread.sleep(2000)
  system.terminate()

  def createActor(id: String) = system.actorOf(PersistentFSMActor.props(id))

}
