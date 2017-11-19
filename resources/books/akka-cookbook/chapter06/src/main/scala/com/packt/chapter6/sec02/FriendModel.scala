package com.packt.chapter6.sec02

// ---------------------------------------------------------------------------------------------------------------------

sealed trait FriendCommand

case class AddFriend(friend: Friend) extends FriendCommand

case class RemoveFriend(friend: Friend) extends FriendCommand

// ---------------------------------------------------------------------------------------------------------------------

sealed trait FriendEvent

case class FriendAdded(friend: Friend) extends FriendEvent

case class FriendRemoved(friend: Friend) extends FriendEvent

// ---------------------------------------------------------------------------------------------------------------------

case class FriendState(friends: Vector[Friend] = Vector.empty[Friend]) {
  def update(event: FriendEvent) = event match {
    case FriendAdded(friend) => copy(friends :+ friend)
    case FriendRemoved(friend) => copy(friends.filterNot(_ == friend))
  }

  override def toString = friends.mkString(",")
}

case class Friend(id: String)
