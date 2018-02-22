# Event Bus - Overview

Originally conceived as a way to send messages to groups of actors, the EventBus has been generalized into a set of composable traits implementing a simple interface:

Scala

    /**
     * Attempts to register the subscriber to the specified Classifier
     * @return true if successful and false if not (because it was already
     *   subscribed to that Classifier, or otherwise)
     */
    def subscribe(subscriber: Subscriber, to: Classifier): Boolean

    /**
     * Attempts to deregister the subscriber from the specified Classifier
     * @return true if successful and false if not (because it wasn't subscribed
     *   to that Classifier, or otherwise)
     */
    def unsubscribe(subscriber: Subscriber, from: Classifier): Boolean

    /**
     * Attempts to deregister the subscriber from all Classifiers it may be subscribed to
     */
    def unsubscribe(subscriber: Subscriber): Unit

    /**
     * Publishes the specified Event to this bus
     */
    def publish(event: Event): Unit

Java

Note

Please note that the EventBus does not preserve the sender of the published messages. If you need a reference to the original sender you have to provide it inside the message.

This mechanism is used in different places within Akka, e.g. the Event Stream. Implementations can make use of the specific building blocks presented below.

An event bus must define the following three abstract types:

    Event is the type of all events published on that bus
    Subscriber is the type of subscribers allowed to register on that event bus
    Classifier defines the classifier to be used in selecting subscribers for dispatching events

The traits below are still generic in these types, but they need to be defined for any concrete implementation.

# Classifiers

The classifiers presented here are part of the Akka distribution, but rolling your own in case you do not find a perfect match is not difficult, check the implementation of the existing ones on github
Lookup Classification

The simplest classification is just to extract an arbitrary classifier from each event and maintaining a set of subscribers for each possible classifier. This can be compared to tuning in on a radio station. The trait LookupClassification is still generic in that it abstracts over how to compare subscribers and how exactly to classify.

The necessary methods to be implemented are illustrated with the following example:

Scala

    import akka.event.EventBus
    import akka.event.LookupClassification

    final case class MsgEnvelope(topic: String, payload: Any)

    /**
     * Publishes the payload of the MsgEnvelope when the topic of the
     * MsgEnvelope equals the String specified when subscribing.
     */
    class LookupBusImpl extends EventBus with LookupClassification {
      type Event = MsgEnvelope
      type Classifier = String
      type Subscriber = ActorRef

      // is used for extracting the classifier from the incoming events
      override protected def classify(event: Event): Classifier = event.topic

      // will be invoked for each event for all subscribers which registered themselves
      // for the event’s classifier
      override protected def publish(event: Event, subscriber: Subscriber): Unit = {
        subscriber ! event.payload
      }

      // must define a full order over the subscribers, expressed as expected from
      // `java.lang.Comparable.compare`
      override protected def compareSubscribers(a: Subscriber, b: Subscriber): Int =
        a.compareTo(b)

      // determines the initial size of the index data structure
      // used internally (i.e. the expected number of different classifiers)
      override protected def mapSize: Int = 128

    }

Java

A test for this implementation may look like this:

Scala

    val lookupBus = new LookupBusImpl
    lookupBus.subscribe(testActor, "greetings")
    lookupBus.publish(MsgEnvelope("time", System.currentTimeMillis()))
    lookupBus.publish(MsgEnvelope("greetings", "hello"))
    expectMsg("hello")

Java

This classifier is efficient in case no subscribers exist for a particular event.
Subchannel Classification

If classifiers form a hierarchy and it is desired that subscription be possible not only at the leaf nodes, this classification may be just the right one. It can be compared to tuning in on (possibly multiple) radio channels by genre. This classification has been developed for the case where the classifier is just the JVM class of the event and subscribers may be interested in subscribing to all subclasses of a certain class, but it may be used with any classifier hierarchy.

The necessary methods to be implemented are illustrated with the following example:

Scala

    import akka.util.Subclassification

    class StartsWithSubclassification extends Subclassification[String] {
      override def isEqual(x: String, y: String): Boolean =
        x == y

      override def isSubclass(x: String, y: String): Boolean =
        x.startsWith(y)
    }

    import akka.event.SubchannelClassification

    /**
     * Publishes the payload of the MsgEnvelope when the topic of the
     * MsgEnvelope starts with the String specified when subscribing.
     */
    class SubchannelBusImpl extends EventBus with SubchannelClassification {
      type Event = MsgEnvelope
      type Classifier = String
      type Subscriber = ActorRef

      // Subclassification is an object providing `isEqual` and `isSubclass`
      // to be consumed by the other methods of this classifier
      override protected val subclassification: Subclassification[Classifier] =
        new StartsWithSubclassification

      // is used for extracting the classifier from the incoming events
      override protected def classify(event: Event): Classifier = event.topic

      // will be invoked for each event for all subscribers which registered
      // themselves for the event’s classifier
      override protected def publish(event: Event, subscriber: Subscriber): Unit = {
        subscriber ! event.payload
      }
    }

