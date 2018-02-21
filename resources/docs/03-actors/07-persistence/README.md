# Persistence - Overview
- Akka persistence enables stateful actors to persist their internal state so that it can be recovered when an actor is:
    - Started. 
    - Restarted after a JVM crash or by a supervisor. 
    - Or migrated in a cluster. 
- The key concept behind Akka persistence is that:
    - only changes to an actor’s internal state are persisted.
    - but never its current state directly.
    - except for optional snapshots.
- These changes are only ever appended to storage, nothing is ever mutated.
- This allows for very high transaction rates and efficient replication. 
- Stateful actors are recovered by replaying stored changes to these actors from which they can rebuild internal state. 
- This can be either the full history of changes or starting from a snapshot which can dramatically reduce recovery times. 
- Akka persistence also provides point-to-point communication with at-least-once message delivery semantics.

# Dependencies
- Akka persistence is a separate jar file. 
- Make sure that you have the following dependency in your project:
```sbtshell
"com.typesafe.akka" %% "akka-persistence" % "2.5.9"
```
- The Akka persistence extension comes with few built-in persistence plugins:
    - in-memory heap based journal, 
    - local file-system based snapshot-store 
    - and LevelDB based journal.
- LevelDB based plugins will require the following additional dependency declaration:
```sbtshell
"org.fusesource.leveldbjni" % "leveldbjni-all"   % "1.8"
```


# Architecture

### `PersistentActor`: 
- Is a persistent, stateful actor.   
- It is able to persist events to a journal and can react to them in a thread-safe manner.   
- It can be used to implement both command as well as event sourced actors.   
- When a persistent actor is started or restarted,   
    - journaled messages are replayed to that actor so that it can recover internal state from these messages.

### `AtLeastOnceDelivery`: 
- To send messages with at-least-once delivery semantics to destinations,   
    - also in case of sender and receiver JVM crashes.

