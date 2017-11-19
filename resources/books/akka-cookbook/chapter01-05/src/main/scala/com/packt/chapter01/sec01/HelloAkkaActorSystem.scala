package com.packt.chapter01.sec01

import akka.actor.ActorSystem
import com.packt.utils.terminate

// ---------------------------------------------------------------------------------------------------------------------

object HelloAkkaActorSystem extends App {

  val actorSystem = ActorSystem("HelloAkka")
  println(actorSystem)

  terminate(actorSystem)

}
