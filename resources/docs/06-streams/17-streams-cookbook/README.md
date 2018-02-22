# Streams Cookbook - Overview

# Introduction

This is a collection of patterns to demonstrate various usage of the Akka Streams API by solving small targeted problems in the format of “recipes”. The purpose of this page is to give inspiration and ideas how to approach various small tasks involving streams. The recipes in this page can be used directly as-is, but they are most powerful as starting points: customization of the code snippets is warmly encouraged.

This part also serves as supplementary material for the main body of documentation. It is a good idea to have this page open while reading the manual and look for examples demonstrating various streaming concepts as they appear in the main body of documentation.

If you need a quick reference of the available processing stages used in the recipes see stages overview.

# Working with Flows

In this collection we show simple recipes that involve linear flows. The recipes in this section are rather general, more targeted recipes are available as separate sections (Buffers and working with rate, Working with streaming IO).
Logging elements of a stream

Situation: During development it is sometimes helpful to see what happens in a particular section of a stream.

The simplest solution is to simply use a map operation and use println to print the elements received to the console. While this recipe is rather simplistic, it is often suitable for a quick debug session.

Scala

    val loggedSource = mySource.map { elem ⇒ println(elem); elem }

Java

Another approach to logging is to use log() operation which allows configuring logging for elements flowing through the stream as well as completion and erroring.

Scala

    // customise log levels
    mySource.log("before-map")
      .withAttributes(Attributes.logLevels(onElement = Logging.WarningLevel))
      .map(analyse)

    // or provide custom logging adapter
    implicit val adapter = Logging(system, "customLogger")
    mySource.log("custom")

Java

Flattening a stream of sequences

Situation: A stream is given as a stream of sequence of elements, but a stream of elements needed instead, streaming all the nested elements inside the sequences separately.

The mapConcat operation can be used to implement a one-to-many transformation of elements using a mapper function in the form of In => immutable.Seq[Out] . In this case we want to map a Seq of elements to the elements in the collection itself, so we can just call mapConcat(identity) .

Scala

    val myData: Source[List[Message], NotUsed] = someDataSource
    val flattened: Source[Message, NotUsed] = myData.mapConcat(identity)

Java

Draining a stream to a strict collection

Situation: A possibly unbounded sequence of elements is given as a stream, which needs to be collected into a Scala collection while ensuring boundedness

A common situation when working with streams is one where we need to collect incoming elements into a Scala collection. This operation is supported via Sink.seq which materializes into a Future[Seq[T]] .

The function limit or take should always be used in conjunction in order to guarantee stream boundedness, thus preventing the program from running out of memory.

For example, this is best avoided:

Scala

    // Dangerous: might produce a collection with 2 billion elements!
    val f: Future[Seq[String]] = mySource.runWith(Sink.seq)

Java

Rather, use limit or take to ensure that the resulting Seq will contain only up to max elements:

Scala

    val MAX_ALLOWED_SIZE = 100

    // OK. Future will fail with a `StreamLimitReachedException`
    // if the number of incoming elements is larger than max
    val limited: Future[Seq[String]] =
      mySource.limit(MAX_ALLOWED_SIZE).runWith(Sink.seq)

    // OK. Collect up until max-th elements only, then cancel upstream
    val ignoreOverflow: Future[Seq[String]] =
      mySource.take(MAX_ALLOWED_SIZE).runWith(Sink.seq)

Java

Calculating the digest of a ByteString stream

Situation: A stream of bytes is given as a stream of ByteString s and we want to calculate the cryptographic digest of the stream.

This recipe uses a GraphStage to host a mutable MessageDigest class (part of the Java Cryptography API) and update it with the bytes arriving from the stream. When the stream starts, the onPull handler of the stage is called, which just bubbles up the pull event to its upstream. As a response to this pull, a ByteString chunk will arrive (onPush) which we use to update the digest, then it will pull for the next chunk.

