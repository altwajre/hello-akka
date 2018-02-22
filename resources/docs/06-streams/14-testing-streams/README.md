# Testing streams - Overview

Verifying behaviour of Akka Stream sources, flows and sinks can be done using various code patterns and libraries. Here we will discuss testing these elements using:

    simple sources, sinks and flows;
    sources and sinks in combination with TestProbe from the akka-testkit module;
    sources and sinks specifically crafted for writing tests from the akka-stream-testkit module.

It is important to keep your data processing pipeline as separate sources, flows and sinks. This makes them easily testable by wiring them up to other sources or sinks, or some test harnesses that akka-testkit or akka-stream-testkit provide.

# Built in sources, sinks and combinators

Testing a custom sink can be as simple as attaching a source that emits elements from a predefined collection, running a constructed test flow and asserting on the results that sink produced. Here is an example of a test for a sink:

Scala

    val sinkUnderTest = Flow[Int].map(_ * 2).toMat(Sink.fold(0)(_ + _))(Keep.right)

    val future = Source(1 to 4).runWith(sinkUnderTest)
    val result = Await.result(future, 3.seconds)
    assert(result == 20)

Java

The same strategy can be applied for sources as well. In the next example we have a source that produces an infinite stream of elements. Such source can be tested by asserting that first arbitrary number of elements hold some condition. Here the take combinator and Sink.seq are very useful.

Scala

    import system.dispatcher
    import akka.pattern.pipe

    val sourceUnderTest = Source.repeat(1).map(_ * 2)

    val future = sourceUnderTest.take(10).runWith(Sink.seq)
    val result = Await.result(future, 3.seconds)
    assert(result == Seq.fill(10)(2))

Java

When testing a flow we need to attach a source and a sink. As both stream ends are under our control, we can choose sources that tests various edge cases of the flow and sinks that ease assertions.

Scala

    val flowUnderTest = Flow[Int].takeWhile(_ < 5)

    val future = Source(1 to 10).via(flowUnderTest).runWith(Sink.fold(Seq.empty[Int])(_ :+ _))
    val result = Await.result(future, 3.seconds)
    assert(result == (1 to 4))

Java


# TestKit

Akka Stream offers integration with Actors out of the box. This support can be used for writing stream tests that use familiar TestProbe from the akka-testkit API.

One of the more straightforward tests would be to materialize stream to a Future and then use pipe pattern to pipe the result of that future to the probe.

Scala

    import system.dispatcher
    import akka.pattern.pipe

    val sourceUnderTest = Source(1 to 4).grouped(2)

    val probe = TestProbe()
    sourceUnderTest.runWith(Sink.seq).pipeTo(probe.ref)
    probe.expectMsg(3.seconds, Seq(Seq(1, 2), Seq(3, 4)))

Java

Instead of materializing to a future, we can use a Sink.actorRef that sends all incoming elements to the given ActorRef. Now we can use assertion methods on TestProbe and expect elements one by one as they arrive. We can also assert stream completion by expecting for onCompleteMessage which was given to Sink.actorRef.

Scala

    case object Tick
    val sourceUnderTest = Source.tick(0.seconds, 200.millis, Tick)

    val probe = TestProbe()
    val cancellable = sourceUnderTest.to(Sink.actorRef(probe.ref, "completed")).run()

    probe.expectMsg(1.second, Tick)
    probe.expectNoMsg(100.millis)
    probe.expectMsg(3.seconds, Tick)
    cancellable.cancel()
    probe.expectMsg(3.seconds, "completed")

Java

Similarly to Sink.actorRef that provides control over received elements, we can use Source.actorRef and have full control over elements to be sent.

Scala

    val sinkUnderTest = Flow[Int].map(_.toString).toMat(Sink.fold("")(_ + _))(Keep.right)

    val (ref, future) = Source.actorRef(8, OverflowStrategy.fail)
      .toMat(sinkUnderTest)(Keep.both).run()

    ref ! 1
    ref ! 2
    ref ! 3
    ref ! akka.actor.Status.Success("done")

    val result = Await.result(future, 3.seconds)
    assert(result == "123")

Java


# Streams TestKit

You may have noticed various code patterns that emerge when testing stream pipelines. Akka Stream has a separate akka-stream-testkit module that provides tools specifically for writing stream tests. This module comes with two main components that are TestSource and TestSink which provide sources and sinks that materialize to probes that allow fluent API.
Note

Be sure to add the module akka-stream-testkit to your dependencies.

A sink returned by TestSink.probe allows manual control over demand and assertions over elements coming downstream.

Scala

    val sourceUnderTest = Source(1 to 4).filter(_ % 2 == 0).map(_ * 2)

    sourceUnderTest
      .runWith(TestSink.probe[Int])
      .request(2)
      .expectNext(4, 8)
      .expectComplete()

Java

A source returned by TestSource.probe can be used for asserting demand or controlling when stream is completed or ended with an error.

Scala

    val sinkUnderTest = Sink.cancelled

    TestSource.probe[Int]
      .toMat(sinkUnderTest)(Keep.left)
      .run()
      .expectCancellation()

Java

You can also inject exceptions and test sink behaviour on error conditions.

Scala

    val sinkUnderTest = Sink.head[Int]

    val (probe, future) = TestSource.probe[Int]
      .toMat(sinkUnderTest)(Keep.both)
      .run()
    probe.sendError(new Exception("boom"))

    Await.ready(future, 3.seconds)
    val Failure(exception) = future.value.get
    assert(exception.getMessage == "boom")

Java

Test source and sink can be used together in combination when testing flows.

Scala

    val flowUnderTest = Flow[Int].mapAsyncUnordered(2) { sleep ⇒
      pattern.after(10.millis * sleep, using = system.scheduler)(Future.successful(sleep))
    }

    val (pub, sub) = TestSource.probe[Int]
      .via(flowUnderTest)
      .toMat(TestSink.probe[Int])(Keep.both)
      .run()

    sub.request(n = 3)
    pub.sendNext(3)
    pub.sendNext(2)
    pub.sendNext(1)
    sub.expectNextUnordered(1, 2, 3)

    pub.sendError(new Exception("Power surge in the linear subroutine C-47!"))
    val ex = sub.expectError()
    assert(ex.getMessage.contains("C-47"))

Java


# Fuzzing Mode

For testing, it is possible to enable a special stream execution mode that exercises concurrent execution paths more aggressively (at the cost of reduced performance) and therefore helps exposing race conditions in tests. To enable this setting add the following line to your configuration:

akka.stream.materializer.debug.fuzzing-mode = on

Warning

Never use this setting in production or benchmarks. This is a testing tool to provide more coverage of your code during tests, but it reduces the throughput of streams. A warning message will be logged if you have this setting enabled.
