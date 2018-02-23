# Dynamic stream handling - Overview

# Controlling graph completion with KillSwitch

A KillSwitch allows the completion of graphs of FlowShape from the outside. It consists of a flow element that can be linked to a graph of FlowShape needing completion control. The KillSwitch trait allows to:

    complete the graph(s) via shutdown()
    fail the graph(s) via abort(Throwable error)

Scala

    trait KillSwitch {
      /**
       * After calling [[KillSwitch#shutdown()]] the linked [[Graph]]s of [[FlowShape]] are completed normally.
       */
      def shutdown(): Unit
      /**
       * After calling [[KillSwitch#abort()]] the linked [[Graph]]s of [[FlowShape]] are failed.
       */
      def abort(ex: Throwable): Unit
    }

After the first call to either shutdown or abort, all subsequent calls to any of these methods will be ignored. Graph completion is performed by both

    completing its downstream
    cancelling (in case of shutdown) or failing (in case of abort) its upstream.

A KillSwitch can control the completion of one or multiple streams, and therefore comes in two different flavours.

## UniqueKillSwitch

UniqueKillSwitch allows to control the completion of one materialized Graph of FlowShape. Refer to the below for usage examples.

    Shutdown

Scala

    val countingSrc = Source(Stream.from(1)).delay(1.second, DelayOverflowStrategy.backpressure)
    val lastSnk = Sink.last[Int]

    val (killSwitch, last) = countingSrc
      .viaMat(KillSwitches.single)(Keep.right)
      .toMat(lastSnk)(Keep.both)
      .run()

    doSomethingElse()

    killSwitch.shutdown()

    Await.result(last, 1.second) shouldBe 2

Java

    Abort

Scala

    val countingSrc = Source(Stream.from(1)).delay(1.second, DelayOverflowStrategy.backpressure)
    val lastSnk = Sink.last[Int]

    val (killSwitch, last) = countingSrc
      .viaMat(KillSwitches.single)(Keep.right)
      .toMat(lastSnk)(Keep.both).run()

    val error = new RuntimeException("boom!")
    killSwitch.abort(error)

    Await.result(last.failed, 1.second) shouldBe error

Java


## SharedKillSwitch

A SharedKillSwitch allows to control the completion of an arbitrary number graphs of FlowShape. It can be materialized multiple times via its flow method, and all materialized graphs linked to it are controlled by the switch. Refer to the below for usage examples.

    Shutdown

Scala

    val countingSrc = Source(Stream.from(1)).delay(1.second, DelayOverflowStrategy.backpressure)
    val lastSnk = Sink.last[Int]
    val sharedKillSwitch = KillSwitches.shared("my-kill-switch")

    val last = countingSrc
      .via(sharedKillSwitch.flow)
      .runWith(lastSnk)

    val delayedLast = countingSrc
      .delay(1.second, DelayOverflowStrategy.backpressure)
      .via(sharedKillSwitch.flow)
      .runWith(lastSnk)

    doSomethingElse()

    sharedKillSwitch.shutdown()

    Await.result(last, 1.second) shouldBe 2
    Await.result(delayedLast, 1.second) shouldBe 1

Java

    Abort

Scala

    val countingSrc = Source(Stream.from(1)).delay(1.second)
    val lastSnk = Sink.last[Int]
    val sharedKillSwitch = KillSwitches.shared("my-kill-switch")

    val last1 = countingSrc.via(sharedKillSwitch.flow).runWith(lastSnk)
    val last2 = countingSrc.via(sharedKillSwitch.flow).runWith(lastSnk)

    val error = new RuntimeException("boom!")
    sharedKillSwitch.abort(error)

    Await.result(last1.failed, 1.second) shouldBe error
    Await.result(last2.failed, 1.second) shouldBe error

Java

Note

A UniqueKillSwitch is always a result of a materialization, whilst SharedKillSwitch needs to be constructed before any materialization takes place.

