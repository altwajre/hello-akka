# Persistence Query - Overview

Akka persistence query complements Persistence by 
- providing a universal asynchronous stream based query interface 
- that various journal plugins can implement in order to expose their query capabilities.

The most typical use case of persistence query is implementing the so-called query side 
- (also known as “read side”) in the popular CQRS architecture pattern 
- in which the writing side of the application (e.g. implemented using akka persistence) 
- is completely separated from the “query side”. 
- Akka Persistence Query itself is not directly the query side of an application, 
- however it can help to migrate data from the write side to the query side database. 
- In very simple scenarios Persistence Query may be powerful enough to fulfill the query needs of your app, 
- however we highly recommend splitting up the write/read sides into separate datastores as the need arises.

# Dependencies

Akka persistence query is a separate jar file. Make sure that you have the following dependency in your project:

```sbtshell
"com.typesafe.akka" %% "akka-persistence-query" % "2.5.9"
```

# Design overview

Akka persistence query is purposely designed to be a very loosely specified API. 
- This is in order to keep the provided APIs general enough for each journal implementation 
- to be able to expose its best features, 
- e.g. a SQL journal can use complex SQL queries 
- or if a journal is able to subscribe to a live event stream this should also be possible to expose the same API 
- a typed stream of events.

Each read journal must explicitly document which types of queries it supports. 
- Refer to your journal’s plugins documentation for details on which queries and semantics it supports.

While Akka Persistence Query does not provide actual implementations of ReadJournals, 
- it defines a number of pre-defined query types for the most common query scenarios, 
- that most journals are likely to implement (however they are not required to).

# Read Journals

In order to issue queries one has to first obtain an instance of a ReadJournal. 
- Read journals are implemented as Community plugins, each targeting a specific datastore 
- (for example Cassandra or JDBC databases). 
- For example, given a library that provides a akka.persistence.query.my-read-journal obtaining the related journal is as simple as:

```scala
// obtain read journal by plugin id
val readJournal =
  PersistenceQuery(system).readJournalFor[MyScaladslReadJournal](
    "akka.persistence.query.my-read-journal")

// issue query to journal
val source: Source[EventEnvelope, NotUsed] =
  readJournal.eventsByPersistenceId("user-1337", 0, Long.MaxValue)

// materialize stream, consuming events
implicit val mat = ActorMaterializer()
source.runForeach { event ⇒ println("Event: " + event) }
```

Journal implementers are encouraged to put this identifier in a variable known to the user, 
- such that one can access it via readJournalFor[NoopJournal](NoopJournal.identifier), however this is not enforced.

Read journal implementations are available as Community plugins.

## Predefined queries

Akka persistence query comes with a number of query interfaces built in 
- and suggests Journal implementors to implement them according to the semantics described below. 
- It is important to notice that while these query types are very common a journal is not obliged to implement all of them 
- for example because in a given journal such query would be significantly inefficient.

#### Note
Refer to the documentation of the ReadJournal plugin you are using for a specific list of supported query types. 
- For example, Journal plugins should document their stream completion strategies.
##

The predefined queries are:

## PersistenceIdsQuery and CurrentPersistenceIdsQuery

persistenceIds which is designed to allow users to subscribe to a stream of all persistent ids in the system. 
- By default this stream should be assumed to be a “live” stream, 
- which means that the journal should keep emitting new persistence ids as they come into the system:

```scala
readJournal.persistenceIds()
```

If your usage does not require a live stream, you can use the currentPersistenceIds query:

```scala
readJournal.currentPersistenceIds()
```

## EventsByPersistenceIdQuery and CurrentEventsByPersistenceIdQuery

eventsByPersistenceId is a query equivalent to replaying a PersistentActor, 
- however, since it is a stream it is possible to keep it alive 
- and watch for additional incoming events persisted by the persistent actor identified by the given persistenceId.

```scala
readJournal.eventsByPersistenceId("user-us-1337")
```

Most journals will have to revert to polling in order to achieve this, 
- which can typically be configured with a refresh-interval configuration property.

If your usage does not require a live stream, you can use the currentEventsByPersistenceId query.

## EventsByTag and CurrentEventsByTag

