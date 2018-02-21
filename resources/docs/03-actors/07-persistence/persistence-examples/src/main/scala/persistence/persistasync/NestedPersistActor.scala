package persistence.persistasync

import akka.persistence.PersistentActor

class NestedPersistActor extends PersistentActor {

  override def persistenceId = "nested-persist-id"

  override def receiveRecover: Receive = {
    case evt ⇒ println(s"receiveRecover: $evt")
  }

  override def receiveCommand: Receive = {
    case c: String ⇒
      sender() ! c
      persist(s"$c-1-outer") { outer1 ⇒
        sender() ! outer1
        persist(s"$c-1-inner") { inner1 ⇒ sender() ! inner1 }
      }
      persist(s"$c-2-outer") { outer2 ⇒
        sender() ! outer2
        persist(s"$c-2-inner") { inner2 ⇒ sender() ! inner2 }
      }
  }

}