# Dynamic fan-in and fan-out with MergeHub, BroadcastHub and PartitionHub

There are many cases when consumers or producers of a certain service (represented as a Sink, Source, or possibly Flow) are dynamic and not known in advance. The Graph DSL does not allow to represent this, all connections of the graph must be known in advance and must be connected upfront. To allow dynamic fan-in and fan-out streaming, the Hubs should be used. They provide means to construct Sink and Source pairs that are “attached” to each other, but one of them can be materialized multiple times to implement dynamic fan-in or fan-out.

## Using the MergeHub

A MergeHub allows to implement a dynamic fan-in junction point in a graph where elements coming from different producers are emitted in a First-Comes-First-Served fashion. If the consumer cannot keep up then all of the producers are backpressured. The hub itself comes as a Source to which the single consumer can be attached. It is not possible to attach any producers until this Source has been materialized (started). This is ensured by the fact that we only get the corresponding Sink as a materialized value. Usage might look like this:

Scala

    // A simple consumer that will print to the console for now
    val consumer = Sink.foreach(println)

    // Attach a MergeHub Source to the consumer. This will materialize to a
    // corresponding Sink.
    val runnableGraph: RunnableGraph[Sink[String, NotUsed]] =
      MergeHub.source[String](perProducerBufferSize = 16).to(consumer)

    // By running/materializing the consumer we get back a Sink, and hence
    // now have access to feed elements into it. This Sink can be materialized
    // any number of times, and every element that enters the Sink will
    // be consumed by our consumer.
    val toConsumer: Sink[String, NotUsed] = runnableGraph.run()

    // Feeding two independent sources into the hub.
    Source.single("Hello!").runWith(toConsumer)
    Source.single("Hub!").runWith(toConsumer)

Java

This sequence, while might look odd at first, ensures proper startup order. Once we get the Sink, we can use it as many times as wanted. Everything that is fed to it will be delivered to the consumer we attached previously until it cancels.

## Using the BroadcastHub

A BroadcastHub can be used to consume elements from a common producer by a dynamic set of consumers. The rate of the producer will be automatically adapted to the slowest consumer. In this case, the hub is a Sink to which the single producer must be attached first. Consumers can only be attached once the Sink has been materialized (i.e. the producer has been started). One example of using the BroadcastHub:

Scala

    // A simple producer that publishes a new "message" every second
    val producer = Source.tick(1.second, 1.second, "New message")

    // Attach a BroadcastHub Sink to the producer. This will materialize to a
    // corresponding Source.
    // (We need to use toMat and Keep.right since by default the materialized
    // value to the left is used)
    val runnableGraph: RunnableGraph[Source[String, NotUsed]] =
      producer.toMat(BroadcastHub.sink(bufferSize = 256))(Keep.right)

    // By running/materializing the producer, we get back a Source, which
    // gives us access to the elements published by the producer.
    val fromProducer: Source[String, NotUsed] = runnableGraph.run()

    // Print out messages from the producer in two independent consumers
    fromProducer.runForeach(msg ⇒ println("consumer1: " + msg))
    fromProducer.runForeach(msg ⇒ println("consumer2: " + msg))

Java

The resulting Source can be materialized any number of times, each materialization effectively attaching a new subscriber. If there are no subscribers attached to this hub then it will not drop any elements but instead backpressure the upstream producer until subscribers arrive. This behavior can be tweaked by using the combinators .buffer for example with a drop strategy, or just attaching a subscriber that drops all messages. If there are no other subscribers, this will ensure that the producer is kept drained (dropping all elements) and once a new subscriber arrives it will adaptively slow down, ensuring no more messages are dropped.

## Combining dynamic stages to build a simple Publish-Subscribe service

The features provided by the Hub implementations are limited by default. This is by design, as various combinations can be used to express additional features like unsubscribing producers or consumers externally. We show here an example that builds a Flow representing a publish-subscribe channel. The input of the Flow is published to all subscribers while the output streams all the elements published.