eventsByTag allows querying events regardless of which persistenceId they are associated with. 
- This query is hard to implement in some journals 
- or may need some additional preparation of the used data store to be executed efficiently. 
- The goal of this query is to allow querying for all events which are “tagged” with a specific tag. 
- That includes the use case to query all domain events of an Aggregate Root type. 
- Please refer to your read journal plugin’s documentation to find out if and how it is supported.

Some journals may support tagging of events via an Event Adapters 
- that wraps the events in a akka.persistence.journal.Tagged with the given tags. 
- The journal may support other ways of doing tagging - again, 
- how exactly this is implemented depends on the used journal. 
- Here is an example of such a tagging event adapter:

```scala
import akka.persistence.journal.WriteEventAdapter
import akka.persistence.journal.Tagged

class MyTaggingEventAdapter extends WriteEventAdapter {
  val colors = Set("green", "black", "blue")
  override def toJournal(event: Any): Any = event match {
    case s: String ⇒
      var tags = colors.foldLeft(Set.empty[String]) { (acc, c) ⇒
        if (s.contains(c)) acc + c else acc
      }
      if (tags.isEmpty) event
      else Tagged(event, tags)
    case _ ⇒ event
  }

  override def manifest(event: Any): String = ""
}
```


#### Note
A very important thing to keep in mind when using queries spanning multiple persistenceIds, 
- such as EventsByTag is that the order of events at which the events appear in the stream rarely is guaranteed 
- (or stable between materializations).

Journals may choose to opt for strict ordering of the events, 
- and should then document explicitly what kind of ordering guarantee they provide 
- for example “ordered by timestamp ascending, independently of persistenceId” is easy to achieve on relational databases, 
- yet may be hard to implement efficiently on plain key-value datastores.

In the example below we query all events which have been tagged.
- We assume this was performed by the write-side using an EventAdapter, 
- or that the journal is smart enough that it can figure out what we mean by this tag 
- for example if the journal stored the events as JSON 
- it may try to find those with the field tag set to this value etc.

```scala
// assuming journal is able to work with numeric offsets we can:

val blueThings: Source[EventEnvelope, NotUsed] =
  readJournal.eventsByTag("blue")

// find top 10 blue things:
val top10BlueThings: Future[Vector[Any]] =
  blueThings
    .map(_.event)
    .take(10) // cancels the query stream after pulling 10 elements
    .runFold(Vector.empty[Any])(_ :+ _)

// start another query, from the known offset
val furtherBlueThings = readJournal.eventsByTag("blue", offset = Sequence(10))
```

As you can see, we can use all the usual stream combinators available from Streams on the resulting query stream, 
- including for example taking the first 10 and cancelling the stream. 
- It is worth pointing out that the built-in EventsByTag query has an optionally supported offset parameter 
- (of type Long) 
- which the journals can use to implement resumable-streams. 
- For example a journal may be able to use a WHERE clause to begin the read starting from a specific row, 
- or in a datastore that is able to order events by insertion time, 
- it could treat the Long as a timestamp and select only older events.

If your usage does not require a live stream, you can use the currentEventsByTag query.

## Materialized values of queries

Journals are able to provide additional information related to a query by exposing Materialized values, 
- which are a feature of Streams that allows to expose additional values at stream materialization time.

More advanced query journals may use this technique to expose information about the character of the materialized stream, f
- or example if it’s finite or infinite, strictly ordered or not ordered at all. 
- The materialized value type is defined as the second type parameter of the returned Source, 
- which allows journals to provide users with their specialised query object, as demonstrated in the sample below:

```scala
final case class RichEvent(tags: Set[String], payload: Any)

// a plugin can provide:
case class QueryMetadata(deterministicOrder: Boolean, infinite: Boolean)
```

```scala
def byTagsWithMeta(tags: Set[String]): Source[RichEvent, QueryMetadata] = {
```

```scala
val query: Source[RichEvent, QueryMetadata] =
  readJournal.byTagsWithMeta(Set("red", "blue"))

query
  .mapMaterializedValue { meta ⇒
    println(s"The query is: " +
      s"ordered deterministically: ${meta.deterministicOrder}, " +
      s"infinite: ${meta.infinite}")
  }
  .map { event ⇒ println(s"Event payload: ${event.payload}") }
  .runWith(Sink.ignore)
```

# Performance and denormalization

