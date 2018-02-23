# Integration - Overview


# Integrating with Actors

For piping the elements of a stream as messages to an ordinary actor you can use ask in a mapAsync or use Sink.actorRefWithAck.

Messages can be sent to a stream with Source.queue or via the ActorRef that is materialized by Source.actorRef.

## mapAsync + ask

A nice way to delegate some processing of elements in a stream to an actor is to use ask in mapAsync.
- The back-pressure of the stream is maintained by the Future of the ask and the mailbox of the actor will not be filled with more messages than the given parallelism of the mapAsync stage.

```scala

    import akka.pattern.ask
    implicit val askTimeout = Timeout(5.seconds)
    val words: Source[String, NotUsed] =
      Source(List("hello", "hi"))

    words
      .mapAsync(parallelism = 5)(elem ⇒ (ref ? elem).mapTo[String])
      // continue processing of the replies from the actor
      .map(_.toLowerCase)
      .runWith(Sink.ignore)

```

Note that the messages received in the actor will be in the same order as the stream elements, i.e. the parallelism does not change the ordering of the messages.
- There is a performance advantage of using parallelism > 1 even though the actor will only process one message at a time because then there is already a message in the mailbox when the actor has completed previous message.

The actor must reply to the sender() for each message from the stream.
- That reply will complete the Future of the ask and it will be the element that is emitted downstreams from mapAsync.

```scala

    class Translator extends Actor {
      def receive = {
        case word: String ⇒
          // ... process message
          val reply = word.toUpperCase
          sender() ! reply // reply to the ask
      }
    }

```

The stream can be completed with failure by sending akka.actor.Status.Failure as reply from the actor.

If the ask fails due to timeout the stream will be completed with TimeoutException failure.
- If that is not desired outcome you can use recover on the ask Future.

If you don’t care about the reply values and only use them as back-pressure signals you can use Sink.ignore after the mapAsync stage and then actor is effectively a sink of the stream.

The same pattern can be used with Actor routers.
- Then you can use mapAsyncUnordered for better efficiency if you don’t care about the order of the emitted downstream elements (the replies).

## Sink.actorRefWithAck

The sink sends the elements of the stream to the given ActorRef that sends back back-pressure signal.
- First element is always onInitMessage, then stream is waiting for the given acknowledgement message from the given actor which means that it is ready to process elements.
- It also requires the given acknowledgement message after each stream element to make back-pressure work.

If the target actor terminates the stream will be cancelled.
- When the stream is completed successfully the given onCompleteMessage will be sent to the destination actor.
- When the stream is completed with failure a akka.actor.Status.Failure message will be sent to the destination actor.

#### Note

Using Sink.actorRef or ordinary tell from a map or foreach stage means that there is no back-pressure signal from the destination actor, i.e. if the actor is not consuming the messages fast enough the mailbox of the actor will grow, unless you use a bounded mailbox with zero mailbox-push-timeout-time or use a rate limiting stage in front.
- It’s often better to use Sink.actorRefWithAck or ask in mapAsync, though.

## Source.queue

Source.queue can be used for emitting elements to a stream from an actor (or from anything running outside the stream).
- The elements will be buffered until the stream can process them.
- You can offer elements to the queue and they will be emitted to the stream if there is demand from downstream, otherwise they will be buffered until request for demand is received.

Use overflow strategy akka.stream.OverflowStrategy.backpressure to avoid dropping of elements if the buffer is full.

SourceQueue.offer returns Future[QueueOfferResult] which completes with QueueOfferResult.Enqueued if element was added to buffer or sent downstream.
- It completes with QueueOfferResult.Dropped if element was dropped.
- Can also complete with QueueOfferResult.Failure - when stream failed or QueueOfferResult.QueueClosed when downstream is completed.

When used from an actor you typically pipe the result of the Future back to the actor to continue processing.

## Source.actorRef

Messages sent to the actor that is materialized by Source.actorRef will be emitted to the stream if there is demand from downstream, otherwise they will be buffered until request for demand is received.

Depending on the defined OverflowStrategy it might drop elements if there is no space available in the buffer.
- The strategy OverflowStrategy.backpressure is not supported for this Source type, i.e. elements will be dropped if the buffer is filled by sending at a rate that is faster than the stream can consume.
- You should consider using Source.queue if you want a backpressured actor interface.

