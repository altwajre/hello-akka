package com.packt.chapter10.sec11

import akka.actor.{Actor, ActorLogging}

class EnvelopeReceiver extends Actor with ActorLogging {
  def receive = {
    case x => log.info(s"Received [$x]")
  }
}
