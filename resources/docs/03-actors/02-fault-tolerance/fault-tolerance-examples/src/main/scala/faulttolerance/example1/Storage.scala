package faulttolerance.example1

import akka.actor.Actor
import akka.event.LoggingReceive

object Storage {
  final case class Store(entry: Entry)
  final case class Get(key: String)
  final case class Entry(key: String, value: Long)
  class StorageException(msg: String) extends RuntimeException(msg)
}

/**
  * Saves key/value pairs to persistent storage when receiving `Store` message.
  * Replies with current value when receiving `Get` message.
  * Will throw StorageException if the underlying data store is out of order.
  */
class Storage extends Actor {
  import Storage._

  val db = DummyDB

  def receive = LoggingReceive {
    case Store(Entry(key, count)) ⇒ db.save(key, count)
    case Get(key)                 ⇒ sender() ! Entry(key, db.load(key).getOrElse(0L))
  }
}

object DummyDB {
  import Storage.StorageException
  private var db = Map[String, Long]()

  @throws(classOf[StorageException])
  def save(key: String, value: Long): Unit = synchronized {
    if (11 <= value && value <= 14)
      throw new StorageException("Simulated store failure " + value)
    db += (key -> value)
  }

  @throws(classOf[StorageException])
  def load(key: String): Option[Long] = synchronized {
    db.get(key)
  }
}