Eventually the stream of ByteString s depletes and we get a notification about this event via onUpstreamFinish. At this point we want to emit the digest value, but we cannot do it with push in this handler directly since there may be no downstream demand. Instead we call emit which will temporarily replace the handlers, emit the provided value when demand comes in and then reset the stage state. It will then complete the stage.

Scala

    import akka.stream.stage._
    class DigestCalculator(algorithm: String) extends GraphStage[FlowShape[ByteString, ByteString]] {
      val in = Inlet[ByteString]("DigestCalculator.in")
      val out = Outlet[ByteString]("DigestCalculator.out")
      override val shape = FlowShape.of(in, out)

      override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
        val digest = MessageDigest.getInstance(algorithm)

        setHandler(out, new OutHandler {
          override def onPull(): Trigger = {
            pull(in)
          }
        })

        setHandler(in, new InHandler {
          override def onPush(): Trigger = {
            val chunk = grab(in)
            digest.update(chunk.toArray)
            pull(in)
          }

          override def onUpstreamFinish(): Unit = {
            emit(out, ByteString(digest.digest()))
            completeStage()
          }
        })

      }
    }
    val digest: Source[ByteString, NotUsed] = data.via(new DigestCalculator("SHA-256"))

Java

Parsing lines from a stream of ByteStrings

Situation: A stream of bytes is given as a stream of ByteString s containing lines terminated by line ending characters (or, alternatively, containing binary frames delimited by a special delimiter byte sequence) which needs to be parsed.

The Framing helper object contains a convenience method to parse messages from a stream of ByteString s:

Scala

    import akka.stream.scaladsl.Framing
    val linesStream = rawData.via(Framing.delimiter(
      ByteString("\r\n"), maximumFrameLength = 100, allowTruncation = true))
      .map(_.utf8String)

Java

Dealing with compressed data streams

Situation: A gzipped stream of bytes is given as a stream of ByteString s, for example from a FileIO source.

The Compression helper object contains convenience methods for decompressing data streams compressed with Gzip or Deflate.

Scala

    import akka.stream.scaladsl.Compression
    val uncompressed = compressed.via(Compression.gunzip())
      .map(_.utf8String)

Java

Implementing reduce-by-key

Situation: Given a stream of elements, we want to calculate some aggregated value on different subgroups of the elements.

The “hello world” of reduce-by-key style operations is wordcount which we demonstrate below. Given a stream of words we first create a new stream that groups the words according to the identity function, i.e. now we have a stream of streams, where every substream will serve identical words.

To count the words, we need to process the stream of streams (the actual groups containing identical words). groupBy returns a SubFlow , which means that we transform the resulting substreams directly. In this case we use the reduce combinator to aggregate the word itself and the number of its occurrences within a tuple (String, Integer) . Each substream will then emit one final value—precisely such a pair—when the overall input completes. As a last step we merge back these values from the substreams into one single output stream.

One noteworthy detail pertains to the MaximumDistinctWords parameter: this defines the breadth of the groupBy and merge operations. Akka Streams is focused on bounded resource consumption and the number of concurrently open inputs to the merge operator describes the amount of resources needed by the merge itself. Therefore only a finite number of substreams can be active at any given time. If the groupBy operator encounters more keys than this number then the stream cannot continue without violating its resource bound, in this case groupBy will terminate with a failure.

Scala

    val counts: Source[(String, Int), NotUsed] = words
      // split the words into separate streams first
      .groupBy(MaximumDistinctWords, identity)
      //transform each element to pair with number of words in it
      .map(_ -> 1)
      // add counting logic to the streams
      .reduce((l, r) ⇒ (l._1, l._2 + r._2))
      // get a stream of word counts
      .mergeSubstreams

Java

By extracting the parts specific to wordcount into

    a groupKey function that defines the groups
    a map map each element to value that is used by the reduce on the substream
    a reduce function that does the actual reduction

we get a generalized version below:

