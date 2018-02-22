package persistence.atleastonce

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

class AtLeastOnceSpec(_system: ActorSystem) extends TestKit(_system)
  with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("AtLeastOnceSpec"))

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "At-least-once" must {
    "work" in {

      system.actorOf(Props[MyDestination], "my-destination")

      val persistentActor = system.actorOf(Props(classOf[MyPersistentActor], "/user/my-destination"), "AtLeastOnce")

      persistentActor ! "a"

      // See console output

      expectNoMessage(1.second)

    }
  }
}