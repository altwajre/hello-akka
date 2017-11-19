package com.packt.utils

import akka.actor.ActorSystem

object terminate {

  def apply(actorSystem: ActorSystem, delay: Int = 2000) = {
    Thread.sleep(delay)
    actorSystem.terminate()
  }

}