Scala

    def reduceByKey[In, K, Out](
      maximumGroupSize: Int,
      groupKey:         (In) ⇒ K,
      map:              (In) ⇒ Out)(reduce: (Out, Out) ⇒ Out): Flow[In, (K, Out), NotUsed] = {

      Flow[In]
        .groupBy[K](maximumGroupSize, groupKey)
        .map(e ⇒ groupKey(e) -> map(e))
        .reduce((l, r) ⇒ l._1 -> reduce(l._2, r._2))
        .mergeSubstreams
    }

    val wordCounts = words.via(
      reduceByKey(
        MaximumDistinctWords,
        groupKey = (word: String) ⇒ word,
        map = (word: String) ⇒ 1)((left: Int, right: Int) ⇒ left + right))

Java

Note

Please note that the reduce-by-key version we discussed above is sequential in reading the overall input stream, in other words it is NOT a parallelization pattern like MapReduce and similar frameworks.
Sorting elements to multiple groups with groupBy

Situation: The groupBy operation strictly partitions incoming elements, each element belongs to exactly one group. Sometimes we want to map elements into multiple groups simultaneously.

To achieve the desired result, we attack the problem in two steps:

    first, using a function topicMapper that gives a list of topics (groups) a message belongs to, we transform our stream of Message to a stream of (Message, Topic) where for each topic the message belongs to a separate pair will be emitted. This is achieved by using mapConcat
    Then we take this new stream of message topic pairs (containing a separate pair for each topic a given message belongs to) and feed it into groupBy, using the topic as the group key.

Scala

    val topicMapper: (Message) ⇒ immutable.Seq[Topic] = extractTopics

    val messageAndTopic: Source[(Message, Topic), NotUsed] = elems.mapConcat { msg: Message ⇒
      val topicsForMessage = topicMapper(msg)
      // Create a (Msg, Topic) pair for each of the topics
      // the message belongs to
      topicsForMessage.map(msg -> _)
    }

    val multiGroups = messageAndTopic
      .groupBy(2, _._2).map {
        case (msg, topic) ⇒
          // do what needs to be done
      }

Java

Adhoc source

Situation: The idea is that you have a source which you don’t want to start until you have a demand. Also, you want to shutdown it down when there is no more demand, and start it up again there is new demand again.

You can achieve this behavior by combining lazily, backpressureTimeout and recoverWithRetries as follows:

Scala

    def adhocSource[T](source: Source[T, _], timeout: FiniteDuration, maxRetries: Int): Source[T, _] =
      Source.lazily(
        () ⇒ source.backpressureTimeout(timeout).recoverWithRetries(maxRetries, {
          case t: TimeoutException ⇒
            Source.lazily(() ⇒ source.backpressureTimeout(timeout)).mapMaterializedValue(_ ⇒ NotUsed)
        })
      )

Java


# Working with Graphs

In this collection we show recipes that use stream graph elements to achieve various goals.
Triggering the flow of elements programmatically

Situation: Given a stream of elements we want to control the emission of those elements according to a trigger signal. In other words, even if the stream would be able to flow (not being backpressured) we want to hold back elements until a trigger signal arrives.

This recipe solves the problem by simply zipping the stream of Message elements with the stream of Trigger signals. Since Zip produces pairs, we simply map the output stream selecting the first element of the pair.

Scala

    val graph = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder ⇒
      import GraphDSL.Implicits._
      val zip = builder.add(Zip[Message, Trigger]())
      elements ~> zip.in0
      triggerSource ~> zip.in1
      zip.out ~> Flow[(Message, Trigger)].map { case (msg, trigger) ⇒ msg } ~> sink
      ClosedShape
    })

Java

Alternatively, instead of using a Zip, and then using map to get the first element of the pairs, we can avoid creating the pairs in the first place by using ZipWith which takes a two argument function to produce the output element. If this function would return a pair of the two argument it would be exactly the behavior of Zip so ZipWith is a generalization of zipping.

Scala

    val graph = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder ⇒
      import GraphDSL.Implicits._
      val zip = builder.add(ZipWith((msg: Message, trigger: Trigger) ⇒ msg))

      elements ~> zip.in0
      triggerSource ~> zip.in1
      zip.out ~> sink
      ClosedShape
    })

