package fsm.simple

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import fsm.simple.Buncher._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.collection.immutable

class FSMDocSpec(_system: ActorSystem) extends TestKit(_system)
  with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("FSMDocSpec"))

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "simple finite state machine" must {

    "batch correctly" in {
      val buncher = system.actorOf(Props(classOf[Buncher]))
      buncher ! SetTarget(testActor)
      buncher ! Queue(42)
      buncher ! Queue(43)
      expectMsg(Batch(immutable.Seq(42, 43)))
      buncher ! Queue(44)
      buncher ! Flush
      buncher ! Queue(45)
      expectMsg(Batch(immutable.Seq(44)))
      expectMsg(Batch(immutable.Seq(45)))
    }

    "not batch if uninitialized" in {
      val buncher = system.actorOf(Props(classOf[Buncher]))
      buncher ! Queue(42)
      expectNoMsg
    }
  }

}