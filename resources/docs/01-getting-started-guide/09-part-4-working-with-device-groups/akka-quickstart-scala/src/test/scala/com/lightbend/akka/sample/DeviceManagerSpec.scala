package com.lightbend.akka.sample

import akka.actor.{ActorSystem, PoisonPill}
import akka.testkit.{TestKit, TestProbe}
import com.lightbend.akka.sample.DeviceManager.{DeviceRegistered, RequestTrackDevice, props}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

class DeviceManagerSpec(_system: ActorSystem)
  extends TestKit(_system)
    with Matchers
    with FlatSpecLike
    with BeforeAndAfterAll {

  def this() = this(ActorSystem("DeviceManagerSpec"))

  override def afterAll: Unit = {
    shutdown(system)
  }

  "A Device Manager Actor" should "be able to create a group actor if it doesn't exist" in {
    val probe = TestProbe()
    val managerActor = system.actorOf(props())

    managerActor.tell(RequestTrackDevice("group", "device1"), probe.ref)
    probe.expectMsg(DeviceRegistered)

  }

  it should "handle termination of device groups" in {
    val probe = TestProbe()
    val managerActor = system.actorOf(props())

    managerActor.tell(RequestTrackDevice("group", "device1"), probe.ref)
    probe.expectMsg(DeviceRegistered)
    val toShutDown = probe.lastSender

    probe.watch(toShutDown)
    toShutDown ! PoisonPill
    probe.expectTerminated(toShutDown)

  }

}