Java

Balancing jobs to a fixed pool of workers

Situation: Given a stream of jobs and a worker process expressed as a Flow create a pool of workers that automatically balances incoming jobs to available workers, then merges the results.

We will express our solution as a function that takes a worker flow and the number of workers to be allocated and gives a flow that internally contains a pool of these workers. To achieve the desired result we will create a Flow from a graph.

The graph consists of a Balance node which is a special fan-out operation that tries to route elements to available downstream consumers. In a for loop we wire all of our desired workers as outputs of this balancer element, then we wire the outputs of these workers to a Merge element that will collect the results from the workers.

To make the worker stages run in parallel we mark them as asynchronous with async.

Scala

    def balancer[In, Out](worker: Flow[In, Out, Any], workerCount: Int): Flow[In, Out, NotUsed] = {
      import GraphDSL.Implicits._

      Flow.fromGraph(GraphDSL.create() { implicit b ⇒
        val balancer = b.add(Balance[In](workerCount, waitForAllDownstreams = true))
        val merge = b.add(Merge[Out](workerCount))

        for (_ ← 1 to workerCount) {
          // for each worker, add an edge from the balancer to the worker, then wire
          // it to the merge element
          balancer ~> worker.async ~> merge
        }

        FlowShape(balancer.in, merge.out)
      })
    }

    val processedJobs: Source[Result, NotUsed] = myJobs.via(balancer(worker, 3))

Java


# Working with rate

This collection of recipes demonstrate various patterns where rate differences between upstream and downstream needs to be handled by other strategies than simple backpressure.
Dropping elements

Situation: Given a fast producer and a slow consumer, we want to drop elements if necessary to not slow down the producer too much.

This can be solved by using a versatile rate-transforming operation, conflate. Conflate can be thought as a special reduce operation that collapses multiple upstream elements into one aggregate element if needed to keep the speed of the upstream unaffected by the downstream.

When the upstream is faster, the reducing process of the conflate starts. Our reducer function simply takes the freshest element. This in a simple dropping operation.

Scala

    val droppyStream: Flow[Message, Message, NotUsed] =
      Flow[Message].conflate((lastMessage, newMessage) ⇒ newMessage)

Java

There is a more general version of conflate named conflateWithSeed that allows to express more complex aggregations, more similar to a fold.
Dropping broadcast

Situation: The default Broadcast graph element is properly backpressured, but that means that a slow downstream consumer can hold back the other downstream consumers resulting in lowered throughput. In other words the rate of Broadcast is the rate of its slowest downstream consumer. In certain cases it is desirable to allow faster consumers to progress independently of their slower siblings by dropping elements if necessary.

One solution to this problem is to append a buffer element in front of all of the downstream consumers defining a dropping strategy instead of the default Backpressure. This allows small temporary rate differences between the different consumers (the buffer smooths out small rate variances), but also allows faster consumers to progress by dropping from the buffer of the slow consumers if necessary.

Scala

    val graph = RunnableGraph.fromGraph(GraphDSL.create(mySink1, mySink2, mySink3)((_, _, _)) { implicit b ⇒ (sink1, sink2, sink3) ⇒
      import GraphDSL.Implicits._

      val bcast = b.add(Broadcast[Int](3))
      myElements ~> bcast

      bcast.buffer(10, OverflowStrategy.dropHead) ~> sink1
      bcast.buffer(10, OverflowStrategy.dropHead) ~> sink2
      bcast.buffer(10, OverflowStrategy.dropHead) ~> sink3
      ClosedShape
    })

Java

Collecting missed ticks

Situation: Given a regular (stream) source of ticks, instead of trying to backpressure the producer of the ticks we want to keep a counter of the missed ticks instead and pass it down when possible.

