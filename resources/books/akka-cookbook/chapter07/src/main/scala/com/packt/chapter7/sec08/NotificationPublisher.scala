package com.packt.chapter7.sec08

import akka.actor.Actor
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish

class NotificationPublisher extends Actor {

  val mediator = DistributedPubSub(context.system).mediator

  def receive = {
    case notification: Notification =>
      mediator ! Publish("notification", notification)
  }
}
