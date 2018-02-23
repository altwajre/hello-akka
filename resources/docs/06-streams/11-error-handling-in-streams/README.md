# Error Handling in Streams - Overview

When a stage in a stream fails this will normally lead to the entire stream being torn down. Each of the stages downstream gets informed about the failure and each upstream stage sees a cancellation.

In many cases you may want to avoid complete stream failure, this can be done in a few different ways:

    recover to emit a final element then complete the stream normally on upstream failure
    recoverWithRetries to create a new upstream and start consuming from that on failure
    Restarting sections of the stream after a backoff
    Using a supervision strategy for stages that support it

In addition to these built in tools for error handling, a common pattern is to wrap the stream inside an actor, and have the actor restart the entire stream on failure.

# Recover

recover allows you to emit a final element and then complete the stream on an upstream failure. Deciding which exceptions should be recovered is done through a PartialFunction. If an exception does not have a matching case the stream is failed.

Recovering can be useful if you want to gracefully complete a stream on failure while letting downstream know that there was a failure.

```scala

    Source(0 to 6).map(n ⇒
      if (n < 5) n.toString
      else throw new RuntimeException("Boom!")
    ).recover {
      case _: RuntimeException ⇒ "stream truncated"
    }.runForeach(println)

```

This will output:

```scala

    0
    1
    2
    3
    4
    stream truncated

```


# Recover with retries

recoverWithRetries allows you to put a new upstream in place of the failed one, recovering stream failures up to a specified maximum number of times.

Deciding which exceptions should be recovered is done through a PartialFunction. If an exception does not have a matching case the stream is failed.

```scala

    val planB = Source(List("five", "six", "seven", "eight"))

    Source(0 to 10).map(n ⇒
      if (n < 5) n.toString
      else throw new RuntimeException("Boom!")
    ).recoverWithRetries(attempts = 1, {
      case _: RuntimeException ⇒ planB
    }).runForeach(println)

```

This will output:

```scala

    0
    1
    2
    3
    4
    five
    six
    seven
    eight

```


# Delayed restarts with a backoff stage

Just as Akka provides the backoff supervision pattern for actors, Akka streams also provides a RestartSource, RestartSink and RestartFlow for implementing the so-called exponential backoff supervision strategy, starting a stage again when it fails or completes, each time with a growing time delay between restarts.

This pattern is useful when the stage fails or completes because some external resource is not available and we need to give it some time to start-up again. One of the prime examples when this is useful is when a WebSocket connection fails due to the HTTP server it’s running on going down, perhaps because it is overloaded. By using an exponential backoff, we avoid going into a tight reconnect loop, which both gives the HTTP server some time to recover, and it avoids using needless resources on the client side.

The following snippet shows how to create a backoff supervisor using akka.stream.scaladsl.RestartSource which will supervise the given Source. The Source in this case is a stream of Server Sent Events, produced by akka-http. If the stream fails or completes at any point, the request will be made again, in increasing intervals of 3, 6, 12, 24 and finally 30 seconds (at which point it will remain capped due to the maxBackoff parameter):

```scala

    val restartSource = RestartSource.withBackoff(
      minBackoff = 3.seconds,
      maxBackoff = 30.seconds,
      randomFactor = 0.2 // adds 20% "noise" to vary the intervals slightly
    ) { () ⇒
      // Create a source from a future of a source
      Source.fromFutureSource {
        // Make a single request with akka-http
        Http().singleRequest(HttpRequest(
          uri = "http://example.com/eventstream"
        ))
          // Unmarshall it as a source of server sent events
          .flatMap(Unmarshal(_).to[Source[ServerSentEvent, NotUsed]])
      }
    }

```

Using a randomFactor to add a little bit of additional variance to the backoff intervals is highly recommended, in order to avoid multiple streams re-start at the exact same point in time, for example because they were stopped due to a shared resource such as the same server going down and re-starting after the same configured interval. By adding additional randomness to the re-start intervals the streams will start in slightly different points in time, thus avoiding large spikes of traffic hitting the recovering server or other resource that they all need to contact.

The above RestartSource will never terminate unless the Sink it’s fed into cancels. It will often be handy to use it in combination with a KillSwitch, so that you can terminate it when needed:

```scala

    val killSwitch = restartSource
      .viaMat(KillSwitches.single)(Keep.right)
      .toMat(Sink.foreach(event ⇒ println(s"Got event: $event")))(Keep.left)
      .run()

    doSomethingElse()

    killSwitch.shutdown()

```

Sinks and flows can also be supervised, using akka.stream.scaladsl.RestartSink and akka.stream.scaladsl.RestartFlow . The RestartSink is restarted when it cancels, while the RestartFlow is restarted when either the in port cancels, the out port completes, or the out port sends an error.

# Supervision Strategies

#### Note

The stages that support supervision strategies are explicitly documented to do so, if there is nothing in the documentation of a stage saying that it adheres to the supervision strategy it means it fails rather than applies supervision.

