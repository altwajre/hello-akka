package hello.akka

import akka.actor.{Actor, ActorSystem, Props, RootActorPath}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.MemberUp
import com.typesafe.config.ConfigFactory

class Backend extends Actor {

  val cluster = Cluster(context.system)

  override def preStart(): Unit = {
    cluster.subscribe(self, classOf[MemberUp])
  }

  override def postStop(): Unit = {
    cluster.unsubscribe(self)
  }

  override def receive: Receive = {

    case Add(num1, num2) =>
      println(s"Backend: received add operation ($self)")

    case MemberUp(member) =>
      if (member.hasRole("frontend")) {
        val frontend = context.actorSelection(
          RootActorPath(member.address) / "user" / "frontend"
        )
        frontend ! BackendRegistration
      }
  }

}

object Backend {

  def initiate(port: Int) = {
    val config = ConfigFactory
      .parseString(s"akka.remote.netty.tcp.port=$port")
      .withFallback(ConfigFactory.load.getConfig("Backend"))

    val system = ActorSystem("ClusterSystem", config)

    val backend = system.actorOf(Props[Backend], "Backend")
  }

}