The stream can be completed successfully by sending akka.actor.PoisonPill or akka.actor.Status.Success to the actor reference.

The stream can be completed with failure by sending akka.actor.Status.Failure to the actor reference.

The actor will be stopped when the stream is completed, failed or cancelled from downstream, i.e. you can watch it to get notified when that happens.

# Integrating with External Services

Stream transformations and side effects involving external non-stream based services can be performed with mapAsync or mapAsyncUnordered.

For example, sending emails to the authors of selected tweets using an external email service:

```scala

    def send(email: Email): Future[Unit] = {
      // ...
    }

```

We start with the tweet stream of authors:

```scala

    val authors: Source[Author, NotUsed] =
      tweets
        .filter(_.hashtags.contains(akkaTag))
        .map(_.author)

```

Assume that we can lookup their email address using:

```scala

    def lookupEmail(handle: String): Future[Option[String]] =

```

Transforming the stream of authors to a stream of email addresses by using the lookupEmail service can be done with mapAsync:

```scala

    val emailAddresses: Source[String, NotUsed] =
      authors
        .mapAsync(4)(author ⇒ addressSystem.lookupEmail(author.handle))
        .collect { case Some(emailAddress) ⇒ emailAddress }

```

Finally, sending the emails:

```scala

    val sendEmails: RunnableGraph[NotUsed] =
      emailAddresses
        .mapAsync(4)(address ⇒ {
          emailServer.send(
            Email(to = address, title = "Akka", body = "I like your tweet"))
        })
        .to(Sink.ignore)

    sendEmails.run()

```

mapAsync is applying the given function that is calling out to the external service to each of the elements as they pass through this processing step.
- The function returns a Future and the value of that future will be emitted downstreams.
- The number of Futures that shall run in parallel is given as the first argument to mapAsync.
- These Futures may complete in any order, but the elements that are emitted downstream are in the same order as received from upstream.

That means that back-pressure works as expected.
- For example if the emailServer.send is the bottleneck it will limit the rate at which incoming tweets are retrieved and email addresses looked up.

The final piece of this pipeline is to generate the demand that pulls the tweet authors information through the emailing pipeline: we attach a Sink.ignore which makes it all run.
- If our email process would return some interesting data for further transformation then we would of course not ignore it but send that result stream onwards for further processing or storage.

Note that mapAsync preserves the order of the stream elements.
- In this example the order is not important and then we can use the more efficient mapAsyncUnordered:

```scala

    val authors: Source[Author, NotUsed] =
      tweets.filter(_.hashtags.contains(akkaTag)).map(_.author)

    val emailAddresses: Source[String, NotUsed] =
      authors
        .mapAsyncUnordered(4)(author ⇒ addressSystem.lookupEmail(author.handle))
        .collect { case Some(emailAddress) ⇒ emailAddress }

    val sendEmails: RunnableGraph[NotUsed] =
      emailAddresses
        .mapAsyncUnordered(4)(address ⇒ {
          emailServer.send(
            Email(to = address, title = "Akka", body = "I like your tweet"))
        })
        .to(Sink.ignore)

    sendEmails.run()

```

In the above example the services conveniently returned a Future of the result.
- If that is not the case you need to wrap the call in a Future.
- If the service call involves blocking you must also make sure that you run it on a dedicated execution context, to avoid starvation and disturbance of other tasks in the system.

```scala

    val blockingExecutionContext = system.dispatchers.lookup("blocking-dispatcher")

    val sendTextMessages: RunnableGraph[NotUsed] =
      phoneNumbers
        .mapAsync(4)(phoneNo ⇒ {
          Future {
            smsServer.send(
              TextMessage(to = phoneNo, body = "I like your tweet"))
          }(blockingExecutionContext)
        })
        .to(Sink.ignore)

    sendTextMessages.run()

```

The configuration of the "blocking-dispatcher" may look something like:
```hocon
blocking-dispatcher {
  executor = "thread-pool-executor"
  thread-pool-executor {
    core-pool-size-min    = 10
    core-pool-size-max    = 10
  }
}
```