The error handling strategies are inspired by actor supervision strategies, but the semantics have been adapted to the domain of stream processing. The most important difference is that supervision is not automatically applied to stream stages but instead something that each stage has to implement explicitly.

For many stages it may not even make sense to implement support for supervision strategies, this is especially true for stages connecting to external technologies where for example a failed connection will likely still fail if a new connection is tried immediately (see Restart with back off for such scenarios).

For stages that do implement supervision, the strategies for how to handle exceptions from processing stream elements can be selected when materializing the stream through use of an attribute.

There are three ways to handle exceptions from application code:

    Stop - The stream is completed with failure.
    Resume - The element is dropped and the stream continues.
    Restart - The element is dropped and the stream continues after restarting the stage. Restarting a stage means that any accumulated state is cleared. This is typically performed by creating a new instance of the stage.

By default the stopping strategy is used for all exceptions, i.e. the stream will be completed with failure when an exception is thrown.

```scala

    implicit val materializer = ActorMaterializer()
    val source = Source(0 to 5).map(100 / _)
    val result = source.runWith(Sink.fold(0)(_ + _))
    // division by zero will fail the stream and the
    // result here will be a Future completed with Failure(ArithmeticException)

```

The default supervision strategy for a stream can be defined on the settings of the materializer.

```scala

    val decider: Supervision.Decider = {
      case _: ArithmeticException ⇒ Supervision.Resume
      case _                      ⇒ Supervision.Stop
    }
    implicit val materializer = ActorMaterializer(
      ActorMaterializerSettings(system).withSupervisionStrategy(decider))
    val source = Source(0 to 5).map(100 / _)
    val result = source.runWith(Sink.fold(0)(_ + _))
    // the element causing division by zero will be dropped
    // result here will be a Future completed with Success(228)

```

Here you can see that all ArithmeticException will resume the processing, i.e. the elements that cause the division by zero are effectively dropped.

#### Note

Be aware that dropping elements may result in deadlocks in graphs with cycles, as explained in Graph cycles, liveness and deadlocks.

The supervision strategy can also be defined for all operators of a flow.

```scala

    implicit val materializer = ActorMaterializer()
    val decider: Supervision.Decider = {
      case _: ArithmeticException ⇒ Supervision.Resume
      case _                      ⇒ Supervision.Stop
    }
    val flow = Flow[Int]
      .filter(100 / _ < 50).map(elem ⇒ 100 / (5 - elem))
      .withAttributes(ActorAttributes.supervisionStrategy(decider))
    val source = Source(0 to 5).via(flow)

    val result = source.runWith(Sink.fold(0)(_ + _))
    // the elements causing division by zero will be dropped
    // result here will be a Future completed with Success(150)

```

Restart works in a similar way as Resume with the addition that accumulated state, if any, of the failing processing stage will be reset.

```scala

    implicit val materializer = ActorMaterializer()
    val decider: Supervision.Decider = {
      case _: IllegalArgumentException ⇒ Supervision.Restart
      case _                           ⇒ Supervision.Stop
    }
    val flow = Flow[Int]
      .scan(0) { (acc, elem) ⇒
        if (elem < 0) throw new IllegalArgumentException("negative not allowed")
        else acc + elem
      }
      .withAttributes(ActorAttributes.supervisionStrategy(decider))
    val source = Source(List(1, 3, -1, 5, 7)).via(flow)
    val result = source.limit(1000).runWith(Sink.seq)
    // the negative element cause the scan stage to be restarted,
    // i.e. start from 0 again
    // result here will be a Future completed with Success(Vector(0, 1, 4, 0, 5, 12))

```


## Errors from mapAsync

Stream supervision can also be applied to the futures of mapAsync and mapAsyncUnordered even if such failures happen in the future rather than inside the stage itself.

Let’s say that we use an external service to lookup email addresses and we would like to discard those that cannot be found.

We start with the tweet stream of authors:

```scala

    val authors: Source[Author, NotUsed] =
      tweets
        .filter(_.hashtags.contains(akkaTag))
        .map(_.author)

```

Assume that we can lookup their email address using:

```scala

    def lookupEmail(handle: String): Future[String] =

```

The Future is completed with Failure if the email is not found.

Transforming the stream of authors to a stream of email addresses by using the lookupEmail service can be done with mapAsync and we use Supervision.resumingDecider to drop unknown email addresses:

```scala

    import ActorAttributes.supervisionStrategy
    import Supervision.resumingDecider

    val emailAddresses: Source[String, NotUsed] =
      authors.via(
        Flow[Author].mapAsync(4)(author ⇒ addressSystem.lookupEmail(author.handle))
          .withAttributes(supervisionStrategy(resumingDecider)))

```

If we would not use Resume the default stopping strategy would complete the stream with failure on the first Future that was completed with Failure.