First, we connect a MergeHub and a BroadcastHub together to form a publish-subscribe channel. Once we materialize this small stream, we get back a pair of Source and Sink that together define the publish and subscribe sides of our channel.

Scala

    // Obtain a Sink and Source which will publish and receive from the "bus" respectively.
    val (sink, source) =
      MergeHub.source[String](perProducerBufferSize = 16)
        .toMat(BroadcastHub.sink(bufferSize = 256))(Keep.both)
        .run()

Java

We now use a few tricks to add more features. First of all, we attach a Sink.ignore at the broadcast side of the channel to keep it drained when there are no subscribers. If this behavior is not the desired one this line can be simply dropped.

Scala

    // Ensure that the Broadcast output is dropped if there are no listening parties.
    // If this dropping Sink is not attached, then the broadcast hub will not drop any
    // elements itself when there are no subscribers, backpressuring the producer instead.
    source.runWith(Sink.ignore)

Java

We now wrap the Sink and Source in a Flow using Flow.fromSinkAndSource. This bundles up the two sides of the channel into one and forces users of it to always define a publisher and subscriber side (even if the subscriber side is just dropping). It also allows us to very simply attach a KillSwitch as a BidiStage which in turn makes it possible to close both the original Sink and Source at the same time. Finally, we add backpressureTimeout on the consumer side to ensure that subscribers that block the channel for more than 3 seconds are forcefully removed (and their stream failed).

Scala

    // We create now a Flow that represents a publish-subscribe channel using the above
    // started stream as its "topic". We add two more features, external cancellation of
    // the registration and automatic cleanup for very slow subscribers.
    val busFlow: Flow[String, String, UniqueKillSwitch] =
      Flow.fromSinkAndSource(sink, source)
        .joinMat(KillSwitches.singleBidi[String, String])(Keep.right)
        .backpressureTimeout(3.seconds)

Java

The resulting Flow now has a type of Flow[String, String, UniqueKillSwitch] representing a publish-subscribe channel which can be used any number of times to attach new producers or consumers. In addition, it materializes to a UniqueKillSwitch (see UniqueKillSwitch) that can be used to deregister a single user externally:

Scala

    val switch: UniqueKillSwitch =
      Source.repeat("Hello world!")
        .viaMat(busFlow)(Keep.right)
        .to(Sink.foreach(println))
        .run()

    // Shut down externally
    switch.shutdown()

Java


## Using the PartitionHub

This is a may change feature*

A PartitionHub can be used to route elements from a common producer to a dynamic set of consumers. The selection of consumer is done with a function. Each element can be routed to only one consumer.

The rate of the producer will be automatically adapted to the slowest consumer. In this case, the hub is a Sink to which the single producer must be attached first. Consumers can only be attached once the Sink has been materialized (i.e. the producer has been started). One example of using the PartitionHub:

Scala

    // A simple producer that publishes a new "message-" every second
    val producer = Source.tick(1.second, 1.second, "message")
      .zipWith(Source(1 to 100))((a, b) ⇒ s"$a-$b")

    // Attach a PartitionHub Sink to the producer. This will materialize to a
    // corresponding Source.
    // (We need to use toMat and Keep.right since by default the materialized
    // value to the left is used)
    val runnableGraph: RunnableGraph[Source[String, NotUsed]] =
      producer.toMat(PartitionHub.sink(
        (size, elem) ⇒ math.abs(elem.hashCode) % size,
        startAfterNrOfConsumers = 2, bufferSize = 256))(Keep.right)

    // By running/materializing the producer, we get back a Source, which
    // gives us access to the elements published by the producer.
    val fromProducer: Source[String, NotUsed] = runnableGraph.run()

    // Print out messages from the producer in two independent consumers
    fromProducer.runForeach(msg ⇒ println("consumer1: " + msg))
    fromProducer.runForeach(msg ⇒ println("consumer2: " + msg))