Java

A test for this implementation may look like this:

Scala

    val subchannelBus = new SubchannelBusImpl
    subchannelBus.subscribe(testActor, "abc")
    subchannelBus.publish(MsgEnvelope("xyzabc", "x"))
    subchannelBus.publish(MsgEnvelope("bcdef", "b"))
    subchannelBus.publish(MsgEnvelope("abc", "c"))
    expectMsg("c")
    subchannelBus.publish(MsgEnvelope("abcdef", "d"))
    expectMsg("d")

Java

This classifier is also efficient in case no subscribers are found for an event, but it uses conventional locking to synchronize an internal classifier cache, hence it is not well-suited to use cases in which subscriptions change with very high frequency (keep in mind that “opening” a classifier by sending the first message will also have to re-check all previous subscriptions).
Scanning Classification

The previous classifier was built for multi-classifier subscriptions which are strictly hierarchical, this classifier is useful if there are overlapping classifiers which cover various parts of the event space without forming a hierarchy. It can be compared to tuning in on (possibly multiple) radio stations by geographical reachability (for old-school radio-wave transmission).

The necessary methods to be implemented are illustrated with the following example:

Scala

    import akka.event.ScanningClassification

    /**
     * Publishes String messages with length less than or equal to the length
     * specified when subscribing.
     */
    class ScanningBusImpl extends EventBus with ScanningClassification {
      type Event = String
      type Classifier = Int
      type Subscriber = ActorRef

      // is needed for determining matching classifiers and storing them in an
      // ordered collection
      override protected def compareClassifiers(a: Classifier, b: Classifier): Int =
        if (a < b) -1 else if (a == b) 0 else 1

      // is needed for storing subscribers in an ordered collection
      override protected def compareSubscribers(a: Subscriber, b: Subscriber): Int =
        a.compareTo(b)

      // determines whether a given classifier shall match a given event; it is invoked
      // for each subscription for all received events, hence the name of the classifier
      override protected def matches(classifier: Classifier, event: Event): Boolean =
        event.length <= classifier

      // will be invoked for each event for all subscribers which registered themselves
      // for a classifier matching this event
      override protected def publish(event: Event, subscriber: Subscriber): Unit = {
        subscriber ! event
      }
    }

Java

A test for this implementation may look like this:

Scala

    val scanningBus = new ScanningBusImpl
    scanningBus.subscribe(testActor, 3)
    scanningBus.publish("xyzabc")
    scanningBus.publish("ab")
    expectMsg("ab")
    scanningBus.publish("abc")
    expectMsg("abc")

Java

This classifier takes always a time which is proportional to the number of subscriptions, independent of how many actually match.
Actor Classification

This classification was originally developed specifically for implementing DeathWatch: subscribers as well as classifiers are of type ActorRef.

This classification requires an ActorSystem in order to perform book-keeping operations related to the subscribers being Actors, which can terminate without first unsubscribing from the EventBus. ManagedActorClassification maintains a system Actor which takes care of unsubscribing terminated actors automatically.

The necessary methods to be implemented are illustrated with the following example:

Scala

    import akka.event.ActorEventBus
    import akka.event.ManagedActorClassification
    import akka.event.ActorClassifier

    final case class Notification(ref: ActorRef, id: Int)

    class ActorBusImpl(val system: ActorSystem) extends ActorEventBus with ActorClassifier with ManagedActorClassification {
      type Event = Notification

      // is used for extracting the classifier from the incoming events
      override protected def classify(event: Event): ActorRef = event.ref

      // determines the initial size of the index data structure
      // used internally (i.e. the expected number of different classifiers)
      override protected def mapSize: Int = 128
    }

Java

A test for this implementation may look like this:

Scala

    val observer1 = TestProbe().ref
    val observer2 = TestProbe().ref
    val probe1 = TestProbe()
    val probe2 = TestProbe()
    val subscriber1 = probe1.ref
    val subscriber2 = probe2.ref
    val actorBus = new ActorBusImpl(system)
    actorBus.subscribe(subscriber1, observer1)
    actorBus.subscribe(subscriber2, observer1)
    actorBus.subscribe(subscriber2, observer2)
    actorBus.publish(Notification(observer1, 100))
    probe1.expectMsg(Notification(observer1, 100))
    probe2.expectMsg(Notification(observer1, 100))
    actorBus.publish(Notification(observer2, 101))
    probe2.expectMsg(Notification(observer2, 101))
    probe1.expectNoMsg(500.millis)

Java

This classifier is still is generic in the event type, and it is efficient for all use cases.

# Event Stream

The event stream is the main event bus of each actor system: it is used for carrying log messages and Dead Letters and may be used by the user code for other purposes as well. It uses Subchannel Classification which enables registering to related sets of channels (as is used for RemotingLifecycleEvent). The following example demonstrates how a simple subscription works. Given a simple actor:

import akka.actor.{ Actor, DeadLetter, Props }

class DeadLetterListener extends Actor {
  def receive = {
    case d: DeadLetter ⇒ println(d)
  }
}

val listener = system.actorOf(Props[DeadLetterListener])
system.eventStream.subscribe(listener, classOf[DeadLetter])

It is also worth pointing out that thanks to the way the subchannel classification is implemented in the event stream, it is possible to subscribe to a group of events, by subscribing to their common superclass as demonstrated in the following example:

Scala

    abstract class AllKindsOfMusic { def artist: String }
    case class Jazz(artist: String) extends AllKindsOfMusic
    case class Electronic(artist: String) extends AllKindsOfMusic

    class Listener extends Actor {
      def receive = {
        case m: Jazz       ⇒ println(s"${self.path.name} is listening to: ${m.artist}")
        case m: Electronic ⇒ println(s"${self.path.name} is listening to: ${m.artist}")
      }
    }

    val jazzListener = system.actorOf(Props[Listener])
    val musicListener = system.actorOf(Props[Listener])
    system.eventStream.subscribe(jazzListener, classOf[Jazz])
    system.eventStream.subscribe(musicListener, classOf[AllKindsOfMusic])

    // only musicListener gets this message, since it listens to *all* kinds of music:
    system.eventStream.publish(Electronic("Parov Stelar"))

    // jazzListener and musicListener will be notified about Jazz:
    system.eventStream.publish(Jazz("Sonny Rollins"))

Java

Similarly to Actor Classification, EventStream will automatically remove subscribers when they terminate.
Note

The event stream is a local facility, meaning that it will not distribute events to other nodes in a clustered environment (unless you subscribe a Remote Actor to the stream explicitly). If you need to broadcast events in an Akka cluster, without knowing your recipients explicitly (i.e. obtaining their ActorRefs), you may want to look into: Distributed Publish Subscribe in Cluster.
Default Handlers

Upon start-up the actor system creates and subscribes actors to the event stream for logging: these are the handlers which are configured for example in application.conf:

akka {
  loggers = ["akka.event.Logging$DefaultLogger"]
}

The handlers listed here by fully-qualified class name will be subscribed to all log event classes with priority higher than or equal to the configured log-level and their subscriptions are kept in sync when changing the log-level at runtime:

Scala

    system.eventStream.setLogLevel(Logging.DebugLevel)

Java

This means that log events for a level which will not be logged are typically not dispatched at all (unless manual subscriptions to the respective event class have been done)
Dead Letters

As described at Stopping actors, messages queued when an actor terminates or sent after its death are re-routed to the dead letter mailbox, which by default will publish the messages wrapped in DeadLetter. This wrapper holds the original sender, receiver and message of the envelope which was redirected.

Some internal messages (marked with the DeadLetterSuppression trait) will not end up as dead letters like normal messages. These are by design safe and expected to sometimes arrive at a terminated actor and since they are nothing to worry about, they are suppressed from the default dead letters logging mechanism.

However, in case you find yourself in need of debugging these kinds of low level suppressed dead letters, it’s still possible to subscribe to them explicitly:

Scala

    import akka.actor.SuppressedDeadLetter
    system.eventStream.subscribe(listener, classOf[SuppressedDeadLetter])

Java

or all dead letters (including the suppressed ones):

Scala

    import akka.actor.AllDeadLetters
    system.eventStream.subscribe(listener, classOf[AllDeadLetters])

Java

Other Uses

The event stream is always there and ready to be used, just publish your own events (it accepts AnyRef) and subscribe listeners to the corresponding JVM classes.
