package persistence.persistasync

import akka.persistence.PersistentActor

class DeferAsyncActor extends PersistentActor {

  override def persistenceId = "defer-async-id"

  override def receiveRecover: Receive = {
    case evt ⇒ println(s"receiveRecover: $evt")
  }

  override def receiveCommand: Receive = {
    case c: String ⇒
      sender() ! c

      persistAsync(s"evt-$c-1") { e ⇒
        sender() ! e
      }
      persistAsync(s"evt-$c-2") { e ⇒
        sender() ! e
      }
      deferAsync(s"evt-$c-3") { e ⇒
        sender() ! e
      }
  }
}