Java

The partitioner function takes two parameters; the first is the number of active consumers and the second is the stream element. The function should return the index of the selected consumer for the given element, i.e. int greater than or equal to 0 and less than number of consumers.

The resulting Source can be materialized any number of times, each materialization effectively attaching a new consumer. If there are no consumers attached to this hub then it will not drop any elements but instead backpressure the upstream producer until consumers arrive. This behavior can be tweaked by using the combinators .buffer for example with a drop strategy, or just attaching a consumer that drops all messages. If there are no other consumers, this will ensure that the producer is kept drained (dropping all elements) and once a new consumer arrives and messages are routed to the new consumer it will adaptively slow down, ensuring no more messages are dropped.

It is possible to define how many initial consumers that are required before it starts emitting any messages to the attached consumers. While not enough consumers have been attached messages are buffered and when the buffer is full the upstream producer is backpressured. No messages are dropped.

The above example illustrate a stateless partition function. For more advanced stateful routing the statefulSink can be used. Here is an example of a stateful round-robin function:

Scala

    // A simple producer that publishes a new "message-" every second
    val producer = Source.tick(1.second, 1.second, "message")
      .zipWith(Source(1 to 100))((a, b) ⇒ s"$a-$b")

    // New instance of the partitioner function and its state is created
    // for each materialization of the PartitionHub.
    def roundRobin(): (PartitionHub.ConsumerInfo, String) ⇒ Long = {
      var i = -1L

      (info, elem) ⇒ {
        i += 1
        info.consumerIdByIdx((i % info.size).toInt)
      }
    }

    // Attach a PartitionHub Sink to the producer. This will materialize to a
    // corresponding Source.
    // (We need to use toMat and Keep.right since by default the materialized
    // value to the left is used)
    val runnableGraph: RunnableGraph[Source[String, NotUsed]] =
      producer.toMat(PartitionHub.statefulSink(
        () ⇒ roundRobin(),
        startAfterNrOfConsumers = 2, bufferSize = 256))(Keep.right)

    // By running/materializing the producer, we get back a Source, which
    // gives us access to the elements published by the producer.
    val fromProducer: Source[String, NotUsed] = runnableGraph.run()

    // Print out messages from the producer in two independent consumers
    fromProducer.runForeach(msg ⇒ println("consumer1: " + msg))
    fromProducer.runForeach(msg ⇒ println("consumer2: " + msg))

Java

Note that it is a factory of a function to to be able to hold stateful variables that are unique for each materialization.

The function takes two parameters; the first is information about active consumers, including an array of consumer identifiers and the second is the stream element. The function should return the selected consumer identifier for the given element. The function will never be called when there are no active consumers, i.e. there is always at least one element in the array of identifiers.

Another interesting type of routing is to prefer routing to the fastest consumers. The ConsumerInfo has an accessor queueSize that is approximate number of buffered elements for a consumer. Larger value than other consumers could be an indication of that the consumer is slow. Note that this is a moving target since the elements are consumed concurrently. Here is an example of a hub that routes to the consumer with least buffered elements:

Scala

    val producer = Source(0 until 100)

    // ConsumerInfo.queueSize is the approximate number of buffered elements for a consumer.
    // Note that this is a moving target since the elements are consumed concurrently.
    val runnableGraph: RunnableGraph[Source[Int, NotUsed]] =
      producer.toMat(PartitionHub.statefulSink(
        () ⇒ (info, elem) ⇒ info.consumerIds.minBy(id ⇒ info.queueSize(id)),
        startAfterNrOfConsumers = 2, bufferSize = 16))(Keep.right)

    val fromProducer: Source[Int, NotUsed] = runnableGraph.run()

    fromProducer.runForeach(msg ⇒ println("consumer1: " + msg))
    fromProducer.throttle(10, 100.millis, 10, ThrottleMode.Shaping)
      .runForeach(msg ⇒ println("consumer2: " + msg))

Java

