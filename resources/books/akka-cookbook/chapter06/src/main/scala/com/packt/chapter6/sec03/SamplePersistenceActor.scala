package com.packt.chapter6.sec03

import akka.persistence.{PersistentActor, SnapshotOffer}

class SamplePersistenceActor extends PersistentActor {

  override val persistenceId = "unique-id-1"

  var state = ActiveUsers()

  def updateState(event: Event) = {
    println(s"updateState: $event")
    state = state.update(event)
  }

  override val receiveRecover: Receive = {

    case event: Event =>
      println(s"receiveRecover: $event")
      updateState(event)

    case SnapshotOffer(_, snapshot: ActiveUsers) =>
      println(s"receiveRecover: SnapshotOffer: $snapshot")
      state = snapshot

  }

  override val receiveCommand: Receive = {

    case UserUpdate(userId, Add) =>
      println(s"receiveCommand: Add $userId")
      persist(UserAdded(userId))(updateState)

    case UserUpdate(userId, Remove) =>
      println(s"receiveCommand: Remove $userId")
      persist(UserRemoved(userId))(updateState)

    case "snap" =>
      println("receiveCommand: snap")
      saveSnapshot(state)

    case "print" =>
      println(s"receiveCommand: print: $state")

    case ShutdownPersistentActor =>
      println("receiveCommand: Shutdown")
      context.stop(self)

  }

  override def postStop() = println(s"Stopping [${self.path}]")

}