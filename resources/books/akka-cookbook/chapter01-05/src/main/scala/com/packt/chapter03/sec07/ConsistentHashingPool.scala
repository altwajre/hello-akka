package com.packt.chapter03.sec07

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.routing.ConsistentHashingPool
import akka.routing.ConsistentHashingRouter.{ConsistentHashMapping, ConsistentHashable, ConsistentHashableEnvelope}
import com.packt.utils.terminate

// ---------------------------------------------------------------------------------------------------------------------

case class Entry(key: String, value: String)

// option 1 - message can extend ConsistentHashable
case class Get(key: String) extends ConsistentHashable {
  override def consistentHashKey: Any = key
}

case class Evict(key: String)

// ---------------------------------------------------------------------------------------------------------------------

class Cache extends Actor {

  var cache = Map.empty[String, String]

  def receive = {

    case Entry(key, value) =>
      println(s" ${self.path.name} adding key $key")
      cache += (key -> value)

    case Get(key) =>
      println(s" ${self.path.name} fetching key $key")
      sender() ! cache.get(key)

    case Evict(key) =>
      println(s" ${self.path.name} removing key $key")
      cache -= key

  }
}

// ---------------------------------------------------------------------------------------------------------------------

object ConsistentHashingPoolApp extends App {

  val actorSystem = ActorSystem("Hello-Akka")

  // option 2 - extract key from incoming message
  def hashMapping: ConsistentHashMapping = {
    case Evict(key) => key
  }

  val cacheActor: ActorRef = actorSystem.actorOf(
    ConsistentHashingPool(
      nrOfInstances = 10,
      hashMapping = hashMapping
    ).props(Props[Cache]),
    name = "cache"
  )

  // option 3 - wrap message in envelope
  cacheActor ! ConsistentHashableEnvelope(message = Entry("hello", "HELLO"), hashKey = "hello")
  cacheActor ! ConsistentHashableEnvelope(message = Entry("hi", "HI"), hashKey = "hi")
  cacheActor ! Get("hello")
  cacheActor ! Get("hi")
  cacheActor ! Evict("hi")

  terminate(actorSystem)

}