### `AsyncWriteJournal`:  
- A journal stores the sequence of messages sent to a persistent actor.  
- An application can control which messages are journaled and which are received by the persistent actor without being journaled.  
- Journal maintains `highestSequenceNr` that is increased on each message.  
- The storage backend of a journal is pluggable.  
- The persistence extension comes with a LevelDB journal plugin, which writes to the local filesystem.  
- Replicated journals are available as [Community plugins](http://akka.io/community/?_ga=2.251477376.745399318.1519143948-542223074.1518507267).

### Snapshot store:   
- A snapshot store persists snapshots of a persistent actor’s internal state.  
- Snapshots are used for optimizing recovery times.  
- The storage backend of a snapshot store is pluggable.  
- The persistence extension comes with a "local" snapshot storage plugin, which writes to the local filesystem.

### Event sourcing: 
- Akka persistence provides abstractions for the development of event sourced applications.
- Replicated snapshot stores are available as [Community plugins](https://index.scala-lang.org/search?topics=akka-persistence).
- See [Introduction to Event Sourcing](https://msdn.microsoft.com/en-us/library/jj591559.aspx)

# Event sourcing
- See [Events As First-Class Citizens](https://hackernoon.com/events-as-first-class-citizens-8633e8479493).
- A persistent actor receives a (non-persistent) command:
    - which is first validated if it can be applied to the current state.  
- Here validation can mean anything:
    - from simple inspection of a command message’s fields 
    - up to a conversation with several external services.  
- If validation succeeds, events are generated from the command, representing the effect of the command.  
- These events are then persisted and, after successful persistence, used to change the actor’s state.
- When the persistent actor needs to be recovered:
    - Only the persisted events are replayed of which we know that they can be successfully applied.
    - In other words, events cannot fail when being replayed to a persistent actor, in contrast to commands.
- Event sourced actors may of course also process commands that do not change application state:
    - such as query commands for example.
- Akka persistence supports event sourcing with the `PersistentActor` trait.
- An actor that extends this trait uses the `persist` method to persist and handle events.
- The behavior of a `PersistentActor` is defined by implementing `receiveRecover` and `receiveCommand`.
- This is demonstrated in the following example.
- See [Example 1](./persistence-examples/src/main/scala/persistence/example1)
```scala
import akka.actor._
import akka.persistence._

case class Cmd(data: String)
case class Evt(data: String)

case class ExampleState(events: List[String] = Nil) {
  def updated(evt: Evt): ExampleState = copy(evt.data :: events)
  def size: Int = events.length
  override def toString: String = events.reverse.toString
}

class ExamplePersistentActor extends PersistentActor {
  override def persistenceId = "sample-id-1"

  var state = ExampleState()

  def updateState(event: Evt): Unit =
    state = state.updated(event)

  def numEvents =
    state.size

  val receiveRecover: Receive = {
    case evt: Evt                                 ⇒ updateState(evt)
    case SnapshotOffer(_, snapshot: ExampleState) ⇒ state = snapshot
  }

  val snapShotInterval = 1000
  val receiveCommand: Receive = {
    case Cmd(data) ⇒
      persist(Evt(s"${data}-${numEvents}")) { event ⇒
        updateState(event)
        context.system.eventStream.publish(event)
        if (lastSequenceNr % snapShotInterval == 0 && lastSequenceNr != 0)
          saveSnapshot(state)
      }
    case "print" ⇒ println(state)
  }

}
```
- The example defines two data types, Cmd and Evt to represent commands and events, respectively.
- The state of the ExamplePersistentActor is a list of persisted event data contained in ExampleState.
- The persistent actor’s receiveRecover method defines how state is updated during recovery:
    - by handling Evt and SnapshotOffer messages.
- The persistent actor’s receiveCommand method is a command handler.
- In this example, a command is handled by generating an event which is then persisted and handled.
- Events are persisted by calling persist with:
    - an event (or a sequence of events) as first argument 
    - and an event handler as second argument.
- The persist method persists events asynchronously and the event handler is executed for successfully persisted events.
- Successfully persisted events are internally sent back to the persistent actor:
    - as individual messages that trigger event handler executions.
- An event handler may close over persistent actor state and mutate it.
- The sender of a persisted event is the sender of the corresponding command.
- This allows event handlers to reply to the sender of a command (not shown).
- The main responsibility of an event handler is:
    - changing persistent actor state using event data 
    - and notifying others about successful state changes by publishing events.
- When persisting events with persist it is guaranteed that:
    - the persistent actor will not receive further commands
    - between the persist call and the execution(s) of the associated event handler.
- This also holds for multiple persist calls in context of a single command.
- Incoming messages are stashed until the persist is completed.
- If persistence of an event fails, onPersistFailure will be invoked 
    - (logging the error by default), 
    - and the actor will unconditionally be stopped.
- If persistence of an event is rejected before it is stored, 
    - e.g. due to serialization error, 
    - onPersistRejected will be invoked (logging a warning by default) 
    - and the actor continues with the next message.
- The easiest way to run this example yourself is to 
    - download the ready to run Akka Persistence Sample with Scala together with the tutorial. 
- It contains instructions on how to run the PersistentActorExample.
- The source code of this sample can be found in the Akka Samples Repository.

#### Note
- It’s also possible to switch between different command handlers during normal processing and recovery
    - with context.become() and context.unbecome().
- To get the actor into the same state after recovery 
    - you need to take special care to perform the same state transitions
    - with become and unbecome in the receiveRecover method 
    - as you would have done in the command handler.
- Note that when using become from receiveRecover it will still only use the receiveRecover behavior when replaying the events.
- When replay is completed it will use the new behavior.

## Identifiers
- A persistent actor must have an identifier that doesn’t change across different actor incarnations.
- The identifier must be defined with the persistenceId method.
```scala
override def persistenceId = "my-stable-persistence-id"
```
#### Note
- persistenceId must be unique to a given entity in the journal (database table/keyspace).
- When replaying messages persisted to the journal, you query messages with a persistenceId.
- So, if two different entities share the same persistenceId, message-replaying behavior is corrupted.

## Recovery
- By default, a persistent actor is automatically recovered on start and on restart by replaying journaled messages.
- New messages sent to a persistent actor during recovery do not interfere with replayed messages.
- They are stashed and received by a persistent actor after recovery phase completes.
- The number of concurrent recoveries that can be in progress at the same time is limited 
    - to not overload the system and the backend data store.
- When exceeding the limit the actors will wait until other recoveries have been completed.
- This is configured by:
```hocon
akka.persistence.max-concurrent-recoveries = 50
```
#### Note
- Accessing the sender() for replayed messages will always result in a deadLetters reference, 
    - as the original sender is presumed to be long gone.
- If you indeed have to notify an actor during recovery in the future, 
    - store its ActorPath explicitly in your persisted events.

### Recovery customization
- Applications may also customise how recovery is performed by returning a customised Recovery object 
    - in the recovery method of a PersistentActor,
- To skip loading snapshots and replay all events you can use SnapshotSelectionCriteria.None.
- This can be useful if snapshot serialization format has changed in an incompatible way.
- It should typically not be used when events have been deleted.
```scala
override def recovery =
  Recovery(fromSnapshot = SnapshotSelectionCriteria.None)
```
- Another possible recovery customization, which can be useful for debugging, 
    - is setting an upper bound on the replay, 
    - causing the actor to be replayed only up to a certain point "in the past" 
    - (instead of being replayed to its most up to date state).
- Note that after that it is a bad idea to persist new events 
    - because a later recovery will probably be confused by the new events 
    - that follow the events that were previously skipped.
```scala
override def recovery = Recovery(toSequenceNr = 457L)
```
- Recovery can be disabled by returning Recovery.none() in the recovery method of a PersistentActor:
```scala
override def recovery = Recovery.none
```

### Recovery status
- A persistent actor can query its own recovery status via the methods
```scala
def recoveryRunning: Boolean
def recoveryFinished: Boolean
```
- Sometimes there is a need for performing additional initialization when the recovery has completed 
    - before processing any other message sent to the persistent actor.
- The persistent actor will receive a special RecoveryCompleted message 
    - right after recovery and before any other received messages.
```scala
override def receiveRecover: Receive = {
  case RecoveryCompleted ⇒
  // perform init after recovery, before any other messages
  //...
  case evt               ⇒ //...
}

override def receiveCommand: Receive = {
  case msg ⇒ //...
}
```
- The actor will always receive a RecoveryCompleted message, 
    - even if there are no events in the journal and the snapshot store is empty, 
    - or if it’s a new persistent actor with a previously unused persistenceId.
- If there is a problem with recovering the state of the actor from the journal, 
    - onRecoveryFailure is called (logging the error by default) and the actor will be stopped.

## Internal stash
- The persistent actor has a private stash for internally caching incoming messages 
    - during recovery or the persist\persistAll method persisting events.
- You can still use/inherit from the Stash interface.
- The internal stash cooperates with the normal stash by hooking into unstashAll method 
    - and making sure messages are unstashed properly to the internal stash to maintain ordering guarantees.
- You should be careful to not send more messages to a persistent actor than it can keep up with, 
    - otherwise the number of stashed messages will grow without bounds.
- It can be wise to protect against OutOfMemoryError by defining a maximum stash capacity in the mailbox configuration:
```hocon
akka.actor.default-mailbox.stash-capacity=10000
```
- Note that the stash capacity is per actor.
- If you have many persistent actors, e.g. when using cluster sharding, 
    - you may need to define a small stash capacity 
    - to ensure that the total number of stashed messages in the system doesn’t consume too much memory.
- Additionally, the persistent actor defines three strategies to handle failure when the internal stash capacity is exceeded.
- The default overflow strategy is the ThrowOverflowExceptionStrategy, 
    - which discards the current received message and throws a StashOverflowException, 
    - causing actor restart if the default supervision strategy is used.
- You can override the internalStashOverflowStrategy method 
    - to return DiscardToDeadLetterStrategy or ReplyToStrategy for any "individual" persistent actor, 
    - or define the "default" for all persistent actors by providing FQCN, 
    - which must be a subclass of StashOverflowStrategyConfigurator, in the persistence configuration:
```hocon
akka.persistence.internal-stash-overflow-strategy=
  "akka.persistence.ThrowExceptionConfigurator"
```
- The DiscardToDeadLetterStrategy strategy also has a pre-packaged companion configurator akka.persistence.DiscardConfigurator.
- You can also query the default strategy via the Akka persistence extension singleton: 
```scala
Persistence(context.system).defaultInternalStashOverflowStrategy
```
#### Note
- The bounded mailbox should be avoided in the persistent actor, by which the messages come from storage backends may be discarded.
- You can use bounded stash instead of it.

## Relaxed local consistency requirements and high throughput use-cases
- If faced with relaxed local consistency requirements and high throughput demands 
    - sometimes PersistentActor and its persist may not be enough in terms of consuming incoming Commands at a high rate, 
    - because it has to wait until all Events related to a given Command are processed 
    - in order to start processing the next Command.
- While this abstraction is very useful for most cases, 
    - sometimes you may be faced with relaxed requirements about consistency - 
    - for example you may want to process commands as fast as you can, 
    - assuming that the Event will eventually be persisted and handled properly in the background, 
    - retroactively reacting to persistence failures if needed.
- The persistAsync method provides a tool for implementing high-throughput persistent actors.
- It will not stash incoming Commands while the Journal is still working on persisting and/or user code is executing event callbacks.
- In the below example, the event callbacks may be called "at any time", even after the next Command has been processed.
- The ordering between events is still guaranteed ("evt-b-1" will be sent after "evt-a-2", which will be sent after "evt-a-1" etc.).
```scala
class MyPersistentActor extends PersistentActor {

  override def persistenceId = "my-stable-persistence-id"

  override def receiveRecover: Receive = {
    case _ ⇒ // handle recovery here
  }

  override def receiveCommand: Receive = {
    case c: String ⇒ {
      sender() ! c
      persistAsync(s"evt-$c-1") { e ⇒ sender() ! e }
      persistAsync(s"evt-$c-2") { e ⇒ sender() ! e }
    }
  }
}

// usage
persistentActor ! "a"
persistentActor ! "b"

// possible order of received messages:
// a
// b
// evt-a-1
// evt-a-2
// evt-b-1
// evt-b-2
```

#### Note
- In order to implement the pattern known as "command sourcing" 
    - simply call persistAsync(cmd)(...) right away on all incoming messages and handle them in the callback.

#### Warning
- The callback will not be invoked if the actor is restarted (or stopped)
    - in between the call to persistAsync and the journal has confirmed the write.

## Deferring actions until preceding persist handlers have executed
- Sometimes when working with persistAsync or persist 
    - you may find that it would be nice to define some actions 
    - in terms of "happens-after the previous persistAsync/persist handlers have been invoked".
- PersistentActor provides an utility method called deferAsync, 
    - which works similarly to persistAsync yet does not persist the passed in event.
- It is recommended to use it for read operations, and actions which do not have corresponding events in your domain model.
- Using this method is very similar to the persist family of methods, yet it does not persist the passed in event.
- It will be kept in memory and used when invoking the handler.
```scala
class MyPersistentActor extends PersistentActor {

  override def persistenceId = "my-stable-persistence-id"

  override def receiveRecover: Receive = {
    case _ ⇒ // handle recovery here
  }

  override def receiveCommand: Receive = {
    case c: String ⇒ {
      sender() ! c
      persistAsync(s"evt-$c-1") { e ⇒ sender() ! e }
      persistAsync(s"evt-$c-2") { e ⇒ sender() ! e }
      deferAsync(s"evt-$c-3") { e ⇒ sender() ! e }
    }
  }
}
```
- Notice that the sender() is safe to access in the handler callback, 
    - and will be pointing to the original sender of the command for which this deferAsync handler was called.
- The calling side will get the responses in this (guaranteed) order:
```scala
persistentActor ! "a"
persistentActor ! "b"

// order of received messages:
// a
// b
// evt-a-1
// evt-a-2
// evt-a-3
// evt-b-1
// evt-b-2
// evt-b-3
```
- You can also call deferAsync with persist.
```scala
class MyPersistentActor extends PersistentActor {

  override def persistenceId = "my-stable-persistence-id"

  override def receiveRecover: Receive = {
    case _ ⇒ // handle recovery here
  }

  override def receiveCommand: Receive = {
    case c: String ⇒ {
      sender() ! c
      persist(s"evt-$c-1") { e ⇒ sender() ! e }
      persist(s"evt-$c-2") { e ⇒ sender() ! e }
      deferAsync(s"evt-$c-3") { e ⇒ sender() ! e }
    }
  }
}
```

#### Warning
- The callback will not be invoked if the actor is restarted (or stopped) 
    - in between the call to deferAsync and the journal has processed and confirmed all preceding writes.

## Nested persist calls
- It is possible to call persist and persistAsync inside their respective callback blocks  
    - and they will properly retain both the thread safety (including the right value of sender())  
    - as well as stashing guarantees.
- In general it is encouraged to create command handlers which do not need to resort to nested event persisting,  
    - however there are situations where it may be useful.
- It is important to understand the ordering of callback execution in those situations,  
    - as well as their implication on the stashing behaviour (that persist() enforces).
- In the following example two persist calls are issued, and each of them issues another persist inside its callback:
```scala
override def receiveCommand: Receive = {
  case c: String ⇒
    sender() ! c

    persist(s"$c-1-outer") { outer1 ⇒
      sender() ! outer1
      persist(s"$c-1-inner") { inner1 ⇒
        sender() ! inner1
      }
    }

    persist(s"$c-2-outer") { outer2 ⇒
      sender() ! outer2
      persist(s"$c-2-inner") { inner2 ⇒
        sender() ! inner2
      }
    }
}
```
- When sending two commands to this PersistentActor, the persist handlers will be executed in the following order:
```scala
persistentActor ! "a"
persistentActor ! "b"

// order of received messages:
// a
// a-outer-1
// a-outer-2
// a-inner-1
// a-inner-2
// and only then process "b"
// b
// b-outer-1
// b-outer-2
// b-inner-1
// b-inner-2
```
- First the "outer layer" of persist calls is issued and their callbacks are applied.
- After these have successfully completed,  
    - the inner callbacks will be invoked  
    - (once the events they are persisting have been confirmed to be persisted by the journal).
- Only after all these handlers have been successfully invoked will the next command be delivered to the persistent Actor.
- In other words, the stashing of incoming commands  
    - that is guaranteed by initially calling persist() on the outer layer  
    - is extended until all nested persist callbacks have been handled.
- It is also possible to nest persistAsync calls, using the same pattern:
```scala
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
```
- In this case no stashing is happening, yet events are still persisted and callbacks are executed in the expected order:
```scala
persistentActor ! "a"
persistentActor ! "b"

// order of received messages:
// a
// b
// a-outer-1
// a-outer-2
// b-outer-1
// b-outer-2
// a-inner-1
// a-inner-2
// b-inner-1
// b-inner-2

// which can be seen as the following causal relationship:
// a -> a-outer-1 -> a-outer-2 -> a-inner-1 -> a-inner-2
// b -> b-outer-1 -> b-outer-2 -> b-inner-1 -> b-inner-2
```
- While it is possible to nest mixed persist and persistAsync with keeping their respective semantics  
    - it is not a recommended practice, as it may lead to overly complex nesting.
    
#### Warning
- While it is possible to nest persist calls within one another,  
    - it is not legal to call persist from any other Thread than the Actors message processing Thread.
- For example, **it is not legal to call persist from Futures**. 
- Doing so will break the guarantees that the persist methods aim to provide.
- Always call persist and persistAsync from within the Actor’s receive block  
    - (or methods synchronously invoked from there).

## Failures
- If persistence of an event fails, onPersistFailure will be invoked (logging the error by default),  
    - and the actor will unconditionally be stopped.
- The reason that it cannot resume when persist fails  
    - is that it is unknown if the event was actually persisted or not,  
    - and therefore it is in an inconsistent state.
- Restarting on persistent failures will most likely fail anyway since the journal is probably unavailable.
- It is better to stop the actor and after a back-off timeout start it again.
- The akka.pattern.BackoffSupervisor actor is provided to support such restarts.
```scala
val childProps = Props[MyPersistentActor]
val props = BackoffSupervisor.props(
  Backoff.onStop(
    childProps,
    childName = "myActor",
    minBackoff = 3.seconds,
    maxBackoff = 30.seconds,
    randomFactor = 0.2))
context.actorOf(props, name = "mySupervisor")
```
- If persistence of an event is rejected before it is stored, e.g. due to serialization error,  
    - onPersistRejected will be invoked (logging a warning by default), and the actor continues with next message.
- If there is a problem with recovering the state of the actor from the journal when the actor is started,  
    - onRecoveryFailure is called (logging the error by default), and the actor will be stopped.
- Note that failure to load snapshot is also treated like this,  
    - but you can disable loading of snapshots  
    - if you for example know that serialization format has changed in an incompatible way,  
    - see Recovery customization.

## Atomic writes
- Each event is of course stored atomically, but it is also possible to store several events atomically  
    - by using the persistAll or persistAllAsync method.
- That means that all events passed to that method are stored or none of them are stored if there is an error.
- The recovery of a persistent actor will therefore never be done partially with only a subset of events persisted by persistAll.
- Some journals may not support atomic writes of several events and they will then reject the persistAll command,  
    - i.e. onPersistRejected is called with an exception (typically UnsupportedOperationException).

## Batch writes
- In order to optimize throughput when using persistAsync,  
    - a persistent actor internally batches events to be stored under high load  
    - before writing them to the journal (as a single batch).
- The batch size is dynamically determined by how many events are emitted during the time of a journal round-trip:  
    - after sending a batch to the journal no further batch can be sent  
    - before confirmation has been received that the previous batch has been written.
- Batch writes are never timer-based which keeps latencies at a minimum.

## Message deletion
- It is possible to delete all messages (journaled by a single persistent actor) up to a specified sequence number;  
    - Persistent actors may call the deleteMessages method to this end.
- Deleting messages in event sourcing based applications is typically either  
    - not used at all, or used in conjunction with snapshotting,  
    - i.e. after a snapshot has been successfully stored,  
    - a deleteMessages(toSequenceNr) up until the sequence number of the data held by that snapshot can be issued  
    - to safely delete the previous events while still having access to the accumulated state during replays 
    - by loading the snapshot.
#### Warning
- If you are using Persistence Query, query results may be missing deleted messages in a journal,  
    - depending on how deletions are implemented in the journal plugin.
- Unless you use a plugin which still shows deleted messages in persistence query results,  
    - you have to design your application so that it is not affected by missing messages.
- The result of the deleteMessages request is signaled to the persistent actor  
    - with a DeleteMessagesSuccess message if the delete was successful  
    - or a DeleteMessagesFailure message if it failed.
- Message deletion doesn’t affect the highest sequence number of the journal,  
    - even if all messages were deleted from it after deleteMessages invocation.

## Persistence status handling
- Persisting, deleting, and replaying messages can either succeed or fail.

| Method                 | Success                 |
|:-----------------------|:------------------------|
| persist / persistAsync | persist handler invoked |
| onPersistRejected      | No automatic actions.   |
| recovery               | RecoveryCompleted       |
| deleteMessages         | DeleteMessagesSuccess   |

- The most important operations (persist and recovery) have failure handlers modelled as explicit callbacks  
    - which the user can override in the PersistentActor.
- The default implementations of these handlers emit a log message  
    - (error for persist/recovery failures, and warning for others),  
    - logging the failure cause and information about which message caused the failure.
- For critical failures, such as recovery or persisting events failing,  
    - the persistent actor will be stopped after the failure handler is invoked.
- This is because if the underlying journal implementation is signalling persistence failures  
    - it is most likely either failing completely or overloaded  
    - and restarting right-away and trying to persist the event again will most likely not help the journal recover 
    - as it would likely cause a Thundering herd problem,  
    - as many persistent actors would restart and try to persist their events again.
- Instead, use a BackoffSupervisor (as described in Failures)  
    - which implements an exponential-backoff strategy  
    - and allows for more breathing room for the journal to recover  
    - between restarts of the persistent actor.

#### Note
- Journal implementations may choose to implement a retry mechanism,  
    - e.g. such that only after a write fails N number of times a persistence failure is signalled back to the user.
- In other words, once a journal returns a failure, it is considered fatal by Akka Persistence,  
    - and the persistent actor which caused the failure will be stopped.
- Check the documentation of the journal implementation you are using for details if/how it is using this technique.

## Safely shutting down persistent actors
- Special care should be given when shutting down persistent actors from the outside.
- With normal Actors it is often acceptable to use the special PoisonPill message  
    - to signal to an Actor that it should stop itself once it receives this message 
    - in fact this message is handled automatically by Akka,  
    - leaving the target actor no way to refuse stopping itself when given a poison pill.
- This can be dangerous when used with PersistentActor due to the fact that incoming commands are stashed  
    - while the persistent actor is awaiting confirmation from the Journal  
    - that events have been written when persist() was used.
- Since the incoming commands will be drained from the Actor’s mailbox and put into its internal stash  
    - while awaiting the confirmation (thus, before calling the persist handlers)  
    - the Actor may receive and (auto)handle the PoisonPill before it processes the other messages  
    - which have been put into its stash, causing a pre-mature shutdown of the Actor.

#### Warning
- Consider using explicit shut-down messages instead of PoisonPill when working with persistent actors.
- The example below highlights how messages arrive in the Actor’s mailbox  
    - and how they interact with its internal stashing mechanism when persist() is used.
- Notice the early stop behaviour that occurs when PoisonPill is used:

```scala
/** Explicit shutdown message */
case object Shutdown

class SafePersistentActor extends PersistentActor {
  override def persistenceId = "safe-actor"

  override def receiveCommand: Receive = {
    case c: String ⇒
      println(c)
      persist(s"handle-$c") { println(_) }
    case Shutdown ⇒
      context.stop(self)
  }

  override def receiveRecover: Receive = {
    case _ ⇒ // handle recovery here
  }
}
```
```scala
// UN-SAFE, due to PersistentActor's command stashing:
persistentActor ! "a"
persistentActor ! "b"
persistentActor ! PoisonPill
// order of received messages:
// a
//   # b arrives at mailbox, stashing;        internal-stash = [b]
// PoisonPill is an AutoReceivedMessage, is handled automatically
// !! stop !!
// Actor is stopped without handling `b` nor the `a` handler!
```
```scala
// SAFE:
persistentActor ! "a"
persistentActor ! "b"
persistentActor ! Shutdown
// order of received messages:
// a
//   # b arrives at mailbox, stashing;        internal-stash = [b]
//   # Shutdown arrives at mailbox, stashing; internal-stash = [b, Shutdown]
// handle-a
//   # unstashing;                            internal-stash = [Shutdown]
// b
// handle-b
//   # unstashing;                            internal-stash = []
// Shutdown
// -- stop --
```

## Replay Filter
- There could be cases where event streams are corrupted  
    - and multiple writers (i.e. multiple persistent actor instances)  
    - journaled different messages with the same sequence number.
- In such a case, you can configure how you filter replayed messages from multiple writers, upon recovery.
- In your configuration, under the akka.persistence.journal.xxx.replay-filter section  
    - (where xxx is your journal plugin id),  
    - you can select the replay filter mode from one of the following values:
        - repair-by-discard-old
        - fail
        - warn
        - off
- For example, if you configure the replay filter for leveldb plugin, it looks like this:
```hocon
# The replay filter can detect a corrupt event stream by inspecting
# sequence numbers and writerUuid when replaying events.
akka.persistence.journal.leveldb.replay-filter {
  # What the filter should do when detecting invalid events.
  # Supported values:
  # `repair-by-discard-old` : discard events from old writers,
  #                           warning is logged
  # `fail` : fail the replay, error is logged
  # `warn` : log warning but emit events untouched
  # `off` : disable this feature completely
  mode = repair-by-discard-old
}
```

# Snapshots
- As you model your domain using actors,  
    - you may notice that some actors may be prone to accumulating extremely long event logs  
    - and experiencing long recovery times.
- Sometimes, the right approach may be to split out into a set of shorter lived actors.
- However, when this is not an option, you can use snapshots to reduce recovery times drastically.
- Persistent actors can save snapshots of internal state by calling the saveSnapshot method.
- If saving of a snapshot succeeds, the persistent actor receives a SaveSnapshotSuccess message,  
    - otherwise a SaveSnapshotFailure message
```scala
var state: Any = _

val snapShotInterval = 1000
override def receiveCommand: Receive = {
  case SaveSnapshotSuccess(metadata)         ⇒ // ...
  case SaveSnapshotFailure(metadata, reason) ⇒ // ...
  case cmd: String ⇒
    persist(s"evt-$cmd") { e ⇒
      updateState(e)
      if (lastSequenceNr % snapShotInterval == 0 && lastSequenceNr != 0)
        saveSnapshot(state)
    }
}
```
- where metadata is of type SnapshotMetadata:
```scala
final case class SnapshotMetadata(persistenceId: String, sequenceNr: Long, timestamp: Long = 0L)
```
- During recovery, the persistent actor is offered a previously saved snapshot via a SnapshotOffer message  
    - from which it can initialize internal state.
```scala
var state: Any = _

override def receiveRecover: Receive = {
  case SnapshotOffer(metadata, offeredSnapshot) ⇒ state = offeredSnapshot
  case RecoveryCompleted                        ⇒
  case event                                    ⇒ // ...
}
```
- The replayed messages that follow the SnapshotOffer message, if any, are younger than the offered snapshot.
- They finally recover the persistent actor to its current (i.e. latest) state.
- In general, a persistent actor is only offered a snapshot if that persistent actor  
    - has previously saved one or more snapshots  
    - and at least one of these snapshots matches the SnapshotSelectionCriteria that can be specified for recovery.
```scala
override def recovery = Recovery(fromSnapshot = SnapshotSelectionCriteria(
  maxSequenceNr = 457L,
  maxTimestamp = System.currentTimeMillis))
```
- If not specified, they default to SnapshotSelectionCriteria.Latest which selects the latest (= youngest) snapshot.
- To disable snapshot-based recovery, applications should use SnapshotSelectionCriteria.None.
- A recovery where no saved snapshot matches the specified SnapshotSelectionCriteria will replay all journaled messages.

#### Note
- In order to use snapshots, a default snapshot-store (akka.persistence.snapshot-store.plugin) must be configured,  
    - or the PersistentActor can pick a snapshot store explicitly by overriding def snapshotPluginId: String.
- Since it is acceptable for some applications to not use any snapshotting, it is legal to not configure a snapshot store.
- However, Akka will log a warning message when this situation is detected  
    - and then continue to operate until an actor tries to store a snapshot,  
    - at which point the operation will fail (by replying with an SaveSnapshotFailure for example).
- Note that the "persistence mode" of Cluster Sharding makes use of snapshots.
- If you use that mode, you’ll need to define a snapshot store plugin.

## Snapshot deletion
- A persistent actor can delete individual snapshots by calling the deleteSnapshot method  
    - with the sequence number of when the snapshot was taken.
- To bulk-delete a range of snapshots matching SnapshotSelectionCriteria, persistent actors should use the deleteSnapshots method.

## Snapshot status handling
- Saving or deleting snapshots can either succeed or fail 
    - this information is reported back to the persistent actor via status messages  
    - as illustrated in the following table:

| Method                                     | Success                | Failure message        |
|:-------------------------------------------|:-----------------------|:-----------------------|
| saveSnapshot(Any)                          | SaveSnapshotSuccess    | SaveSnapshotFailure    |
| deleteSnapshot(Long)                       | DeleteSnapshotSuccess  | DeleteSnapshotFailure  |
| deleteSnapshots(SnapshotSelectionCriteria) | DeleteSnapshotsSuccess | DeleteSnapshotsFailure |

- If failure messages are left unhandled by the actor,  
    - a default warning log message will be logged  
    - for each incoming failure message.
- No default action is performed on the success messages, however you’re free to handle them  
    - e.g. in order to delete an in memory representation of the snapshot,  
    - or in the case of failure to attempt save the snapshot again.

# At-Least-Once Delivery
- To send messages with at-least-once delivery semantics to destinations  
    - you can mix-in AtLeastOnceDelivery trait to your PersistentActor on the sending side.
- It takes care of re-sending messages when they have not been confirmed within a configurable timeout.
- The state of the sending actor,  
    - including which messages have been sent that have not been confirmed by the recipient  
    - must be persistent so that it can survive a crash of the sending actor or JVM.
- The AtLeastOnceDelivery trait does not persist anything by itself.
- It is your responsibility to persist the intent that a message is sent and that a confirmation has been received.
#### Note
- At-least-once delivery implies that original message sending order is not always preserved,  
    - and the destination may receive duplicate messages.
- Semantics do not match those of a normal ActorRef send operation:
    - it is not at-most-once delivery
    - message order for the same sender-receiver pair is not preserved due to possible resends
    - after a crash and restart of the destination messages are still delivered to the new actor incarnation
- These semantics are similar to what an ActorPath represents (see Actor Lifecycle),  
    - therefore you need to supply a path and not a reference when delivering messages.
- The messages are sent to the path with an actor selection.
- Use the deliver method to send a message to a destination.
- Call the confirmDelivery method when the destination has replied with a confirmation message.

## Relationship between deliver and confirmDelivery
- To send messages to the destination path, use the deliver method after you have persisted the intent to send the message.
- The destination actor must send back a confirmation message.
- When the sending actor receives this confirmation message  
    - you should persist the fact that the message was delivered successfully and then call the confirmDelivery method.
- If the persistent actor is not currently recovering, the deliver method will send the message to the destination actor.
- When recovering, messages will be buffered until they have been confirmed using confirmDelivery.
- Once recovery has completed,  
    - if there are outstanding messages that have not been confirmed (during the message replay),  
    - the persistent actor will resend these before sending any other messages.
- Deliver requires a deliveryIdToMessage function to pass the provided deliveryId into the message  
    - so that the correlation between deliver and confirmDelivery is possible.
- The deliveryId must do the round trip.
- Upon receipt of the message,  
    - the destination actor will send the samedeliveryId wrapped in a confirmation message back to the sender.
- The sender will then use it to call confirmDelivery method to complete the delivery routine.
```scala
import akka.actor.{ Actor, ActorSelection }
import akka.persistence.AtLeastOnceDelivery

case class Msg(deliveryId: Long, s: String)
case class Confirm(deliveryId: Long)

sealed trait Evt
case class MsgSent(s: String) extends Evt
case class MsgConfirmed(deliveryId: Long) extends Evt

class MyPersistentActor(destination: ActorSelection)
  extends PersistentActor with AtLeastOnceDelivery {

  override def persistenceId: String = "persistence-id"

  override def receiveCommand: Receive = {
    case s: String           ⇒ persist(MsgSent(s))(updateState)
    case Confirm(deliveryId) ⇒ persist(MsgConfirmed(deliveryId))(updateState)
  }

  override def receiveRecover: Receive = {
    case evt: Evt ⇒ updateState(evt)
  }

  def updateState(evt: Evt): Unit = evt match {
    case MsgSent(s) ⇒
      deliver(destination)(deliveryId ⇒ Msg(deliveryId, s))

    case MsgConfirmed(deliveryId) ⇒ confirmDelivery(deliveryId)
  }
}

class MyDestination extends Actor {
  def receive = {
    case Msg(deliveryId, s) ⇒
      // ...
      sender() ! Confirm(deliveryId)
  }
}
```
- The deliveryId generated by the persistence module is a strictly monotonically increasing sequence number without gaps.
- The same sequence is used for all destinations of the actor,  
    - i.e. when sending to multiple destinations the destinations will see gaps in the sequence.
- It is not possible to use custom deliveryId.
- However, you can send a custom correlation identifier in the message to the destination.
- You must then retain a mapping between  
    - the internal deliveryId (passed into the deliveryIdToMessage function)  
    - and your custom correlation id (passed into the message).
- You can do this by storing such mapping in a Map(correlationId -> deliveryId)  
    - from which you can retrieve the deliveryId to be passed into the confirmDelivery method  
    - once the receiver of your message has replied with your custom correlation id.
- The AtLeastOnceDelivery trait has a state consisting of unconfirmed messages and a sequence number.
- It does not store this state itself.
- You must persist events corresponding to the deliver and confirmDelivery invocations from your PersistentActor  
    - so that the state can be restored by calling the same methods during the recovery phase of the PersistentActor.
- Sometimes these events can be derived from other business level events, and sometimes you must create separate events.
- During recovery, calls to deliver will not send out messages,  
    - those will be sent later if no matching confirmDelivery will have been performed.
- Support for snapshots is provided by getDeliverySnapshot and setDeliverySnapshot.
- The AtLeastOnceDeliverySnapshot contains the full delivery state, including unconfirmed messages.
- If you need a custom snapshot for other parts of the actor state you must also include the AtLeastOnceDeliverySnapshot.
- It is serialized using protobuf with the ordinary Akka serialization mechanism.
- It is easiest to include the bytes of the AtLeastOnceDeliverySnapshot as a blob in your custom snapshot.
- The interval between redelivery attempts is defined by the redeliverInterval method.
- The default value can be configured with the akka.persistence.at-least-once-delivery.redeliver-interval configuration key.
- The method can be overridden by implementation classes to return non-default values.
- The maximum number of messages that will be sent at each redelivery burst is defined by  
    - the redeliveryBurstLimit method (burst frequency is half of the redelivery interval).
- If there’s a lot of unconfirmed messages (e.g. if the destination is not available for a long time),  
    - this helps to prevent an overwhelming amount of messages to be sent at once.
- The default value can be configured with the akka.persistence.at-least-once-delivery.redelivery-burst-limit configuration key.
- The method can be overridden by implementation classes to return non-default values.
- After a number of delivery attempts a AtLeastOnceDelivery.UnconfirmedWarning message will be sent to self.
- The re-sending will still continue, but you can choose to call confirmDelivery to cancel the re-sending.
- The number of delivery attempts before emitting the warning is defined by the warnAfterNumberOfUnconfirmedAttempts method.
- The default value can be configured with the akka.persistence.at-least-once-delivery.warn-after-number-of-unconfirmed-attempts configuration key.
- The method can be overridden by implementation classes to return non-default values.
- The AtLeastOnceDelivery trait holds messages in memory until their successful delivery has been confirmed.
- The maximum number of unconfirmed messages that the actor is allowed to hold in memory  
    - is defined by the maxUnconfirmedMessages method.
- If this limit is exceed the deliver method will not accept more messages  
    - and it will throw AtLeastOnceDelivery.MaxUnconfirmedMessagesExceededException.
- The default value can be configured with the akka.persistence.at-least-once-delivery.max-unconfirmed-messages configuration key.
- The method can be overridden by implementation classes to return non-default values.

# Event Adapters
- In long running projects using event sourcing sometimes the need arises to detach the data model from the domain model completely.

### Event Adapters help in situations where:

#### Version Migrations  
- Existing events stored in Version 1 should be "upcasted" to a new Version 2 representation,  
- and the process of doing so involves actual code, not just changes on the serialization layer.
- For these scenarios the toJournal function is usually an identity function,  
    - however the fromJournal is implemented as v1.Event=>v2.Event,  
    - performing the necessary mapping inside the fromJournal method.
- This technique is sometimes referred to as "upcasting" in other CQRS libraries.

#### Separating Domain and Data models 
- Thanks to EventAdapters it is possible to completely separate the domain model from the model used to persist data in the Journals.
- For example one may want to use case classes in the domain model,  
    - however persist their protocol-buffer (or any other binary serialization format) counter-parts to the Journal.
- A simple toJournal:MyModel=>MyDataModel and fromJournal:MyDataModel=>MyModel adapter can be used to implement this feature.

#### Journal Specialized Data Types
- Exposing data types understood by the underlying Journal,  
    - for example for data stores which understand JSON  
    - it is possible to write an EventAdapter toJournal:Any=>JSON such that the Journal can directly store the json  
    - instead of serializing the object to its binary representation.
##

- Implementing an EventAdapter is rather straight forward:
```scala
class MyEventAdapter(system: ExtendedActorSystem) extends EventAdapter {
  override def manifest(event: Any): String =
    "" // when no manifest needed, return ""

  override def toJournal(event: Any): Any =
    event // identity

  override def fromJournal(event: Any, manifest: String): EventSeq =
    EventSeq.single(event) // identity
}
```
- Then in order for it to be used on events coming to and from the journal you must bind it using the below configuration syntax:
```hocon
akka.persistence.journal {
  inmem {
    event-adapters {
      tagging        = "docs.persistence.MyTaggingEventAdapter"
      user-upcasting = "docs.persistence.UserUpcastingEventAdapter"
      item-upcasting = "docs.persistence.ItemUpcastingEventAdapter"
    }

    event-adapter-bindings {
      "docs.persistence.Item"        = tagging
      "docs.persistence.TaggedEvent" = tagging
      "docs.persistence.v1.Event"    = [user-upcasting, item-upcasting]
    }
  }
}
```
- It is possible to bind multiple adapters to one class for recovery,  
    - in which case the fromJournal methods of all bound adapters will be applied to a given matching event  
    - (in order of definition in the configuration).
- Since each adapter may return from 0 to n adapted events (called as EventSeq),  
    - each adapter can investigate the event and if it should indeed adapt it return the adapted event(s) for it.
- Other adapters which do not have anything to contribute during this adaptation simply return EventSeq.empty.
- The adapted events are then delivered in-order to the PersistentActor during replay.

#### Note
- For more advanced schema evolution techniques refer to the Persistence - Schema Evolution documentation.

# Persistent FSM
- PersistentFSM handles the incoming messages in an FSM like fashion.
- Its internal state is persisted as a sequence of changes, later referred to as domain events.
- Relationship between incoming messages, FSM’s states and transitions, persistence of domain events is defined by a DSL.

## A Simple Example
- To demonstrate the features of the PersistentFSM trait, consider an actor which represents a Web store customer.
- The contract of our "WebStoreCustomerFSMActor" is that it accepts the following commands:
```scala
sealed trait Command
case class AddItem(item: Item) extends Command
case object Buy extends Command
case object Leave extends Command
case object GetCurrentCart extends Command
```
- **AddItem**: sent when the customer adds an item to a shopping cart.
- **Buy**: when the customer finishes the purchase.
- **Leave**: when the customer leaves the store without purchasing anything.
- **GetCurrentCart**: allows to query the current state of customer’s shopping cart.
##
- The customer can be in one of the following states:
```scala
sealed trait UserState extends FSMState
case object LookingAround extends UserState {
  override def identifier: String = "Looking Around"
}
case object Shopping extends UserState {
  override def identifier: String = "Shopping"
}
case object Inactive extends UserState {
  override def identifier: String = "Inactive"
}
case object Paid extends UserState {
  override def identifier: String = "Paid"
}
```
- LookingAround customer is browsing the site, but hasn’t added anything to the shopping cart.
- Shopping customer has recently added items to the shopping cart.
- Inactive customer has items in the shopping cart, but hasn’t added anything recently.
- Paid customer has purchased the items.

#### Note
- PersistentFSM states must inherit from trait PersistentFSM.FSMState and implement the def identifier: String method.
- This is required in order to simplify the serialization of FSM states.
- String identifiers should be unique!
- Customer’s actions are "recorded" as a sequence of "domain events" which are persisted.
- Those events are replayed on an actor’s start in order to restore the latest customer’s state:
```scala
sealed trait DomainEvent
case class ItemAdded(item: Item) extends DomainEvent
case object OrderExecuted extends DomainEvent
case object OrderDiscarded extends DomainEvent
```
- Customer state data represents the items in a customer’s shopping cart:
```scala
case class Item(id: String, name: String, price: Float)

sealed trait ShoppingCart {
  def addItem(item: Item): ShoppingCart
  def empty(): ShoppingCart
}
case object EmptyShoppingCart extends ShoppingCart {
  def addItem(item: Item) = NonEmptyShoppingCart(item :: Nil)
  def empty() = this
}
case class NonEmptyShoppingCart(items: Seq[Item]) extends ShoppingCart {
  def addItem(item: Item) = NonEmptyShoppingCart(items :+ item)
  def empty() = EmptyShoppingCart
}
```
- Here is how everything is wired together:
```scala
startWith(LookingAround, EmptyShoppingCart)

when(LookingAround) {
  case Event(AddItem(item), _) ⇒
    goto(Shopping) applying ItemAdded(item) forMax (1 seconds)
  case Event(GetCurrentCart, data) ⇒
    stay replying data
}

when(Shopping) {
  case Event(AddItem(item), _) ⇒
    stay applying ItemAdded(item) forMax (1 seconds)
  case Event(Buy, _) ⇒
    goto(Paid) applying OrderExecuted andThen {
      case NonEmptyShoppingCart(items) ⇒
        reportActor ! PurchaseWasMade(items)
        saveStateSnapshot()
      case EmptyShoppingCart ⇒ saveStateSnapshot()
    }
  case Event(Leave, _) ⇒
    stop applying OrderDiscarded andThen {
      case _ ⇒
        reportActor ! ShoppingCardDiscarded
        saveStateSnapshot()
    }
  case Event(GetCurrentCart, data) ⇒
    stay replying data
  case Event(StateTimeout, _) ⇒
    goto(Inactive) forMax (2 seconds)
}

when(Inactive) {
  case Event(AddItem(item), _) ⇒
    goto(Shopping) applying ItemAdded(item) forMax (1 seconds)
  case Event(StateTimeout, _) ⇒
    stop applying OrderDiscarded andThen {
      case _ ⇒ reportActor ! ShoppingCardDiscarded
    }
}

when(Paid) {
  case Event(Leave, _) ⇒ stop()
  case Event(GetCurrentCart, data) ⇒
    stay replying data
}
```

#### Note
- State data can only be modified directly on initialization.
- Later it’s modified only as a result of applying domain events.
- Override the applyEvent method to define how state data is affected by domain events, see the example below
```scala
override def applyEvent(event: DomainEvent, cartBeforeEvent: ShoppingCart): ShoppingCart = {
  event match {
    case ItemAdded(item) ⇒ cartBeforeEvent.addItem(item)
    case OrderExecuted   ⇒ cartBeforeEvent
    case OrderDiscarded  ⇒ cartBeforeEvent.empty()
  }
}
```
- andThen can be used to define actions which will be executed following event’s persistence 
    - convenient for "side effects" like sending a message or logging.
- Notice that actions defined in andThen block are not executed on recovery:
```scala
goto(Paid) applying OrderExecuted andThen {
  case NonEmptyShoppingCart(items) ⇒
    reportActor ! PurchaseWasMade(items)
}
```
- A snapshot of state data can be persisted by calling the saveStateSnapshot() method:
```scala
stop applying OrderDiscarded andThen {
  case _ ⇒
    reportActor ! ShoppingCardDiscarded
    saveStateSnapshot()
}
```
- On recovery state data is initialized according to the latest available snapshot,  
    - then the remaining domain events are replayed, triggering the applyEvent method.

# Periodical snapshot by snapshot-after
- You can enable periodical saveStateSnapshot() calls in PersistentFSM if you turn the following flag on in reference.conf
```hocon
akka.persistence.fsm.snapshot-after = 1000
```
- this means saveStateSnapshot() is called after the sequence number reaches multiple of 1000.
#### Note
- saveStateSnapshot() might not be called exactly at sequence numbers being multiple of the snapshot-after configuration value.
- This is because PersistentFSM works in a sort of "batch" mode when processing and persisting events,  
    - and saveStateSnapshot() is called only at the end of the "batch".
- For example, if you set akka.persistence.fsm.snapshot-after = 1000,  
    - it is possible that saveStateSnapshot() is called at `lastSequenceNr = 1005, 2003, ...`
- A single batch might persist state transition,  
    - also there could be multiple domain events to be persisted if you pass them to applying method in the PersistFSM DSL.

# Storage plugins
- Storage backends for journals and snapshot stores are pluggable in the Akka persistence extension.
- A directory of persistence journal and snapshot store plugins is available at the Akka Community Projects page,  
    - see Community plugins
- Plugins can be selected either by "default" for all persistent actors, or "individually",  
    - when a persistent actor defines its own set of plugins.
- When a persistent actor does NOT override the journalPluginId and snapshotPluginId methods,  
    - the persistence extension will use the "default" journal and snapshot-store plugins configured in reference.conf:
```hocon
akka.persistence.journal.plugin = ""
akka.persistence.snapshot-store.plugin = ""
```
- However, these entries are provided as empty "", and require explicit user configuration via override  
    - in the user application.conf.
- For an example of a journal plugin which writes messages to LevelDB see Local LevelDB journal.
- For an example of a snapshot store plugin which writes snapshots as individual files to the local filesystem  
    - see Local snapshot store.
- Applications can provide their own plugins by implementing a plugin API and activating them by configuration.
- Plugin development requires the following imports:
```scala
import akka.persistence._
import akka.persistence.journal._
import akka.persistence.snapshot._
```

## Eager initialization of persistence plugin
- By default, persistence plugins are started on-demand, as they are used.
- In some case, however, it might be beneficial to start a certain plugin eagerly.
- In order to do that, you should first add akka.persistence.Persistence under the akka.extensions key.
- Then, specify the IDs of plugins you wish to start automatically  
    - under akka.persistence.journal.auto-start-journals  
    - and akka.persistence.snapshot-store.auto-start-snapshot-stores.
- For example, if you want eager initialization for the leveldb journal plugin and the local snapshot store plugin,  
    - your configuration should look like this: 
```hocon
akka {

  extensions = [akka.persistence.Persistence]

  persistence {

    journal {
      plugin = "akka.persistence.journal.leveldb"
      auto-start-journals = ["akka.persistence.journal.leveldb"]
    }

    snapshot-store {
      plugin = "akka.persistence.snapshot-store.local"
      auto-start-snapshot-stores = ["akka.persistence.snapshot-store.local"]
    }
  
  }

}
```

## Journal plugin API
- A journal plugin extends AsyncWriteJournal.
- AsyncWriteJournal is an actor and the methods to be implemented are:
```scala
/**
 * Plugin API: asynchronously writes a batch (`Seq`) of persistent messages to the
 * journal.
 *
 * The batch is only for performance reasons, i.e. all messages don't have to be written
 * atomically. Higher throughput can typically be achieved by using batch inserts of many
 * records compared to inserting records one-by-one, but this aspect depends on the
 * underlying data store and a journal implementation can implement it as efficient as
 * possible. Journals should aim to persist events in-order for a given `persistenceId`
 * as otherwise in case of a failure, the persistent state may be end up being inconsistent.
 *
 * Each `AtomicWrite` message contains the single `PersistentRepr` that corresponds to
 * the event that was passed to the `persist` method of the `PersistentActor`, or it
 * contains several `PersistentRepr` that corresponds to the events that were passed
 * to the `persistAll` method of the `PersistentActor`. All `PersistentRepr` of the
 * `AtomicWrite` must be written to the data store atomically, i.e. all or none must
 * be stored. If the journal (data store) cannot support atomic writes of multiple
 * events it should reject such writes with a `Try` `Failure` with an
 * `UnsupportedOperationException` describing the issue. This limitation should
 * also be documented by the journal plugin.
 *
 * If there are failures when storing any of the messages in the batch the returned
 * `Future` must be completed with failure. The `Future` must only be completed with
 * success when all messages in the batch have been confirmed to be stored successfully,
 * i.e. they will be readable, and visible, in a subsequent replay. If there is
 * uncertainty about if the messages were stored or not the `Future` must be completed
 * with failure.
 *
 * Data store connection problems must be signaled by completing the `Future` with
 * failure.
 *
 * The journal can also signal that it rejects individual messages (`AtomicWrite`) by
 * the returned `immutable.Seq[Try[Unit]]`. It is possible but not mandatory to reduce
 * number of allocations by returning `Future.successful(Nil)` for the happy path,
 * i.e. when no messages are rejected. Otherwise the returned `Seq` must have as many elements
 * as the input `messages` `Seq`. Each `Try` element signals if the corresponding
 * `AtomicWrite` is rejected or not, with an exception describing the problem. Rejecting
 * a message means it was not stored, i.e. it must not be included in a later replay.
 * Rejecting a message is typically done before attempting to store it, e.g. because of
 * serialization error.
 *
 * Data store connection problems must not be signaled as rejections.
 *
 * It is possible but not mandatory to reduce number of allocations by returning
 * `Future.successful(Nil)` for the happy path, i.e. when no messages are rejected.
 *
 * Calls to this method are serialized by the enclosing journal actor. If you spawn
 * work in asynchronous tasks it is alright that they complete the futures in any order,
 * but the actual writes for a specific persistenceId should be serialized to avoid
 * issues such as events of a later write are visible to consumers (query side, or replay)
 * before the events of an earlier write are visible.
 * A PersistentActor will not send a new WriteMessages request before the previous one
 * has been completed.
 *
 * Please note that the `sender` field of the contained PersistentRepr objects has been
 * nulled out (i.e. set to `ActorRef.noSender`) in order to not use space in the journal
 * for a sender reference that will likely be obsolete during replay.
 *
 * Please also note that requests for the highest sequence number may be made concurrently
 * to this call executing for the same `persistenceId`, in particular it is possible that
 * a restarting actor tries to recover before its outstanding writes have completed. In
 * the latter case it is highly desirable to defer reading the highest sequence number
 * until all outstanding writes have completed, otherwise the PersistentActor may reuse
 * sequence numbers.
 *
 * This call is protected with a circuit-breaker.
 */
def asyncWriteMessages(messages: immutable.Seq[AtomicWrite]): Future[immutable.Seq[Try[Unit]]]

/**
 * Plugin API: asynchronously deletes all persistent messages up to `toSequenceNr`
 * (inclusive).
 *
 * This call is protected with a circuit-breaker.
 * Message deletion doesn't affect the highest sequence number of messages, journal must maintain the highest sequence number and never decrease it.
 */
def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long): Future[Unit]

/**
 * Plugin API
 *
 * Allows plugin implementers to use `f pipeTo self` and
 * handle additional messages for implementing advanced features
 *
 */
def receivePluginInternal: Actor.Receive = Actor.emptyBehavior
```
- If the storage backend API only supports synchronous, blocking writes, the methods should be implemented as:
```scala
def asyncWriteMessages(messages: immutable.Seq[AtomicWrite]): Future[immutable.Seq[Try[Unit]]] =
  Future.fromTry(Try {
    // blocking call here
    ???
  })
```
- A journal plugin must also implement the methods defined in AsyncRecovery for replays and sequence number recovery:
```scala
/**
 * Plugin API: asynchronously replays persistent messages. Implementations replay
 * a message by calling `replayCallback`. The returned future must be completed
 * when all messages (matching the sequence number bounds) have been replayed.
 * The future must be completed with a failure if any of the persistent messages
 * could not be replayed.
 *
 * The `replayCallback` must also be called with messages that have been marked
 * as deleted. In this case a replayed message's `deleted` method must return
 * `true`.
 *
 * The `toSequenceNr` is the lowest of what was returned by [[#asyncReadHighestSequenceNr]]
 * and what the user specified as recovery [[akka.persistence.Recovery]] parameter.
 * This does imply that this call is always preceded by reading the highest sequence
 * number for the given `persistenceId`.
 *
 * This call is NOT protected with a circuit-breaker because it may take long time
 * to replay all events. The plugin implementation itself must protect against
 * an unresponsive backend store and make sure that the returned Future is
 * completed with success or failure within reasonable time. It is not allowed
 * to ignore completing the future.
 *
 * @param persistenceId persistent actor id.
 * @param fromSequenceNr sequence number where replay should start (inclusive).
 * @param toSequenceNr sequence number where replay should end (inclusive).
 * @param max maximum number of messages to be replayed.
 * @param recoveryCallback called to replay a single message. Can be called from any
 *                       thread.
 *
 * @see [[AsyncWriteJournal]]
 */
def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long,
                        max: Long)(recoveryCallback: PersistentRepr ⇒ Unit): Future[Unit]

/**
 * Plugin API: asynchronously reads the highest stored sequence number for the
 * given `persistenceId`. The persistent actor will use the highest sequence
 * number after recovery as the starting point when persisting new events.
 * This sequence number is also used as `toSequenceNr` in subsequent call
 * to [[#asyncReplayMessages]] unless the user has specified a lower `toSequenceNr`.
 * Journal must maintain the highest sequence number and never decrease it.
 *
 * This call is protected with a circuit-breaker.
 *
 * Please also note that requests for the highest sequence number may be made concurrently
 * to writes executing for the same `persistenceId`, in particular it is possible that
 * a restarting actor tries to recover before its outstanding writes have completed.
 *
 * @param persistenceId persistent actor id.
 * @param fromSequenceNr hint where to start searching for the highest sequence
 *                       number. When a persistent actor is recovering this
 *                       `fromSequenceNr` will be the sequence number of the used
 *                       snapshot or `0L` if no snapshot is used.
 */
def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long]
```
- A journal plugin can be activated with the following minimal configuration:
```hocon
# Path to the journal plugin to be used
akka.persistence.journal.plugin = "my-journal"

# My custom journal plugin
my-journal {
  # Class name of the plugin.
  class = "docs.persistence.MyJournal"
  # Dispatcher for the plugin actor.
  plugin-dispatcher = "akka.actor.default-dispatcher"
}
```
- The journal plugin instance is an actor  
    - so the methods corresponding to requests from persistent actors are executed sequentially.
- It may delegate to asynchronous libraries, spawn futures, or delegate to other actors to achieve parallelism.
- The journal plugin class must have a constructor with one of these signatures:
    - constructor with one com.typesafe.config.Config parameter and a String parameter for the config path
    - constructor with one com.typesafe.config.Config parameter
    - constructor without parameters
- The plugin section of the actor system’s config will be passed in the config constructor parameter.
- The config path of the plugin is passed in the String parameter.
- The plugin-dispatcher is the dispatcher used for the plugin actor.
- If not specified, it defaults to akka.persistence.dispatchers.default-plugin-dispatcher.
- Don’t run journal tasks/futures on the system default dispatcher, since that might starve other tasks.

## Snapshot store plugin API
- A snapshot store plugin must extend the SnapshotStore actor and implement the following methods:
```scala
/**
 * Plugin API: asynchronously loads a snapshot.
 *
 * If the future `Option` is `None` then all events will be replayed,
 * i.e. there was no snapshot. If snapshot could not be loaded the `Future`
 * should be completed with failure. That is important because events may
 * have been deleted and just replaying the events might not result in a valid
 * state.
 *
 * This call is protected with a circuit-breaker.
 *
 * @param persistenceId id of the persistent actor.
 * @param criteria selection criteria for loading.
 */
def loadAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Option[SelectedSnapshot]]

/**
 * Plugin API: asynchronously saves a snapshot.
 *
 * This call is protected with a circuit-breaker.
 *
 * @param metadata snapshot metadata.
 * @param snapshot snapshot.
 */
def saveAsync(metadata: SnapshotMetadata, snapshot: Any): Future[Unit]

/**
 * Plugin API: deletes the snapshot identified by `metadata`.
 *
 * This call is protected with a circuit-breaker.
 *
 * @param metadata snapshot metadata.
 */
def deleteAsync(metadata: SnapshotMetadata): Future[Unit]

/**
 * Plugin API: deletes all snapshots matching `criteria`.
 *
 * This call is protected with a circuit-breaker.
 *
 * @param persistenceId id of the persistent actor.
 * @param criteria selection criteria for deleting.
 */
def deleteAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Unit]

/**
 * Plugin API
 * Allows plugin implementers to use `f pipeTo self` and
 * handle additional messages for implementing advanced features
 */
def receivePluginInternal: Actor.Receive = Actor.emptyBehavior
```
- A snapshot store plugin can be activated with the following minimal configuration:
```hocon
# Path to the snapshot store plugin to be used
akka.persistence.snapshot-store.plugin = "my-snapshot-store"

# My custom snapshot store plugin
my-snapshot-store {
  # Class name of the plugin.
  class = "docs.persistence.MySnapshotStore"
  # Dispatcher for the plugin actor.
  plugin-dispatcher = "akka.persistence.dispatchers.default-plugin-dispatcher"
}
```
- The snapshot store instance is an actor  
    - so the methods corresponding to requests from persistent actors are executed sequentially.
- It may delegate to asynchronous libraries, spawn futures, or delegate to other actors to achive parallelism.
- The snapshot store plugin class must have a constructor with one of these signatures:
    - constructor with one com.typesafe.config.Config parameter and a String parameter for the config path
    - constructor with one com.typesafe.config.Config parameter
    - constructor without parameters
- The plugin section of the actor system’s config will be passed in the config constructor parameter.
- The config path of the plugin is passed in the String parameter.
- The plugin-dispatcher is the dispatcher used for the plugin actor.
- If not specified, it defaults to akka.persistence.dispatchers.default-plugin-dispatcher.
- Don’t run snapshot store tasks/futures on the system default dispatcher, since that might starve other tasks.

## Plugin TCK
- In order to help developers build correct and high quality storage plugins,  
    - we provide a Technology Compatibility Kit (TCK for short).
- The TCK is usable from Java as well as Scala projects.
- To test your implementation (independently of language) you need to include the akka-persistence-tck dependency:
```sbtshell
"com.typesafe.akka" %% "akka-persistence-tck" % "2.5.9" % "test"
```
- To include the Journal TCK tests in your test suite simply extend the provided JournalSpec:
```scala
class MyJournalSpec extends JournalSpec(
  config = ConfigFactory.parseString(
    """akka.persistence.journal.plugin = "my.journal.plugin"""")) {

  override def supportsRejectingNonSerializableObjects: CapabilityFlag =
    false // or CapabilityFlag.off
}
```
- Please note that some of the tests are optional, and by overriding the supports... methods  
    - you give the TCK the needed information about which tests to run.
- You can implement these methods using boolean values or the provided CapabilityFlag.on / CapabilityFlag.off values.
- We also provide a simple benchmarking class JournalPerfSpec which includes all the tests that JournalSpec has,  
    - and also performs some longer operations on the Journal while printing its performance stats.
- While it is NOT aimed to provide a proper benchmarking environment  
    - it can be used to get a rough feel about your journal’s performance in the most typical scenarios.
- In order to include the SnapshotStore TCK tests in your test suite simply extend the SnapshotStoreSpec:
```scala
class MySnapshotStoreSpec extends SnapshotStoreSpec(
  config = ConfigFactory.parseString(
    """
    akka.persistence.snapshot-store.plugin = "my.snapshot-store.plugin"
    """))
```
- In case your plugin requires some setting up (starting a mock database, removing temporary files etc.)  
    - you can override the beforeAll and afterAll methods to hook into the tests lifecycle:
```scala
class MyJournalSpec extends JournalSpec(
  config = ConfigFactory.parseString(
    """
    akka.persistence.journal.plugin = "my.journal.plugin"
    """)) {

  override def supportsRejectingNonSerializableObjects: CapabilityFlag =
    true // or CapabilityFlag.on

  val storageLocations = List(
    new File(system.settings.config.getString("akka.persistence.journal.leveldb.dir")),
    new File(config.getString("akka.persistence.snapshot-store.local.dir")))

  override def beforeAll() {
    super.beforeAll()
    storageLocations foreach FileUtils.deleteRecursively
  }

  override def afterAll() {
    storageLocations foreach FileUtils.deleteRecursively
    super.afterAll()
  }

}
```
- We highly recommend including these specifications in your test suite,  
    - as they cover a broad range of cases you might have otherwise forgotten to test for  
    - when writing a plugin from scratch.

# Pre-packaged plugins

## Local LevelDB journal
- The LevelDB journal plugin config entry is akka.persistence.journal.leveldb.
- It writes messages to a local LevelDB instance.
- Enable this plugin by defining config property:
```hocon
# Path to the journal plugin to be used
akka.persistence.journal.plugin = "akka.persistence.journal.leveldb"
```
- LevelDB based plugins will also require the following additional dependency declaration:
```sbtshell
"org.fusesource.leveldbjni"   % "leveldbjni-all"   % "1.8"
```
- The default location of LevelDB files is a directory named journal in the current working directory.
- This location can be changed by configuration where the specified path can be relative or absolute:
```hocon
akka.persistence.journal.leveldb.dir = "target/journal"
```
- With this plugin, each actor system runs its own private LevelDB instance.
- One peculiarity of LevelDB is that the deletion operation does not remove messages from the journal,  
    - but adds a "tombstone" for each deleted message instead.
- In the case of heavy journal usage, especially one including frequent deletes,  
    - this may be an issue as users may find themselves dealing with continuously increasing journal sizes.
- To this end, LevelDB offers a special journal compaction function that is exposed via the following configuration:
```hocon
# Number of deleted messages per persistence id that will trigger journal compaction
akka.persistence.journal.leveldb.compaction-intervals {
  persistence-id-1 = 100
  persistence-id-2 = 200
  # ...
  persistence-id-N = 1000
  # use wildcards to match unspecified persistence ids, if any
  "*" = 250
}
```

## Shared LevelDB journal
- A LevelDB instance can also be shared by multiple actor systems (on the same or on different nodes).
- This, for example, allows persistent actors to failover to a backup node  
    - and continue using the shared journal instance from the backup node.

#### Warning
- A shared LevelDB instance is a single point of failure and should therefore only be used for testing purposes.
- Highly-available, replicated journals are available as Community plugins.

#### Note
- This plugin has been supplanted by Persistence Plugin Proxy.
##

- A shared LevelDB instance is started by instantiating the SharedLeveldbStore actor.
```scala
import akka.persistence.journal.leveldb.SharedLeveldbStore

val store = system.actorOf(Props[SharedLeveldbStore], "store")
```
- By default, the shared instance writes journaled messages to a local directory named journal in the current working directory.
- The storage location can be changed by configuration:
```hocon
akka.persistence.journal.leveldb-shared.store.dir = "target/shared"
```
- Actor systems that use a shared LevelDB store must activate the akka.persistence.journal.leveldb-shared plugin.
```hocon
akka.persistence.journal.plugin = "akka.persistence.journal.leveldb-shared"
```
- This plugin must be initialized by injecting the (remote) SharedLeveldbStore actor reference.
- Injection is done by calling the SharedLeveldbJournal.setStore method with the actor reference as argument.
```scala
trait SharedStoreUsage extends Actor {
  override def preStart(): Unit = {
    context.actorSelection("akka.tcp://example@127.0.0.1:2552/user/store") ! Identify(1)
  }

  def receive = {
    case ActorIdentity(1, Some(store)) ⇒
      SharedLeveldbJournal.setStore(store, context.system)
  }
}
```
- Internal journal commands (sent by persistent actors) are buffered until injection completes.
- Injection is idempotent i.e. only the first injection is used.

## Local snapshot store
- The local snapshot store plugin config entry is akka.persistence.snapshot-store.local.
- It writes snapshot files to the local filesystem.
- Enable this plugin by defining config property:
```hocon
# Path to the snapshot store plugin to be used
akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
```
- The default storage location is a directory named snapshots in the current working directory.
- This can be changed by configuration where the specified path can be relative or absolute:
```hocon
akka.persistence.snapshot-store.local.dir = "target/snapshots"
```
- Note that it is not mandatory to specify a snapshot store plugin.
- If you don’t use snapshots you don’t have to configure it.

## Persistence Plugin Proxy
- A persistence plugin proxy allows sharing of journals and snapshot stores across multiple actor systems  
    - (on the same or on different nodes).
- This, for example, allows persistent actors to failover to a backup node  
    - and continue using the shared journal instance from the backup node.
- The proxy works by forwarding all the journal/snapshot store messages to a single, shared, persistence plugin instance,  
    - and therefore supports any use case supported by the proxied plugin.

#### Warning
- A shared journal/snapshot store is a single point of failure and should therefore only be used for testing purposes.
- Highly-available, replicated persistence plugins are available as Community plugins.
##

- The journal and snapshot store proxies are controlled via  
    - the akka.persistence.journal.proxy  
    - and akka.persistence.snapshot-store.proxy configuration entries, respectively.
- Set the target-journal-plugin or target-snapshot-store-plugin keys to the underlying plugin you wish to use  
    - (for example: akka.persistence.journal.leveldb).
- The start-target-journal and start-target-snapshot-store keys should be set to on in exactly one actor system 
    - this is the system that will instantiate the shared persistence plugin.
- Next, the proxy needs to be told how to find the shared plugin.
- This can be done by setting the target-journal-address and target-snapshot-store-address configuration keys,  
    - or programmatically by calling the PersistencePluginProxy.setTargetLocation method.

#### Note
- Akka starts extensions lazily when they are required, and this includes the proxy.
- This means that in order for the proxy to work, the persistence plugin on the target node must be instantiated.
- This can be done by instantiating the PersistencePluginProxyExtension extension,  
    - or by calling the PersistencePluginProxy.start method.

#### Note
- The proxied persistence plugin can (and should) be configured using its original configuration keys.

# Custom serialization
- Serialization of snapshots and payloads of Persistent messages is configurable with Akka’s Serialization infrastructure.
- For example, if an application wants to serialize
    - payloads of type MyPayload with a custom MyPayloadSerializer and
    - snapshots of type MySnapshot with a custom MySnapshotSerializer
- it must add
```hocon
akka.actor {
  serializers {
    my-payload = "docs.persistence.MyPayloadSerializer"
    my-snapshot = "docs.persistence.MySnapshotSerializer"
  }
  serialization-bindings {
    "docs.persistence.MyPayload" = my-payload
    "docs.persistence.MySnapshot" = my-snapshot
  }
}
```
- to the application configuration.
- If not specified, a default serializer is used.
- For more advanced schema evolution techniques refer to the Persistence - Schema Evolution documentation.

# Testing
- When running tests with LevelDB default settings in sbt, make sure to set fork := true in your sbt project.
- Otherwise, you’ll see an UnsatisfiedLinkError.
- Alternatively, you can switch to a LevelDB Java port by setting
```hocon
akka.persistence.journal.leveldb.native = off
```
or
```hocon
akka.persistence.journal.leveldb-shared.store.native = off
```
- in your Akka configuration.
- The LevelDB Java port is for testing purposes only.
- Also note that for the LevelDB Java port, you will need the following dependencies:
```sbtshell
"org.iq80.leveldb" % "leveldb" % "0.9" % "test"
```

#### Warning
- It is not possible to test persistence provided classes (i.e. PersistentActor and AtLeastOnceDelivery) using TestActorRef  
    - due to its synchronous nature.
- These traits need to be able to perform asynchronous tasks in the background  
    - in order to handle internal persistence related events.
- When testing Persistence based projects always rely on asynchronous messaging using the TestKit.

# Configuration
- There are several configuration properties for the persistence module, please refer to the reference configuration.

# Multiple persistence plugin configurations
- By default, a persistent actor will use the "default" journal and snapshot store plugins  
    - configured in the following sections of the reference.conf configuration resource:
```hocon
# Absolute path to the default journal plugin configuration entry.
akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
# Absolute path to the default snapshot store plugin configuration entry.
akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
```
- Note that in this case the actor overrides only the persistenceId method:
```scala
trait ActorWithDefaultPlugins extends PersistentActor {
  override def persistenceId = "123"
}
```
- When the persistent actor overrides the journalPluginId and snapshotPluginId methods,  
    - the actor will be serviced by these specific persistence plugins instead of the defaults:
```scala
trait ActorWithOverridePlugins extends PersistentActor {
  override def persistenceId = "123"
  // Absolute path to the journal plugin configuration entry in the `reference.conf`.
  override def journalPluginId = "akka.persistence.chronicle.journal"
  // Absolute path to the snapshot store plugin configuration entry in the `reference.conf`.
  override def snapshotPluginId = "akka.persistence.chronicle.snapshot-store"
}
```
- Note that journalPluginId and snapshotPluginId must refer to properly configured reference.conf plugin entries  
    - with a standard class property as well as settings which are specific for those plugins, i.e.:
```hocon
# Configuration entry for the custom journal plugin, see `journalPluginId`.
akka.persistence.chronicle.journal {
  # Standard persistence extension property: provider FQCN.
  class = "akka.persistence.chronicle.ChronicleSyncJournal"
  # Custom setting specific for the journal `ChronicleSyncJournal`.
  folder = $${user.dir}/store/journal
}
# Configuration entry for the custom snapshot store plugin, see `snapshotPluginId`.
akka.persistence.chronicle.snapshot-store {
  # Standard persistence extension property: provider FQCN.
  class = "akka.persistence.chronicle.ChronicleSnapshotStore"
  # Custom setting specific for the snapshot store `ChronicleSnapshotStore`.
  folder = $${user.dir}/store/snapshot
}
```