We will use conflateWithSeed to solve the problem. The seed version of conflate takes two functions:

    A seed function that produces the zero element for the folding process that happens when the upstream is faster than the downstream. In our case the seed function is a constant function that returns 0 since there were no missed ticks at that point.
    A fold function that is invoked when multiple upstream messages needs to be collapsed to an aggregate value due to the insufficient processing rate of the downstream. Our folding function simply increments the currently stored count of the missed ticks so far.

As a result, we have a flow of Int where the number represents the missed ticks. A number 0 means that we were able to consume the tick fast enough (i.e. zero means: 1 non-missed tick + 0 missed ticks)

Scala

    val missedTicks: Flow[Tick, Int, NotUsed] =
      Flow[Tick].conflateWithSeed(seed = (_) ⇒ 0)(
        (missedTicks, tick) ⇒ missedTicks + 1)

Java

Create a stream processor that repeats the last element seen

Situation: Given a producer and consumer, where the rate of neither is known in advance, we want to ensure that none of them is slowing down the other by dropping earlier unconsumed elements from the upstream if necessary, and repeating the last value for the downstream if necessary.

We have two options to implement this feature. In both cases we will use GraphStage to build our custom element. In the first version we will use a provided initial value initial that will be used to feed the downstream if no upstream element is ready yet. In the onPush() handler we just overwrite the currentValue variable and immediately relieve the upstream by calling pull(). The downstream onPull handler is very similar, we immediately relieve the downstream by emitting currentValue.

Scala

    import akka.stream._
    import akka.stream.stage._
    final class HoldWithInitial[T](initial: T) extends GraphStage[FlowShape[T, T]] {
      val in = Inlet[T]("HoldWithInitial.in")
      val out = Outlet[T]("HoldWithInitial.out")

      override val shape = FlowShape.of(in, out)

      override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
        private var currentValue: T = initial

        setHandlers(in, out, new InHandler with OutHandler {
          override def onPush(): Unit = {
            currentValue = grab(in)
            pull(in)
          }

          override def onPull(): Unit = {
            push(out, currentValue)
          }
        })

        override def preStart(): Unit = {
          pull(in)
        }
      }

    }

Java

While it is relatively simple, the drawback of the first version is that it needs an arbitrary initial element which is not always possible to provide. Hence, we create a second version where the downstream might need to wait in one single case: if the very first element is not yet available.

We introduce a boolean variable waitingFirstValue to denote whether the first element has been provided or not (alternatively an Option can be used for currentValue or if the element type is a subclass of AnyRef a null can be used with the same purpose). In the downstream onPull() handler the difference from the previous version is that we check if we have received the first value and only emit if we have. This leads to that when the first element comes in we must check if there possibly already was demand from downstream so that we in that case can push the element directly.

Scala

    import akka.stream._
    import akka.stream.stage._
    final class HoldWithWait[T] extends GraphStage[FlowShape[T, T]] {
      val in = Inlet[T]("HoldWithWait.in")
      val out = Outlet[T]("HoldWithWait.out")

      override val shape = FlowShape.of(in, out)

      override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
        private var currentValue: T = _
        private var waitingFirstValue = true

        setHandlers(in, out, new InHandler with OutHandler {
          override def onPush(): Unit = {
            currentValue = grab(in)
            if (waitingFirstValue) {
              waitingFirstValue = false
              if (isAvailable(out)) push(out, currentValue)
            }
            pull(in)
          }

          override def onPull(): Unit = {
            if (!waitingFirstValue) push(out, currentValue)
          }
        })

        override def preStart(): Unit = {
          pull(in)
        }
      }
    }

Java

Globally limiting the rate of a set of streams

Situation: Given a set of independent streams that we cannot merge, we want to globally limit the aggregate throughput of the set of streams.

One possible solution uses a shared actor as the global limiter combined with mapAsync to create a reusable Flow that can be plugged into a stream to limit its rate.

