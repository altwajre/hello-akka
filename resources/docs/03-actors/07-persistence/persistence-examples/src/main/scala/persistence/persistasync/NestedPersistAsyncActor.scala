package persistence.persistasync

import akka.persistence.PersistentActor

class NestedPersistAsyncActor extends PersistentActor {

  override def persistenceId = "nested-persist-id"

  override def receiveRecover: Receive = {
    case evt ⇒ println(s"receiveRecover: $evt")
  }

  override def receiveCommand: Receive = {
    case c: String ⇒
      sender() ! c
      persistAsync(c + "-outer-1") { outer ⇒
        sender() ! outer
        persistAsync(c + "-inner-1") { inner ⇒ sender() ! inner }
      }
      persistAsync(c + "-outer-2") { outer ⇒
        sender() ! outer
        persistAsync(c + "-inner-2") { inner ⇒ sender() ! inner }
      }
  }

}