An alternative for blocking calls is to perform them in a map operation, still using a dedicated dispatcher for that operation.

```scala

    val send = Flow[String]
      .map { phoneNo ⇒
        smsServer.send(TextMessage(to = phoneNo, body = "I like your tweet"))
      }
      .withAttributes(ActorAttributes.dispatcher("blocking-dispatcher"))
    val sendTextMessages: RunnableGraph[NotUsed] =
      phoneNumbers.via(send).to(Sink.ignore)

    sendTextMessages.run()

```

However, that is not exactly the same as mapAsync, since the mapAsync may run several calls concurrently, but map performs them one at a time.

For a service that is exposed as an actor, or if an actor is used as a gateway in front of an external service, you can use ask:

```scala

    import akka.pattern.ask

    val akkaTweets: Source[Tweet, NotUsed] = tweets.filter(_.hashtags.contains(akkaTag))

    implicit val timeout = Timeout(3.seconds)
    val saveTweets: RunnableGraph[NotUsed] =
      akkaTweets
        .mapAsync(4)(tweet ⇒ database ? Save(tweet))
        .to(Sink.ignore)

```

Note that if the ask is not completed within the given timeout the stream is completed with failure.
- If that is not desired outcome you can use recover on the ask Future.

## Illustrating ordering and parallelism

Let us look at another example to get a better understanding of the ordering and parallelism characteristics of mapAsync and mapAsyncUnordered.

Several mapAsync and mapAsyncUnordered futures may run concurrently.
- The number of concurrent futures are limited by the downstream demand.
- For example, if 5 elements have been requested by downstream there will be at most 5 futures in progress.

mapAsync emits the future results in the same order as the input elements were received.
- That means that completed results are only emitted downstream when earlier results have been completed and emitted.
- One slow call will thereby delay the results of all successive calls, even though they are completed before the slow call.

mapAsyncUnordered emits the future results as soon as they are completed, i.e. it is possible that the elements are not emitted downstream in the same order as received from upstream.
- One slow call will thereby not delay the results of faster successive calls as long as there is downstream demand of several elements.

Here is a fictive service that we can use to illustrate these aspects.

```scala

    class SometimesSlowService(implicit ec: ExecutionContext) {

      private val runningCount = new AtomicInteger

      def convert(s: String): Future[String] = {
        println(s"running: $s (${runningCount.incrementAndGet()})")
        Future {
          if (s.nonEmpty && s.head.isLower)
            Thread.sleep(500)
          else
            Thread.sleep(20)
          println(s"completed: $s (${runningCount.decrementAndGet()})")
          s.toUpperCase
        }
      }
    }

```

Elements starting with a lower case character are simulated to take longer time to process.

Here is how we can use it with mapAsync:

```scala

    implicit val blockingExecutionContext = system.dispatchers.lookup("blocking-dispatcher")
    val service = new SometimesSlowService

    implicit val materializer = ActorMaterializer(
      ActorMaterializerSettings(system).withInputBuffer(initialSize = 4, maxSize = 4))

    Source(List("a", "B", "C", "D", "e", "F", "g", "H", "i", "J"))
      .map(elem ⇒ { println(s"before: $elem"); elem })
      .mapAsync(4)(service.convert)
      .runForeach(elem ⇒ println(s"after: $elem"))

```

The output may look like this:

before: a
before: B
before: C
before: D
running: a (1)
running: B (2)
before: e
running: C (3)
before: F
running: D (4)
before: g
before: H
completed: C (3)
completed: B (2)
completed: D (1)
completed: a (0)
after: A
after: B
running: e (1)
after: C
after: D
running: F (2)
before: i
before: J
running: g (3)
running: H (4)
completed: H (2)
completed: F (3)
completed: e (1)
completed: g (0)
after: E
after: F
running: i (1)
after: G
after: H
running: J (2)
completed: J (1)
completed: i (0)
after: I
after: J

Note that after lines are in the same order as the before lines even though elements are completed in a different order.
- For example H is completed before g, but still emitted afterwards.

The numbers in parenthesis illustrates how many calls that are in progress at the same time.
- Here the downstream demand and thereby the number of concurrent calls are limited by the buffer size (4) of the ActorMaterializerSettings.