As the first step we define an actor that will do the accounting for the global rate limit. The actor maintains a timer, a counter for pending permit tokens and a queue for possibly waiting participants. The actor has an open and closed state. The actor is in the open state while it has still pending permits. Whenever a request for permit arrives as a WantToPass message to the actor the number of available permits is decremented and we notify the sender that it can pass by answering with a MayPass message. If the amount of permits reaches zero, the actor transitions to the closed state. In this state requests are not immediately answered, instead the reference of the sender is added to a queue. Once the timer for replenishing the pending permits fires by sending a ReplenishTokens message, we increment the pending permits counter and send a reply to each of the waiting senders. If there are more waiting senders than permits available we will stay in the closed state.

Scala

    object Limiter {
      case object WantToPass
      case object MayPass

      case object ReplenishTokens

      def props(maxAvailableTokens: Int, tokenRefreshPeriod: FiniteDuration,
                tokenRefreshAmount: Int): Props =
        Props(new Limiter(maxAvailableTokens, tokenRefreshPeriod, tokenRefreshAmount))
    }

    class Limiter(
      val maxAvailableTokens: Int,
      val tokenRefreshPeriod: FiniteDuration,
      val tokenRefreshAmount: Int) extends Actor {
      import Limiter._
      import context.dispatcher
      import akka.actor.Status

      private var waitQueue = immutable.Queue.empty[ActorRef]
      private var permitTokens = maxAvailableTokens
      private val replenishTimer = system.scheduler.schedule(
        initialDelay = tokenRefreshPeriod,
        interval = tokenRefreshPeriod,
        receiver = self,
        ReplenishTokens)

      override def receive: Receive = open

      val open: Receive = {
        case ReplenishTokens ⇒
          permitTokens = math.min(permitTokens + tokenRefreshAmount, maxAvailableTokens)
        case WantToPass ⇒
          permitTokens -= 1
          sender() ! MayPass
          if (permitTokens == 0) context.become(closed)
      }

      val closed: Receive = {
        case ReplenishTokens ⇒
          permitTokens = math.min(permitTokens + tokenRefreshAmount, maxAvailableTokens)
          releaseWaiting()
        case WantToPass ⇒
          waitQueue = waitQueue.enqueue(sender())
      }

      private def releaseWaiting(): Unit = {
        val (toBeReleased, remainingQueue) = waitQueue.splitAt(permitTokens)
        waitQueue = remainingQueue
        permitTokens -= toBeReleased.size
        toBeReleased foreach (_ ! MayPass)
        if (permitTokens > 0) context.become(open)
      }

      override def postStop(): Unit = {
        replenishTimer.cancel()
        waitQueue foreach (_ ! Status.Failure(new IllegalStateException("limiter stopped")))
      }
    }

Java

To create a Flow that uses this global limiter actor we use the mapAsync function with the combination of the ask pattern. We also define a timeout, so if a reply is not received during the configured maximum wait period the returned future from ask will fail, which will fail the corresponding stream as well.

Scala

    def limitGlobal[T](limiter: ActorRef, maxAllowedWait: FiniteDuration): Flow[T, T, NotUsed] = {
      import akka.pattern.ask
      import akka.util.Timeout
      Flow[T].mapAsync(4)((element: T) ⇒ {
        import system.dispatcher
        implicit val triggerTimeout = Timeout(maxAllowedWait)
        val limiterTriggerFuture = limiter ? Limiter.WantToPass
        limiterTriggerFuture.map((_) ⇒ element)
      })

    }

Java

Note

The global actor used for limiting introduces a global bottleneck. You might want to assign a dedicated dispatcher for this actor.

# Working with IO
Chunking up a stream of ByteStrings into limited size ByteStrings

Situation: Given a stream of ByteString s we want to produce a stream of ByteString s containing the same bytes in the same sequence, but capping the size of ByteString s. In other words we want to slice up ByteString s into smaller chunks if they exceed a size threshold.

This can be achieved with a single GraphStage. The main logic of our stage is in emitChunk() which implements the following logic:

    if the buffer is empty, and upstream is not closed we pull for more bytes, if it is closed we complete
    if the buffer is nonEmpty, we split it according to the chunkSize. This will give a next chunk that we will emit, and an empty or nonempty remaining buffer.