When building systems using Event sourcing and CQRS techniques 
- it is tremendously important to realise that the write-side has completely different needs from the read-side, 
- and separating those concerns into datastores that are optimised for either side 
- makes it possible to offer the best experience for the write and read sides independently.

For example, in a bidding system it is important to “take the write” 
- and respond to the bidder that we have accepted the bid as soon as possible, 
- which means that write-throughput is of highest importance for the write-side. 
- Often this means that data stores which are able to scale to accommodate these requirements have a less expressive query side.

On the other hand the same application may have some complex statistics view 
- or we may have analysts working with the data to figure out best bidding strategies and trends.
- This often requires some kind of expressive query capabilities like for example SQL or writing Spark jobs to analyse the data. 
- Therefore the data stored in the write-side needs to be projected into the other read-optimised datastore.

#### Note
When referring to Materialized Views in Akka Persistence think of it as “some persistent storage of the result of a Query”. 
- In other words, it means that the view is created once, in order to be afterwards queried multiple times, 
- as in this format it may be more efficient or interesting to query it (instead of the source events directly).

## Materialize view to Reactive Streams compatible datastore

If the read datastore exposes a Reactive Streams interface then implementing a simple projection is as simple as, 
- using the read-journal and feeding it into the databases driver interface, for example like so:

```scala
implicit val system = ActorSystem()
implicit val mat = ActorMaterializer()

val readJournal =
  PersistenceQuery(system).readJournalFor[MyScaladslReadJournal](JournalId)
val dbBatchWriter: Subscriber[immutable.Seq[Any]] =
  ReactiveStreamsCompatibleDBDriver.batchWriter

// Using an example (Reactive Streams) Database driver
readJournal
  .eventsByPersistenceId("user-1337")
  .map(envelope ⇒ envelope.event)
  .map(convertToReadSideTypes) // convert to datatype
  .grouped(20) // batch inserts into groups of 20
  .runWith(Sink.fromSubscriber(dbBatchWriter)) // write batches to read-side database
```

## Materialize view using mapAsync

If the target database does not provide a reactive streams Subscriber that can perform writes, 
- you may have to implement the write logic using plain functions or Actors instead.

In case your write logic is state-less 
- and you just need to convert the events from one data type to another before writing into the alternative datastore, 
- then the projection is as simple as:

```scala
trait ExampleStore {
  def save(event: Any): Future[Unit]
}
```

```scala
val store: ExampleStore = ???

readJournal
  .eventsByTag("bid")
  .mapAsync(1) { e ⇒ store.save(e) }
  .runWith(Sink.ignore)
```

## Resumable projections

Sometimes you may need to implement “resumable” projections, 
- that will not start from the beginning of time each time when run. 
- In this case you will need to store the sequence number (or offset) of the processed event 
- and use it the next time this projection is started. This pattern is not built-in, 
- however is rather simple to implement yourself.

The example below additionally highlights how you would use Actors to implement the write side, 
- in case you need to do some complex logic 
- that would be best handled inside an Actor before persisting the event into the other datastore:

```scala
import akka.pattern.ask
import system.dispatcher
implicit val timeout = Timeout(3.seconds)

val bidProjection = new MyResumableProjection("bid")

val writerProps = Props(classOf[TheOneWhoWritesToQueryJournal], "bid")
val writer = system.actorOf(writerProps, "bid-projection-writer")

bidProjection.latestOffset.foreach { startFromOffset ⇒
  readJournal
    .eventsByTag("bid", Sequence(startFromOffset))
    .mapAsync(8) { envelope ⇒ (writer ? envelope.event).map(_ ⇒ envelope.offset) }
    .mapAsync(1) { offset ⇒ bidProjection.saveProgress(offset) }
    .runWith(Sink.ignore)
}
```

```scala
class TheOneWhoWritesToQueryJournal(id: String) extends Actor {
  val store = new DummyStore()

  var state: ComplexState = ComplexState()

  def receive = {
    case m ⇒
      state = updateState(state, m)
      if (state.readyToSave) store.save(Record(state))
  }

  def updateState(state: ComplexState, msg: Any): ComplexState = {
    // some complicated aggregation logic here ...
    state
  }
}
```

# Query plugins

Query plugins are various (mostly community driven) ReadJournal implementations for all kinds of available datastores. 
- The complete list of available plugins is maintained on the Akka Persistence Query Community Plugins page.

