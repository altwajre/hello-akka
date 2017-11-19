package com.packt.chapter6.sec01

// ---------------------------------------------------------------------------------------------------------------------

sealed trait Command

case object Add extends Command

case object Remove extends Command

// ---------------------------------------------------------------------------------------------------------------------

sealed trait Event

case class UserAdded(userId: String) extends Event

case class UserRemoved(userId: String) extends Event

// ---------------------------------------------------------------------------------------------------------------------

case class UserUpdate(userId: String, command: Command)

case class ActiveUsers(userIds: Set[String] = Set.empty[String]) {
  def update(event: Event) = event match {
    case UserAdded(userId) => copy(userIds + userId)
    case UserRemoved(userId) => copy(userIds.filterNot(_ == userId))
  }

  override def toString = s"$userIds"
}

case object ShutdownPersistentActor