Both onPush() and onPull() calls emitChunk() the only difference is that the push handler also stores the incoming chunk by appending to the end of the buffer.

Scala

    import akka.stream.stage._

    class Chunker(val chunkSize: Int) extends GraphStage[FlowShape[ByteString, ByteString]] {
      val in = Inlet[ByteString]("Chunker.in")
      val out = Outlet[ByteString]("Chunker.out")
      override val shape = FlowShape.of(in, out)

      override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
        private var buffer = ByteString.empty

        setHandler(out, new OutHandler {
          override def onPull(): Unit = {
            if (isClosed(in)) emitChunk()
            else pull(in)
          }
        })
        setHandler(in, new InHandler {
          override def onPush(): Unit = {
            val elem = grab(in)
            buffer ++= elem
            emitChunk()
          }

          override def onUpstreamFinish(): Unit = {
            if (buffer.isEmpty) completeStage()
            else {
              // There are elements left in buffer, so
              // we keep accepting downstream pulls and push from buffer until emptied.
              //
              // It might be though, that the upstream finished while it was pulled, in which
              // case we will not get an onPull from the downstream, because we already had one.
              // In that case we need to emit from the buffer.
              if (isAvailable(out)) emitChunk()
            }
          }
        })

        private def emitChunk(): Unit = {
          if (buffer.isEmpty) {
            if (isClosed(in)) completeStage()
            else pull(in)
          } else {
            val (chunk, nextBuffer) = buffer.splitAt(chunkSize)
            buffer = nextBuffer
            push(out, chunk)
          }
        }

      }
    }

    val chunksStream = rawBytes.via(new Chunker(ChunkLimit))

Java

Limit the number of bytes passing through a stream of ByteStrings

Situation: Given a stream of ByteString s we want to fail the stream if more than a given maximum of bytes has been consumed.

This recipe uses a GraphStage to implement the desired feature. In the only handler we override, onPush() we just update a counter and see if it gets larger than maximumBytes. If a violation happens we signal failure, otherwise we forward the chunk we have received.

Scala

    import akka.stream.stage._
    class ByteLimiter(val maximumBytes: Long) extends GraphStage[FlowShape[ByteString, ByteString]] {
      val in = Inlet[ByteString]("ByteLimiter.in")
      val out = Outlet[ByteString]("ByteLimiter.out")
      override val shape = FlowShape.of(in, out)

      override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
        private var count = 0

        setHandlers(in, out, new InHandler with OutHandler {

          override def onPull(): Unit = {
            pull(in)
          }

          override def onPush(): Unit = {
            val chunk = grab(in)
            count += chunk.size
            if (count > maximumBytes) failStage(new IllegalStateException("Too much bytes"))
            else push(out, chunk)
          }
        })
      }
    }

    val limiter = Flow[ByteString].via(new ByteLimiter(SizeLimit))

Java

Compact ByteStrings in a stream of ByteStrings

Situation: After a long stream of transformations, due to their immutable, structural sharing nature ByteString s may refer to multiple original ByteString instances unnecessarily retaining memory. As the final step of a transformation chain we want to have clean copies that are no longer referencing the original ByteString s.

The recipe is a simple use of map, calling the compact() method of the ByteString elements. This does copying of the underlying arrays, so this should be the last element of a long chain if used.

Scala

    val compacted: Source[ByteString, NotUsed] = data.map(_.compact)

Java

Injecting keep-alive messages into a stream of ByteStrings

Situation: Given a communication channel expressed as a stream of ByteString s we want to inject keep-alive messages but only if this does not interfere with normal traffic.

There is a built-in operation that allows to do this directly:

Scala

    import scala.concurrent.duration._
    val injectKeepAlive: Flow[ByteString, ByteString, NotUsed] =
      Flow[ByteString].keepAlive(1.second, () ⇒ keepaliveMessage)

Java

