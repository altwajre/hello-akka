package com.packt.chapter9.sec08

import akka.actor.Actor

class UnresponsiveActor extends Actor {
  def receive = Actor.ignoringBehavior
}