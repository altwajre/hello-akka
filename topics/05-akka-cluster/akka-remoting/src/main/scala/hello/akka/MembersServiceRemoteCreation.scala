package hello.akka

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory
import hello.akka.Worker.Work

object MembersServiceRemoteCreation extends App {

  val config = ConfigFactory.load.getConfig("MembersServiceRemoteCreation")

  val system = ActorSystem("MembersServiceRemoteCreation", config)

  val worker = system.actorOf(Props[Worker], "workerActorRemote")

  println(s"The remote path of worker actor is: ${worker.path}")

  worker ! Work("Hi remote actor!")

}