Here is how we can use the same service with mapAsyncUnordered:

```scala

    implicit val blockingExecutionContext = system.dispatchers.lookup("blocking-dispatcher")
    val service = new SometimesSlowService

    implicit val materializer = ActorMaterializer(
      ActorMaterializerSettings(system).withInputBuffer(initialSize = 4, maxSize = 4))

    Source(List("a", "B", "C", "D", "e", "F", "g", "H", "i", "J"))
      .map(elem ⇒ { println(s"before: $elem"); elem })
      .mapAsyncUnordered(4)(service.convert)
      .runForeach(elem ⇒ println(s"after: $elem"))

```

The output may look like this:

before: a
before: B
before: C
before: D
running: a (1)
running: B (2)
before: e
running: C (3)
before: F
running: D (4)
before: g
before: H
completed: B (3)
completed: C (1)
completed: D (2)
after: B
after: D
running: e (2)
after: C
running: F (3)
before: i
before: J
completed: F (2)
after: F
running: g (3)
running: H (4)
completed: H (3)
after: H
completed: a (2)
after: A
running: i (3)
running: J (4)
completed: J (3)
after: J
completed: e (2)
after: E
completed: g (1)
after: G
completed: i (0)
after: I

Note that after lines are not in the same order as the before lines.
- For example H overtakes the slow G.

The numbers in parenthesis illustrates how many calls that are in progress at the same time.
- Here the downstream demand and thereby the number of concurrent calls are limited by the buffer size (4) of the ActorMaterializerSettings.

# Integrating with Reactive Streams

Reactive Streams defines a standard for asynchronous stream processing with non-blocking back pressure.
- It makes it possible to plug together stream libraries that adhere to the standard.
- Akka Streams is one such library.

An incomplete list of other implementations:

    Reactor (1.1+)
    RxJava
    Ratpack
    Slick

The two most important interfaces in Reactive Streams are the Publisher and Subscriber.

```scala

    import org.reactivestreams.Publisher
    import org.reactivestreams.Subscriber

```

Let us assume that a library provides a publisher of tweets:

```scala

    def tweets: Publisher[Tweet]

```

and another library knows how to store author handles in a database:

```scala

    def storage: Subscriber[Author]

```

Using an Akka Streams Flow we can transform the stream and connect those:

```scala

    val authors = Flow[Tweet]
      .filter(_.hashtags.contains(akkaTag))
      .map(_.author)

    Source.fromPublisher(tweets).via(authors).to(Sink.fromSubscriber(storage)).run()

```

The Publisher is used as an input Source to the flow and the Subscriber is used as an output Sink.

A Flow can also be also converted to a RunnableGraph[Processor[In, Out]] which materializes to a Processor when run() is called.
- run() itself can be called multiple times, resulting in a new Processor instance each time.

```scala

    val processor: Processor[Tweet, Author] = authors.toProcessor.run()

    tweets.subscribe(processor)
    processor.subscribe(storage)

```

A publisher can be connected to a subscriber with the subscribe method.

It is also possible to expose a Source as a Publisher by using the Publisher-Sink:

```scala

    val authorPublisher: Publisher[Author] =
      Source.fromPublisher(tweets).via(authors).runWith(Sink.asPublisher(fanout = false))

    authorPublisher.subscribe(storage)

```

A publisher that is created with Sink.asPublisher(fanout = false) supports only a single subscription.
- Additional subscription attempts will be rejected with an IllegalStateException.

A publisher that supports multiple subscribers using fan-out/broadcasting is created as follows:

```scala

    def alert: Subscriber[Author]
    def storage: Subscriber[Author]

```

```scala

    val authorPublisher: Publisher[Author] =
      Source.fromPublisher(tweets).via(authors)
        .runWith(Sink.asPublisher(fanout = true))

    authorPublisher.subscribe(storage)
    authorPublisher.subscribe(alert)

```

The input buffer size of the stage controls how far apart the slowest subscriber can be from the fastest subscriber before slowing down the stream.

To make the picture complete, it is also possible to expose a Sink as a Subscriber by using the Subscriber-Source:

```scala

    val tweetSubscriber: Subscriber[Tweet] =
      authors.to(Sink.fromSubscriber(storage)).runWith(Source.asSubscriber[Tweet])

    tweets.subscribe(tweetSubscriber)

```

It is also possible to use re-wrap Processor instances as a Flow by passing a factory function that will create the Processor instances:

```scala

    // An example Processor factory
    def createProcessor: Processor[Int, Int] = Flow[Int].toProcessor.run()

    val flow: Flow[Int, Int, NotUsed] = Flow.fromProcessor(() ⇒ createProcessor)

```

Please note that a factory is necessary to achieve reusability of the resulting Flow.

## Implementing Reactive Streams Publisher or Subscriber

As described above any Akka Streams Source can be exposed as a Reactive Streams Publisher and any Sink can be exposed as a Reactive Streams Subscriber.
- Therefore we recommend that you implement Reactive Streams integrations with built-in stages or custom stages.

For historical reasons the ActorPublisher and ActorSubscriber traits are provided to support implementing Reactive Streams Publisher and Subscriber with an Actor.

These can be consumed by other Reactive Stream libraries or used as an Akka Streams Source or Sink.

#### Warning

ActorPublisher and ActorSubscriber will probably be deprecated in future versions of Akka.

#### Warning

ActorPublisher and ActorSubscriber cannot be used with remote actors, because if signals of the Reactive Streams protocol (e.g. request) are lost the the stream may deadlock.

### ActorPublisher

#### Warning

Deprecation warning: ActorPublisher is deprecated in favour of the vastly more type-safe and safe to implement akka.stream.stage.GraphStage.
- It can also expose a “stage actor ref” is needed to be addressed as-if an Actor.
- Custom stages implemented using GraphStage are also automatically fusable.

To learn more about implementing custom stages using it refer to Custom processing with GraphStage.

Extend akka.stream.actor.ActorPublisher in your Actor to make it a stream publisher that keeps track of the subscription life cycle and requested elements.

Here is an example of such an actor.
- It dispatches incoming jobs to the attached subscriber:

```scala

    object JobManager {
      def props: Props = Props[JobManager]

      final case class Job(payload: String)
      case object JobAccepted
      case object JobDenied
    }

    class JobManager extends ActorPublisher[JobManager.Job] {
      import akka.stream.actor.ActorPublisherMessage._
      import JobManager._

      val MaxBufferSize = 100
      var buf = Vector.empty[Job]

      def receive = {
        case job: Job if buf.size == MaxBufferSize ⇒
          sender() ! JobDenied
        case job: Job ⇒
          sender() ! JobAccepted
          if (buf.isEmpty && totalDemand > 0)
            onNext(job)
          else {
            buf :+= job
            deliverBuf()
          }
        case Request(_) ⇒
          deliverBuf()
        case Cancel ⇒
          context.stop(self)
      }

      @tailrec final def deliverBuf(): Unit =
        if (totalDemand > 0) {
          /*
           * totalDemand is a Long and could be larger than
           * what buf.splitAt can accept
           */
          if (totalDemand <= Int.MaxValue) {
            val (use, keep) = buf.splitAt(totalDemand.toInt)
            buf = keep
            use foreach onNext
          } else {
            val (use, keep) = buf.splitAt(Int.MaxValue)
            buf = keep
            use foreach onNext
            deliverBuf()
          }
        }
    }

```

You send elements to the stream by calling onNext.
- You are allowed to send as many elements as have been requested by the stream subscriber.
- This amount can be inquired with totalDemand.
- It is only allowed to use onNext when isActive and totalDemand>0, otherwise onNext will throw IllegalStateException.

When the stream subscriber requests more elements the ActorPublisherMessage.Request message is delivered to this actor, and you can act on that event.
- The totalDemand is updated automatically.

When the stream subscriber cancels the subscription the ActorPublisherMessage.Cancel message is delivered to this actor.
- After that subsequent calls to onNext will be ignored.

You can complete the stream by calling onComplete.
- After that you are not allowed to call onNext, onError and onComplete.

You can terminate the stream with failure by calling onError.
- After that you are not allowed to call onNext, onError and onComplete.