The plugin for LevelDB is described in Persistence Query for LevelDB.

This section aims to provide tips and guide plugin developers through implementing a custom query plugin. 
- Most users will not need to implement journals themselves, except if targeting a not yet supported datastore.

#### Note
Since different data stores provide different query capabilities,
- journal plugins must extensively document their exposed semantics as well as handled query scenarios.

## ReadJournal plugin API
A read journal plugin must implement akka.persistence.query.ReadJournalProvider 
- which creates instances of akka.persistence.query.scaladsl.ReadJournal and akka.persistence.query.javaadsl.ReadJournal. 
- The plugin must implement both the scaladsl and the javadsl traits 
- because the akka.stream.scaladsl.Source and akka.stream.javadsl.Source are different types 
- and even though those types can easily be converted to each other 
- it is most convenient for the end user to get access to the Java or Scala Source directly. 
- As illustrated below one of the implementations can delegate to the other.

Below is a simple journal implementation:

```scala
class MyReadJournalProvider(system: ExtendedActorSystem, config: Config)
  extends ReadJournalProvider {

  override val scaladslReadJournal: MyScaladslReadJournal =
    new MyScaladslReadJournal(system, config)

  override val javadslReadJournal: MyJavadslReadJournal =
    new MyJavadslReadJournal(scaladslReadJournal)
}

class MyScaladslReadJournal(system: ExtendedActorSystem, config: Config)
  extends akka.persistence.query.scaladsl.ReadJournal
  with akka.persistence.query.scaladsl.EventsByTagQuery
  with akka.persistence.query.scaladsl.EventsByPersistenceIdQuery
  with akka.persistence.query.scaladsl.PersistenceIdsQuery
  with akka.persistence.query.scaladsl.CurrentPersistenceIdsQuery {

  private val refreshInterval: FiniteDuration =
    config.getDuration("refresh-interval", MILLISECONDS).millis

  /**
   * You can use `NoOffset` to retrieve all events with a given tag or retrieve a subset of all
   * events by specifying a `Sequence` `offset`. The `offset` corresponds to an ordered sequence number for
   * the specific tag. Note that the corresponding offset of each event is provided in the
   * [[akka.persistence.query.EventEnvelope]], which makes it possible to resume the
   * stream at a later point from a given offset.
   *
   * The `offset` is exclusive, i.e. the event with the exact same sequence number will not be included
   * in the returned stream. This means that you can use the offset that is returned in `EventEnvelope`
   * as the `offset` parameter in a subsequent query.
   */
  override def eventsByTag(
    tag: String, offset: Offset = Sequence(0L)): Source[EventEnvelope, NotUsed] = offset match {
    case Sequence(offsetValue) ⇒
      val props = MyEventsByTagPublisher.props(tag, offsetValue, refreshInterval)
      Source.actorPublisher[EventEnvelope](props)
        .mapMaterializedValue(_ ⇒ NotUsed)
    case NoOffset ⇒ eventsByTag(tag, Sequence(0L)) //recursive
    case _ ⇒
      throw new IllegalArgumentException("LevelDB does not support " + offset.getClass.getName + " offsets")
  }

  override def eventsByPersistenceId(
    persistenceId: String, fromSequenceNr: Long = 0L,
    toSequenceNr: Long = Long.MaxValue): Source[EventEnvelope, NotUsed] = {
    // implement in a similar way as eventsByTag
    ???
  }

  override def persistenceIds(): Source[String, NotUsed] = {
    // implement in a similar way as eventsByTag
    ???
  }

  override def currentPersistenceIds(): Source[String, NotUsed] = {
    // implement in a similar way as eventsByTag
    ???
  }

  // possibility to add more plugin specific queries

  def byTagsWithMeta(tags: Set[String]): Source[RichEvent, QueryMetadata] = {
    // implement in a similar way as eventsByTag
    ???
  }

}

class MyJavadslReadJournal(scaladslReadJournal: MyScaladslReadJournal)
  extends akka.persistence.query.javadsl.ReadJournal
  with akka.persistence.query.javadsl.EventsByTagQuery
  with akka.persistence.query.javadsl.EventsByPersistenceIdQuery
  with akka.persistence.query.javadsl.PersistenceIdsQuery
  with akka.persistence.query.javadsl.CurrentPersistenceIdsQuery {

  override def eventsByTag(
    tag: String, offset: Offset = Sequence(0L)): javadsl.Source[EventEnvelope, NotUsed] =
    scaladslReadJournal.eventsByTag(tag, offset).asJava

  override def eventsByPersistenceId(
    persistenceId: String, fromSequenceNr: Long = 0L,
    toSequenceNr: Long = Long.MaxValue): javadsl.Source[EventEnvelope, NotUsed] =
    scaladslReadJournal.eventsByPersistenceId(
      persistenceId, fromSequenceNr, toSequenceNr).asJava

  override def persistenceIds(): javadsl.Source[String, NotUsed] =
    scaladslReadJournal.persistenceIds().asJava

  override def currentPersistenceIds(): javadsl.Source[String, NotUsed] =
    scaladslReadJournal.currentPersistenceIds().asJava

  // possibility to add more plugin specific queries

  def byTagsWithMeta(
    tags: java.util.Set[String]): javadsl.Source[RichEvent, QueryMetadata] = {
    import scala.collection.JavaConverters._
    scaladslReadJournal.byTagsWithMeta(tags.asScala.toSet).asJava
  }
}
```

