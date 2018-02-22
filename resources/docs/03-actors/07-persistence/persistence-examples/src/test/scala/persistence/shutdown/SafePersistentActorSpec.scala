package persistence.shutdown

import akka.actor.{ActorSystem, PoisonPill, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

class SafePersistentActorSpec(_system: ActorSystem) extends TestKit(_system)
  with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("SafePersistentActorSpec"))

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "Shutting down a persistent actor with PoisonPill" must {
    "be considered UNSAFE" in {
      val persistentActor = system.actorOf(Props[SafePersistentActor], "SafePersistentActor")

      // UN-SAFE, due to PersistentActor's command stashing:
      persistentActor ! "a"
      persistentActor ! "b"
      persistentActor ! PoisonPill

      // order of received messages:
      // a
      //   # b arrives at mailbox, stashing;        internal-stash = [b]
      // PoisonPill is an AutoReceivedMessage, is handled automatically
      // !! stop !!
      // Actor is stopped without handling `b` nor the `a` handler!

      expectNoMessage(1.seconds)

    }

  }

  "Shutting down a persistent actor with explicit message" must {
    "be considered SAFE" in {
      val persistentActor = system.actorOf(Props[SafePersistentActor], "SafePersistentActor")

      // UN-SAFE, due to PersistentActor's command stashing:
      persistentActor ! "a"
      persistentActor ! "b"
      persistentActor ! Shutdown

      // order of received messages:
      // a
      //   # b arrives at mailbox, stashing;        internal-stash = [b]
      // PoisonPill is an AutoReceivedMessage, is handled automatically
      // !! stop !!
      // Actor is stopped without handling `b` nor the `a` handler!

      expectMsg("handle-a")
      expectMsg("handle-b")

      expectNoMessage(1.second)
    }

  }

}