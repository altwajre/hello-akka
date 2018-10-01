package hello.akka

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import hello.akka.Worker.Work

object MembersServiceLookup extends App {

  val config = ConfigFactory.load.getConfig("MembersServiceLookup")

  val system = ActorSystem("MembersServiceLookup", config)

  val worker = system.actorSelection("akka.tcp://MembersService@127.0.0.1:2552/user/remote-worker")

  worker ! Work("Hi remote actor!")

}