And the eventsByTag could be backed by such an Actor for example:

```scala
class MyEventsByTagPublisher(tag: String, offset: Long, refreshInterval: FiniteDuration)
  extends ActorPublisher[EventEnvelope] {

  private case object Continue

  private val connection: java.sql.Connection = ???

  private val Limit = 1000
  private var currentOffset = offset
  var buf = Vector.empty[EventEnvelope]

  import context.dispatcher
  val continueTask = context.system.scheduler.schedule(
    refreshInterval, refreshInterval, self, Continue)

  override def postStop(): Unit = {
    continueTask.cancel()
  }

  def receive = {
    case _: Request | Continue ⇒
      query()
      deliverBuf()

    case Cancel ⇒
      context.stop(self)
  }

  object Select {
    private def statement() = connection.prepareStatement(
      """
        SELECT id, persistent_repr FROM journal
        WHERE tag = ? AND id > ?
        ORDER BY id LIMIT ?
      """)

    def run(tag: String, from: Long, limit: Int): Vector[(Long, Array[Byte])] = {
      val s = statement()
      try {
        s.setString(1, tag)
        s.setLong(2, from)
        s.setLong(3, limit)
        val rs = s.executeQuery()

        val b = Vector.newBuilder[(Long, Array[Byte])]
        while (rs.next())
          b += (rs.getLong(1) -> rs.getBytes(2))
        b.result()
      } finally s.close()
    }
  }

  def query(): Unit =
    if (buf.isEmpty) {
      try {
        val result = Select.run(tag, currentOffset, Limit)
        currentOffset = if (result.nonEmpty) result.last._1 else currentOffset
        val serialization = SerializationExtension(context.system)

        buf = result.map {
          case (id, bytes) ⇒
            val p = serialization.deserialize(bytes, classOf[PersistentRepr]).get
            EventEnvelope(offset = Sequence(id), p.persistenceId, p.sequenceNr, p.payload)
        }
      } catch {
        case e: Exception ⇒
          onErrorThenStop(e)
      }
    }

  final def deliverBuf(): Unit =
    if (totalDemand > 0 && buf.nonEmpty) {
      if (totalDemand <= Int.MaxValue) {
        val (use, keep) = buf.splitAt(totalDemand.toInt)
        buf = keep
        use foreach onNext
      } else {
        buf foreach onNext
        buf = Vector.empty
      }
    }
}
```

The ReadJournalProvider class must have a constructor with one of these signatures:
    - Constructor with a ExtendedActorSystem parameter, a com.typesafe.config.Config parameter, 
- and a String parameter for the config path.
    - Constructor with a ExtendedActorSystem parameter, and a com.typesafe.config.Config parameter.
    - Constructor with one ExtendedActorSystem parameter.
    - Constructor without parameters.

The plugin section of the actor system’s config will be passed in the config constructor parameter. 
- The config path of the plugin is passed in the String parameter.

If the underlying datastore only supports queries that are completed when they reach the end of the “result set”, 
- the journal has to submit new queries after a while 
- in order to support “infinite” event streams that include events stored after the initial query has completed. 
- It is recommended that the plugin use a configuration property named refresh-interval for defining such a refresh interval.
