package persistence.persistasync

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

class NestedPersistAsyncActorSpec(_system: ActorSystem) extends TestKit(_system)
  with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("NestedPersistAsyncActorSpec"))

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "Nested persistAsync" must {
    "work" in {
      val persistentActor = system.actorOf(Props[NestedPersistAsyncActor], "NestedPersistAsyncActor")

      persistentActor ! "a"
      persistentActor ! "b"

      // a -> a-outer-1 -> a-outer-2 -> a-inner-1 -> a-inner-2
      // b -> b-outer-1 -> b-outer-2 -> b-inner-1 -> b-inner-2

      // Expect guaranteed order of received messages:
      expectMsg("a")
      expectMsg("b")
      expectMsg("a-outer-1")
      expectMsg("a-outer-2")
      expectMsg("b-outer-1")
      expectMsg("b-outer-2")
      expectMsg("a-inner-1")
      expectMsg("a-inner-2")
      expectMsg("b-inner-1")
      expectMsg("b-inner-2")

      expectNoMessage(1.second)

    }
  }
}