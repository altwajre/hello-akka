package com.packt.chapter7.sec09

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.pattern.ask
import akka.util.Timeout
import com.packt.chapter7.sec09.TemperatureActor._

import scala.concurrent.duration._

object ClusterShardingApplication extends App {

  val actorSystem = ActorSystem("ClusterSystem")

  import actorSystem.dispatcher

  val temperatureActor: ActorRef = ClusterSharding(actorSystem).
    start(
      typeName = TemperatureActor.shardName,
      entityProps = Props[TemperatureActor],
      settings = ClusterShardingSettings(actorSystem),
      extractEntityId = TemperatureActor.extractEntityId,
      extractShardId = TemperatureActor.extractShardId
    )

  //Let's simulate some time has passed. Never use Thread.sleep in production!
  Thread.sleep(20000)


  val locations = Vector(
    Location("USA", "Chicago"),
    Location("ESP", "Madrid"),
    Location("FIN", "Helsinki")
  )

  temperatureActor ! UpdateTemperature(locations(0), 1.0)
  temperatureActor ! UpdateTemperature(locations(1), 20.0)
  temperatureActor ! UpdateTemperature(locations(2), -10.0)

  Thread.sleep(3000)

  implicit val timeout = Timeout(5 seconds)

  for (location <- locations) {
    (temperatureActor ? GetCurrentTemperature(location)).onSuccess {
      case x: Double =>
        println(s"Current temperature in $location is $x")
    }
  }

}
