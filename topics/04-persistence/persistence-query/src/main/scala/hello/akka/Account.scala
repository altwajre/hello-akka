package hello.akka

import akka.persistence.fsm.PersistentFSM
import akka.persistence.fsm.PersistentFSM.FSMState

import scala.reflect._

class Account extends PersistentFSM[Account.State, Account.Data, Account.DomainEvent] {

  import Account._

  override def persistenceId: String = "account"

  override def applyEvent(evt: DomainEvent, currentData: Data): Data = {
    evt match {
      case AcceptedTransaction(amount, CR) =>
        val newAmount = currentData.amount + amount
        println(s"Your new balance is $newAmount")
        Balance(newAmount)
      case AcceptedTransaction(amount, DR) =>
        val newAmount = currentData.amount - amount
        println(s"Your new balance is $newAmount")
        if (newAmount > 0)
          Balance(newAmount)
        else
          ZeroBalance
      case RejectedTransaction(_, _, reason) =>
        println(s"Rejected transaction: $reason")
        currentData
    }
  }

  //override implicit def domainEventClassTag: ClassTag[DomainEvent] = classTag[DomainEvent]
  override def domainEventClassTag: ClassTag[DomainEvent] = classTag[DomainEvent]

  startWith(Empty, ZeroBalance)

  when(Empty) {

    case Event(Operation(amount, CR), _) =>
      println("Welcome, This is your first Credit operation")
      goto(Active) applying AcceptedTransaction(amount, CR)

    case Event(Operation(amount, DR), _) =>
      println("Sorry, insufficient funds")
      stay applying RejectedTransaction(amount, DR, "Insufficient funds")

  }

  when(Active) {

    case Event(Operation(amount, CR), _) =>
      println("Welcome, This is your first Credit operation")
      stay applying AcceptedTransaction(amount, CR)

    case Event(Operation(amount, DR), balance) =>
      val newBalance = balance.amount - amount
      if (newBalance > 0)
        stay applying AcceptedTransaction(amount, DR)
      else if (newBalance == 0)
        goto(Empty) applying AcceptedTransaction(amount, DR)
      else
        stay applying RejectedTransaction(amount, DR, "Insufficient funds")
  }

}

object Account {

  sealed trait State extends FSMState

  case object Empty extends State {
    override def identifier: String = "Empty"
  }

  case object Active extends State {
    override def identifier: String = "Active"
  }

  sealed trait Data {
    val amount: Float
  }

  case object ZeroBalance extends Data {
    override val amount: Float = 0.0F
  }

  case class Balance(amount: Float) extends Data

  sealed trait DomainEvent

  case class AcceptedTransaction(amount: Float,
                                 `type`: TransactionType) extends DomainEvent

  case class RejectedTransaction(amount: Float,
                                 `type`: TransactionType,
                                 reason: String) extends DomainEvent

  sealed trait TransactionType

  case object CR extends TransactionType

  case object DR extends TransactionType

  case class Operation(amount: Float, `type`: TransactionType)

}
