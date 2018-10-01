package hello.akka

import akka.actor.{Actor, ActorSystem, Props}
import com.typesafe.config.ConfigFactory

class Backend extends Actor {

  override def receive: Receive = {

    case Add(num1, num2) =>
      println(s"Backend: received add operation ($self)")

  }

}

object Backend {

  def initiate(port: Int): Unit = {
    val config = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port")
      .withFallback(
        ConfigFactory.parseString(s"akka.cluster.roles = [backend]")
      )
      .withFallback(
        ConfigFactory.load("loadbalancer")
      )

    val system = ActorSystem("ClusterSystem", config)

    val backend = system.actorOf(Props[Backend], "backend")
  }

}