If you suspect that this ActorPublisher may never get subscribed to, you can override the subscriptionTimeout method to provide a timeout after which this Publisher should be considered canceled.
- The actor will be notified when the timeout triggers via an ActorPublisherMessage.SubscriptionTimeoutExceeded message and MUST then perform cleanup and stop itself.

If the actor is stopped the stream will be completed, unless it was not already terminated with failure, completed or canceled.

More detailed information can be found in the API documentation.

This is how it can be used as input Source to a Flow:

```scala

    val jobManagerSource = Source.actorPublisher[JobManager.Job](JobManager.props)
    val ref = Flow[JobManager.Job]
      .map(_.payload.toUpperCase)
      .map { elem ⇒ println(elem); elem }
      .to(Sink.ignore)
      .runWith(jobManagerSource)

    ref ! JobManager.Job("a")
    ref ! JobManager.Job("b")
    ref ! JobManager.Job("c")

```

A publisher that is created with Sink.asPublisher supports a specified number of subscribers.
- Additional subscription attempts will be rejected with an IllegalStateException.

### ActorSubscriber

#### Warning

Deprecation warning: ActorSubscriber is deprecated in favour of the vastly more type-safe and safe to implement akka.stream.stage.GraphStage.
- It can also expose a “stage actor ref” is needed to be addressed as-if an Actor.
- Custom stages implemented using GraphStage are also automatically fusable.

To learn more about implementing custom stages using it refer to Custom processing with GraphStage.

Extend akka.stream.actor.ActorSubscriber in your Actor to make it a stream subscriber with full control of stream back pressure.
- It will receive ActorSubscriberMessage.OnNext, ActorSubscriberMessage.OnComplete and ActorSubscriberMessage.OnError messages from the stream.
- It can also receive other, non-stream messages, in the same way as any actor.

Here is an example of such an actor.
- It dispatches incoming jobs to child worker actors:

```scala

    object WorkerPool {
      case class Msg(id: Int, replyTo: ActorRef)
      case class Work(id: Int)
      case class Reply(id: Int)
      case class Done(id: Int)

      def props: Props = Props(new WorkerPool)
    }

    class WorkerPool extends ActorSubscriber {
      import WorkerPool._
      import ActorSubscriberMessage._

      val MaxQueueSize = 10
      var queue = Map.empty[Int, ActorRef]

      val router = {
        val routees = Vector.fill(3) {
          ActorRefRoutee(context.actorOf(Props[Worker]))
        }
        Router(RoundRobinRoutingLogic(), routees)
      }

      override val requestStrategy = new MaxInFlightRequestStrategy(max = MaxQueueSize) {
        override def inFlightInternally: Int = queue.size
      }

      def receive = {
        case OnNext(Msg(id, replyTo)) ⇒
          queue += (id -> replyTo)
          assert(queue.size <= MaxQueueSize, s"queued too many: ${queue.size}")
          router.route(Work(id), self)
        case Reply(id) ⇒
          queue(id) ! Done(id)
          queue -= id
          if (canceled && queue.isEmpty) {
            context.stop(self)
          }
        case OnComplete ⇒
          if (queue.isEmpty) {
            context.stop(self)
          }
      }
    }

    class Worker extends Actor {
      import WorkerPool._
      def receive = {
        case Work(id) ⇒
          // ...
          sender() ! Reply(id)
      }
    }

```

Subclass must define the RequestStrategy to control stream back pressure.
- After each incoming message the ActorSubscriber will automatically invoke the RequestStrategy.requestDemand and propagate the returned demand to the stream.

    The provided WatermarkRequestStrategy is a good strategy if the actor performs work itself.
    The provided MaxInFlightRequestStrategy is useful if messages are queued internally or delegated to other actors.
    You can also implement a custom RequestStrategy or call request manually together with ZeroRequestStrategy or some other strategy.
- In that case you must also call request when the actor is started or when it is ready, otherwise it will not receive any elements.

More detailed information can be found in the API documentation.

This is how it can be used as output Sink to a Flow:

```scala

    val N = 117
    val worker = Source(1 to N).map(WorkerPool.Msg(_, replyTo))
      .runWith(Sink.actorSubscriber(WorkerPool.props))

```

