package persistence.persistasync

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

class NestedPersistActorSpec(_system: ActorSystem) extends TestKit(_system)
  with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("NestedPersistActorSpec"))

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "Nested persist" must {
    "work" in {
      val persistentActor = system.actorOf(Props[NestedPersistActor], "NestedPersistActor")

      persistentActor ! "a"
      persistentActor ! "b"

      // Expect guaranteed order of received messages:
      expectMsg("a")
      expectMsg("a-1-outer")
      expectMsg("a-2-outer")
      expectMsg("a-1-inner")
      expectMsg("a-2-inner")
      expectMsg("b")
      expectMsg("b-1-outer")
      expectMsg("b-2-outer")
      expectMsg("b-1-inner")
      expectMsg("b-2-inner")

      expectNoMessage(1.second)

    }
  }
}