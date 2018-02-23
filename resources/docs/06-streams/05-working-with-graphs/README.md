# Working with Graphs - Overview

In Akka Streams computation graphs are not expressed using a fluent DSL like linear computations are, instead they are written in a more graph-resembling DSL which aims to make translating graph drawings (e.g. from notes taken from design discussions, or illustrations in protocol specifications) to and from code simpler. In this section we’ll dive into the multiple ways of constructing and re-using graphs, as well as explain common pitfalls and how to avoid them.

Graphs are needed whenever you want to perform any kind of fan-in (“multiple inputs”) or fan-out (“multiple outputs”) operations. Considering linear Flows to be like roads, we can picture graph operations as junctions: multiple flows being connected at a single point. Some graph operations which are common enough and fit the linear style of Flows, such as concat (which concatenates two streams, such that the second one is consumed after the first one has completed), may have shorthand methods defined on Flow or Source themselves, however you should keep in mind that those are also implemented as graph junctions.

# Constructing Graphs

Graphs are built from simple Flows which serve as the linear connections within the graphs as well as junctions which serve as fan-in and fan-out points for Flows. Thanks to the junctions having meaningful types based on their behaviour and making them explicit elements these elements should be rather straightforward to use.

Akka Streams currently provide these junctions (for a detailed list see stages overview):

### Fan-out
- Broadcast[T] – (1 input, N outputs) given an input element emits to each output
- Balance[T] – (1 input, N outputs) given an input element emits to one of its output ports
- UnzipWith[In,A,B,...] – (1 input, N outputs) takes a function of 1 input that given a value for each input emits N output elements (where N <= 20)
- UnZip[A,B] – (1 input, 2 outputs) splits a stream of (A,B) tuples into two streams, one of type A and one of type B

### Fan-in
- Merge[In] – (N inputs , 1 output) picks randomly from inputs pushing them one by one to its output
- MergePreferred[In] – like Merge but if elements are available on preferred port, it picks from it, otherwise randomly from others
- MergePrioritized[In] – like Merge but if elements are available on all input ports, it picks from them randomly based on their priority
- ZipWith[A,B,...,Out] – (N inputs, 1 output) which takes a function of N inputs that given a value for each input emits 1 output element
- Zip[A,B] – (2 inputs, 1 output) is a ZipWith specialised to zipping input streams of A and B into a (A,B) tuple stream
- Concat[A] – (2 inputs, 1 output) concatenates two streams (first consume one, then the second one)

One of the goals of the GraphDSL DSL is to look similar to how one would draw a graph on a whiteboard, so that it is simple to translate a design from whiteboard to code and be able to relate those two. Let’s illustrate this by translating the below hand drawn graph into Akka Streams:

simple-graph-example.png

Such graph is simple to translate to the Graph DSL since each linear element corresponds to a Flow, and each circle corresponds to either a Junction or a Source or Sink if it is beginning or ending a Flow. Junctions must always be created with defined type parameters, as otherwise the Nothing type will be inferred.

```scala

    val g = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._
      val in = Source(1 to 10)
      val out = Sink.ignore

      val bcast = builder.add(Broadcast[Int](2))
      val merge = builder.add(Merge[Int](2))

      val f1, f2, f3, f4 = Flow[Int].map(_ + 10)

      in ~> f1 ~> bcast ~> f2 ~> merge ~> f3 ~> out
      bcast ~> f4 ~> merge
      ClosedShape
    })

```


#### Note

Junction reference equality defines graph node equality (i.e. the same merge instance used in a GraphDSL refers to the same location in the resulting graph).

Notice the import GraphDSL.Implicits._ which brings into scope the ~> operator (read as “edge”, “via” or “to”) and its inverted counterpart <~ (for noting down flows in the opposite direction where appropriate).

By looking at the snippets above, it should be apparent that the GraphDSL.Builder object is mutable. It is used (implicitly) by the ~> operator, also making it a mutable operation as well. The reason for this design choice is to enable simpler creation of complex graphs, which may even contain cycles. Once the GraphDSL has been constructed though, the GraphDSL instance is immutable, thread-safe, and freely shareable. The same is true of all graph pieces—sources, sinks, and flows—once they are constructed. This means that you can safely re-use one given Flow or junction in multiple places in a processing graph.

We have seen examples of such re-use already above: the merge and broadcast junctions were imported into the graph using builder.add(...), an operation that will make a copy of the blueprint that is passed to it and return the inlets and outlets of the resulting copy so that they can be wired up. Another alternative is to pass existing graphs—of any shape—into the factory method that produces a new graph. The difference between these approaches is that importing using builder.add(...) ignores the materialized value of the imported graph while importing via the factory method allows its inclusion; for more details see Stream Materialization.

In the example below we prepare a graph that consists of two parallel streams, in which we re-use the same instance of Flow, yet it will properly be materialized as two connections between the corresponding Sources and Sinks:

```scala


    val topHeadSink = Sink.head[Int]
    val bottomHeadSink = Sink.head[Int]
    val sharedDoubler = Flow[Int].map(_ * 2)

    RunnableGraph.fromGraph(GraphDSL.create(topHeadSink, bottomHeadSink)((_, _)) { implicit builder =>
      (topHS, bottomHS) =>
      import GraphDSL.Implicits._
      val broadcast = builder.add(Broadcast[Int](2))
      Source.single(1) ~> broadcast.in

      broadcast.out(0) ~> sharedDoubler ~> topHS.in
      broadcast.out(1) ~> sharedDoubler ~> bottomHS.in
      ClosedShape
    })

```


# Constructing and combining Partial Graphs

Sometimes it is not possible (or needed) to construct the entire computation graph in one place, but instead construct all of its different phases in different places and in the end connect them all into a complete graph and run it.

This can be achieved by returning a different Shape than ClosedShape, for example FlowShape(in, out), from the function given to GraphDSL.create. See Predefined shapes for a list of such predefined shapes. Making a Graph a RunnableGraph requires all ports to be connected, and if they are not it will throw an exception at construction time, which helps to avoid simple wiring errors while working with graphs. A partial graph however allows you to return the set of yet to be connected ports from the code block that performs the internal wiring.

Let’s imagine we want to provide users with a specialized element that given 3 inputs will pick the greatest int value of each zipped triple. We’ll want to expose 3 input ports (unconnected sources) and one output port (unconnected sink).

```scala

    val pickMaxOfThree = GraphDSL.create() { implicit b ⇒
      import GraphDSL.Implicits._

      val zip1 = b.add(ZipWith[Int, Int, Int](math.max _))
      val zip2 = b.add(ZipWith[Int, Int, Int](math.max _))
      zip1.out ~> zip2.in0

      UniformFanInShape(zip2.out, zip1.in0, zip1.in1, zip2.in1)
    }

    val resultSink = Sink.head[Int]

    val g = RunnableGraph.fromGraph(GraphDSL.create(resultSink) { implicit b ⇒ sink ⇒
      import GraphDSL.Implicits._

      // importing the partial graph will return its shape (inlets & outlets)
      val pm3 = b.add(pickMaxOfThree)

      Source.single(1) ~> pm3.in(0)
      Source.single(2) ~> pm3.in(1)
      Source.single(3) ~> pm3.in(2)
      pm3.out ~> sink.in
      ClosedShape
    })

    val max: Future[Int] = g.run()
    Await.result(max, 300.millis) should equal(3)

```

As you can see, first we construct the partial graph that contains all the zipping and comparing of stream elements. This partial graph will have three inputs and one output, wherefore we use the UniformFanInShape. Then we import it (all of its nodes and connections) explicitly into the closed graph built in the second step in which all the undefined elements are rewired to real sources and sinks. The graph can then be run and yields the expected result.

#### Warning

Please note that GraphDSL is not able to provide compile time type-safety about whether or not all elements have been properly connected—this validation is performed as a runtime check during the graph’s instantiation.

A partial graph also verifies that all ports are either connected or part of the returned Shape.

# Constructing Sources, Sinks and Flows from Partial Graphs

Instead of treating a partial graph as simply a collection of flows and junctions which may not yet all be connected it is sometimes useful to expose such a complex graph as a simpler structure, such as a Source, Sink or Flow.

In fact, these concepts can be easily expressed as special cases of a partially connected graph:

    Source is a partial graph with exactly one output, that is it returns a SourceShape.
    Sink is a partial graph with exactly one input, that is it returns a SinkShape.
    Flow is a partial graph with exactly one input and exactly one output, that is it returns a FlowShape.

Being able to hide complex graphs inside of simple elements such as Sink / Source / Flow enables you to easily create one complex element and from there on treat it as simple compound stage for linear computations.

In order to create a Source from a graph the method Source.fromGraph is used, to use it we must have a Graph[SourceShape, T]. This is constructed using GraphDSL.create and returning a SourceShape from the function passed in. The single outlet must be provided to the SourceShape.of method and will become “the sink that must be attached before this Source can run”.

Refer to the example below, in which we create a Source that zips together two numbers, to see this graph construction in action:

```scala

    val pairs = Source.fromGraph(GraphDSL.create() { implicit b ⇒
      import GraphDSL.Implicits._

      // prepare graph elements
      val zip = b.add(Zip[Int, Int]())
      def ints = Source.fromIterator(() ⇒ Iterator.from(1))

      // connect the graph
      ints.filter(_ % 2 != 0) ~> zip.in0
      ints.filter(_ % 2 == 0) ~> zip.in1

      // expose port
      SourceShape(zip.out)
    })

    val firstPair: Future[(Int, Int)] = pairs.runWith(Sink.head)

```

Similarly the same can be done for a Sink[T], using SinkShape.of in which case the provided value must be an Inlet[T]. For defining a Flow[T] we need to expose both an inlet and an outlet:

```scala

    val pairUpWithToString =
      Flow.fromGraph(GraphDSL.create() { implicit b ⇒
        import GraphDSL.Implicits._

        // prepare graph elements
        val broadcast = b.add(Broadcast[Int](2))
        val zip = b.add(Zip[Int, String]())

        // connect the graph
        broadcast.out(0).map(identity) ~> zip.in0
        broadcast.out(1).map(_.toString) ~> zip.in1

        // expose ports
        FlowShape(broadcast.in, zip.out)
      })

    pairUpWithToString.runWith(Source(List(1)), Sink.head)

```


# Combining Sources and Sinks with simplified API

There is a simplified API you can use to combine sources and sinks with junctions like: Broadcast[T], Balance[T], Merge[In] and Concat[A] without the need for using the Graph DSL. The combine method takes care of constructing the necessary graph underneath. In following example we combine two sources into one (fan-in):

```scala

    val sourceOne = Source(List(1))
    val sourceTwo = Source(List(2))
    val merged = Source.combine(sourceOne, sourceTwo)(Merge(_))

    val mergedResult: Future[Int] = merged.runWith(Sink.fold(0)(_ + _))

```

The same can be done for a Sink[T] but in this case it will be fan-out:

```scala

    val sendRmotely = Sink.actorRef(actorRef, "Done")
    val localProcessing = Sink.foreach[Int](_ ⇒ /* do something usefull */ ())

    val sink = Sink.combine(sendRmotely, localProcessing)(Broadcast[Int](_))

    Source(List(0, 1, 2)).runWith(sink)

```


# Building reusable Graph components

It is possible to build reusable, encapsulated components of arbitrary input and output ports using the graph DSL.

As an example, we will build a graph junction that represents a pool of workers, where a worker is expressed as a Flow[I,O,_], i.e. a simple transformation of jobs of type I to results of type O (as you have seen already, this flow can actually contain a complex graph inside). Our reusable worker pool junction will not preserve the order of the incoming jobs (they are assumed to have a proper ID field) and it will use a Balance junction to schedule jobs to available workers. On top of this, our junction will feature a “fastlane”, a dedicated port where jobs of higher priority can be sent.

Altogether, our junction will have two input ports of type I (for the normal and priority jobs) and an output port of type O. To represent this interface, we need to define a custom Shape. The following lines show how to do that.

```scala

    // A shape represents the input and output ports of a reusable
    // processing module
    case class PriorityWorkerPoolShape[In, Out](
      jobsIn:         Inlet[In],
      priorityJobsIn: Inlet[In],
      resultsOut:     Outlet[Out]) extends Shape {

      // It is important to provide the list of all input and output
      // ports with a stable order. Duplicates are not allowed.
      override val inlets: immutable.Seq[Inlet[_]] =
        jobsIn :: priorityJobsIn :: Nil
      override val outlets: immutable.Seq[Outlet[_]] =
        resultsOut :: Nil

      // A Shape must be able to create a copy of itself. Basically
      // it means a new instance with copies of the ports
      override def deepCopy() = PriorityWorkerPoolShape(
        jobsIn.carbonCopy(),
        priorityJobsIn.carbonCopy(),
        resultsOut.carbonCopy())

    }


# Predefined shapes

In general a custom Shape needs to be able to provide all its input and output ports, be able to copy itself, and also be able to create a new instance from given ports. There are some predefined shapes provided to avoid unnecessary boilerplate:

    SourceShape, SinkShape, FlowShape for simpler shapes,
    UniformFanInShape and UniformFanOutShape for junctions with multiple input (or output) ports of the same type,
    FanInShape1, FanInShape2, …, FanOutShape1, FanOutShape2, … for junctions with multiple input (or output) ports of different types.

Since our shape has two input ports and one output port, we can just use the FanInShape DSL to define our custom shape:

```scala

    import FanInShape.{ Init, Name }

    class PriorityWorkerPoolShape2[In, Out](_init: Init[Out] = Name("PriorityWorkerPool"))
      extends FanInShape[Out](_init) {
      protected override def construct(i: Init[Out]) = new PriorityWorkerPoolShape2(i)

      val jobsIn = newInlet[In]("jobsIn")
      val priorityJobsIn = newInlet[In]("priorityJobsIn")
      // Outlet[Out] with name "out" is automatically created
    }

Now that we have a Shape we can wire up a Graph that represents our worker pool. First, we will merge incoming normal and priority jobs using MergePreferred, then we will send the jobs to a Balance junction which will fan-out to a configurable number of workers (flows), finally we merge all these results together and send them out through our only output port. This is expressed by the following code:

```scala

    object PriorityWorkerPool {
      def apply[In, Out](
        worker:      Flow[In, Out, Any],
        workerCount: Int): Graph[PriorityWorkerPoolShape[In, Out], NotUsed] = {

        GraphDSL.create() { implicit b ⇒
          import GraphDSL.Implicits._

          val priorityMerge = b.add(MergePreferred[In](1))
          val balance = b.add(Balance[In](workerCount))
          val resultsMerge = b.add(Merge[Out](workerCount))

          // After merging priority and ordinary jobs, we feed them to the balancer
          priorityMerge ~> balance

          // Wire up each of the outputs of the balancer to a worker flow
          // then merge them back
          for (i ← 0 until workerCount)
            balance.out(i) ~> worker ~> resultsMerge.in(i)

          // We now expose the input ports of the priorityMerge and the output
          // of the resultsMerge as our PriorityWorkerPool ports
          // -- all neatly wrapped in our domain specific Shape
          PriorityWorkerPoolShape(
            jobsIn = priorityMerge.in(0),
            priorityJobsIn = priorityMerge.preferred,
            resultsOut = resultsMerge.out)
        }

      }

    }

All we need to do now is to use our custom junction in a graph. The following code simulates some simple workers and jobs using plain strings and prints out the results. Actually we used two instances of our worker pool junction using add() twice.

```scala

    val worker1 = Flow[String].map("step 1 " + _)
    val worker2 = Flow[String].map("step 2 " + _)

    RunnableGraph.fromGraph(GraphDSL.create() { implicit b ⇒
      import GraphDSL.Implicits._

      val priorityPool1 = b.add(PriorityWorkerPool(worker1, 4))
      val priorityPool2 = b.add(PriorityWorkerPool(worker2, 2))

      Source(1 to 100).map("job: " + _) ~> priorityPool1.jobsIn
      Source(1 to 100).map("priority job: " + _) ~> priorityPool1.priorityJobsIn

      priorityPool1.resultsOut ~> priorityPool2.jobsIn
      Source(1 to 100).map("one-step, priority " + _) ~> priorityPool2.priorityJobsIn

      priorityPool2.resultsOut ~> Sink.foreach(println)
      ClosedShape
    }).run()


# Bidirectional Flows

A graph topology that is often useful is that of two flows going in opposite directions. Take for example a codec stage that serializes outgoing messages and deserializes incoming octet streams. Another such stage could add a framing protocol that attaches a length header to outgoing data and parses incoming frames back into the original octet stream chunks. These two stages are meant to be composed, applying one atop the other as part of a protocol stack. For this purpose exists the special type BidiFlow which is a graph that has exactly two open inlets and two open outlets. The corresponding shape is called BidiShape and is defined like this:

/**
 * A bidirectional flow of elements that consequently has two inputs and two
 * outputs, arranged like this:
 *
 * {{{
 *        +------+
 *  In1 ~>|      |~> Out1
 *        | bidi |
 * Out2 <~|      |<~ In2
 *        +------+
 * }}}
 */
final case class BidiShape[-In1, +Out1, -In2, +Out2](
  in1:  Inlet[In1 @uncheckedVariance],
  out1: Outlet[Out1 @uncheckedVariance],
  in2:  Inlet[In2 @uncheckedVariance],
  out2: Outlet[Out2 @uncheckedVariance]) extends Shape {
  override val inlets: immutable.Seq[Inlet[_]] = in1 :: in2 :: Nil
  override val outlets: immutable.Seq[Outlet[_]] = out1 :: out2 :: Nil

  /**
   * Java API for creating from a pair of unidirectional flows.
   */
  def this(top: FlowShape[In1, Out1], bottom: FlowShape[In2, Out2]) = this(top.in, top.out, bottom.in, bottom.out)

  override def deepCopy(): BidiShape[In1, Out1, In2, Out2] =
    BidiShape(in1.carbonCopy(), out1.carbonCopy(), in2.carbonCopy(), out2.carbonCopy())

}

A bidirectional flow is defined just like a unidirectional Flow as demonstrated for the codec mentioned above:

```scala

    trait Message
    case class Ping(id: Int) extends Message
    case class Pong(id: Int) extends Message

    def toBytes(msg: Message): ByteString = {
      implicit val order = ByteOrder.LITTLE_ENDIAN
      msg match {
        case Ping(id) ⇒ ByteString.newBuilder.putByte(1).putInt(id).result()
        case Pong(id) ⇒ ByteString.newBuilder.putByte(2).putInt(id).result()
      }
    }

    def fromBytes(bytes: ByteString): Message = {
      implicit val order = ByteOrder.LITTLE_ENDIAN
      val it = bytes.iterator
      it.getByte match {
        case 1     ⇒ Ping(it.getInt)
        case 2     ⇒ Pong(it.getInt)
        case other ⇒ throw new RuntimeException(s"parse error: expected 1|2 got $other")
      }
    }

    val codecVerbose = BidiFlow.fromGraph(GraphDSL.create() { b ⇒
      // construct and add the top flow, going outbound
      val outbound = b.add(Flow[Message].map(toBytes))
      // construct and add the bottom flow, going inbound
      val inbound = b.add(Flow[ByteString].map(fromBytes))
      // fuse them together into a BidiShape
      BidiShape.fromFlows(outbound, inbound)
    })

    // this is the same as the above
    val codec = BidiFlow.fromFunctions(toBytes _, fromBytes _)

```

The first version resembles the partial graph constructor, while for the simple case of a functional 1:1 transformation there is a concise convenience method as shown on the last line. The implementation of the two functions is not difficult either:

```scala

    def toBytes(msg: Message): ByteString = {
      implicit val order = ByteOrder.LITTLE_ENDIAN
      msg match {
        case Ping(id) ⇒ ByteString.newBuilder.putByte(1).putInt(id).result()
        case Pong(id) ⇒ ByteString.newBuilder.putByte(2).putInt(id).result()
      }
    }

    def fromBytes(bytes: ByteString): Message = {
      implicit val order = ByteOrder.LITTLE_ENDIAN
      val it = bytes.iterator
      it.getByte match {
        case 1     ⇒ Ping(it.getInt)
        case 2     ⇒ Pong(it.getInt)
        case other ⇒ throw new RuntimeException(s"parse error: expected 1|2 got $other")
      }
    }

```

In this way you could easily integrate any other serialization library that turns an object into a sequence of bytes.

The other stage that we talked about is a little more involved since reversing a framing protocol means that any received chunk of bytes may correspond to zero or more messages. This is best implemented using a GraphStage (see also Custom processing with GraphStage).

```scala

    val framing = BidiFlow.fromGraph(GraphDSL.create() { b ⇒
      implicit val order = ByteOrder.LITTLE_ENDIAN

      def addLengthHeader(bytes: ByteString) = {
        val len = bytes.length
        ByteString.newBuilder.putInt(len).append(bytes).result()
      }

      class FrameParser extends GraphStage[FlowShape[ByteString, ByteString]] {

        val in = Inlet[ByteString]("FrameParser.in")
        val out = Outlet[ByteString]("FrameParser.out")
        override val shape = FlowShape.of(in, out)

        override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

          // this holds the received but not yet parsed bytes
          var stash = ByteString.empty
          // this holds the current message length or -1 if at a boundary
          var needed = -1

          setHandler(out, new OutHandler {
            override def onPull(): Unit = {
              if (isClosed(in)) run()
              else pull(in)
            }
          })
          setHandler(in, new InHandler {
            override def onPush(): Unit = {
              val bytes = grab(in)
              stash = stash ++ bytes
              run()
            }

            override def onUpstreamFinish(): Unit = {
              // either we are done
              if (stash.isEmpty) completeStage()
              // or we still have bytes to emit
              // wait with completion and let run() complete when the
              // rest of the stash has been sent downstream
              else if (isAvailable(out)) run()
            }
          })

          private def run(): Unit = {
            if (needed == -1) {
              // are we at a boundary? then figure out next length
              if (stash.length < 4) {
                if (isClosed(in)) completeStage()
                else pull(in)
              } else {
                needed = stash.iterator.getInt
                stash = stash.drop(4)
                run() // cycle back to possibly already emit the next chunk
              }
            } else if (stash.length < needed) {
              // we are in the middle of a message, need more bytes,
              // or have to stop if input closed
              if (isClosed(in)) completeStage()
              else pull(in)
            } else {
              // we have enough to emit at least one message, so do it
              val emit = stash.take(needed)
              stash = stash.drop(needed)
              needed = -1
              push(out, emit)
            }
          }
        }
      }

      val outbound = b.add(Flow[ByteString].map(addLengthHeader))
      val inbound = b.add(Flow[ByteString].via(new FrameParser))
      BidiShape.fromFlows(outbound, inbound)
    })

```

With these implementations we can build a protocol stack and test it:

```scala

    /* construct protocol stack
     *         +------------------------------------+
     *         | stack                              |
     *         |                                    |
     *         |  +-------+            +---------+  |
     *    ~>   O~~o       |     ~>     |         o~~O    ~>
     * Message |  | codec | ByteString | framing |  | ByteString
     *    <~   O~~o       |     <~     |         o~~O    <~
     *         |  +-------+            +---------+  |
     *         +------------------------------------+
     */
    val stack = codec.atop(framing)

    // test it by plugging it into its own inverse and closing the right end
    val pingpong = Flow[Message].collect { case Ping(id) ⇒ Pong(id) }
    val flow = stack.atop(stack.reversed).join(pingpong)
    val result = Source((0 to 9).map(Ping)).via(flow).limit(20).runWith(Sink.seq)
    Await.result(result, 1.second) should ===((0 to 9).map(Pong))

```

This example demonstrates how BidiFlow subgraphs can be hooked together and also turned around with the .reversed method. The test simulates both parties of a network communication protocol without actually having to open a network connection—the flows can just be connected directly.

# Accessing the materialized value inside the Graph

In certain cases it might be necessary to feed back the materialized value of a Graph (partial, closed or backing a Source, Sink, Flow or BidiFlow). This is possible by using builder.materializedValue which gives an Outlet that can be used in the graph as an ordinary source or outlet, and which will eventually emit the materialized value. If the materialized value is needed at more than one place, it is possible to call materializedValue any number of times to acquire the necessary number of outlets.

```scala

    import GraphDSL.Implicits._
    val foldFlow: Flow[Int, Int, Future[Int]] = Flow.fromGraph(GraphDSL.create(Sink.fold[Int, Int](0)(_ + _)) { implicit builder ⇒ fold ⇒
      FlowShape(fold.in, builder.materializedValue.mapAsync(4)(identity).outlet)
    })

```

Be careful not to introduce a cycle where the materialized value actually contributes to the materialized value. The following example demonstrates a case where the materialized Future of a fold is fed back to the fold itself.

```scala

    import GraphDSL.Implicits._
    // This cannot produce any value:
    val cyclicFold: Source[Int, Future[Int]] = Source.fromGraph(GraphDSL.create(Sink.fold[Int, Int](0)(_ + _)) { implicit builder ⇒ fold ⇒
      // - Fold cannot complete until its upstream mapAsync completes
      // - mapAsync cannot complete until the materialized Future produced by
      //   fold completes
      // As a result this Source will never emit anything, and its materialited
      // Future will never complete
      builder.materializedValue.mapAsync(4)(identity) ~> fold
      SourceShape(builder.materializedValue.mapAsync(4)(identity).outlet)
    })

```


# Graph cycles, liveness and deadlocks

Cycles in bounded stream topologies need special considerations to avoid potential deadlocks and other liveness issues. This section shows several examples of problems that can arise from the presence of feedback arcs in stream processing graphs.

In the following examples runnable graphs are created but do not run because each have some issue and will deadlock after start. Source variable is not defined as the nature and number of element does not matter for described problems.

The first example demonstrates a graph that contains a naïve cycle. The graph takes elements from the source, prints them, then broadcasts those elements to a consumer (we just used Sink.ignore for now) and to a feedback arc that is merged back into the main stream via a Merge junction.

#### Note

The graph DSL allows the connection arrows to be reversed, which is particularly handy when writing cycles—as we will see there are cases where this is very helpful.

```scala

    // WARNING! The graph below deadlocks!
    RunnableGraph.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val merge = b.add(Merge[Int](2))
      val bcast = b.add(Broadcast[Int](2))

      source ~> merge ~> Flow[Int].map { s => println(s); s } ~> bcast ~> Sink.ignore
                merge                    <~                      bcast
      ClosedShape
    })

```

Running this we observe that after a few numbers have been printed, no more elements are logged to the console - all processing stops after some time. After some investigation we observe that:

    through merging from source we increase the number of elements flowing in the cycle
    by broadcasting back to the cycle we do not decrease the number of elements in the cycle

Since Akka Streams (and Reactive Streams in general) guarantee bounded processing (see the “Buffering” section for more details) it means that only a bounded number of elements are buffered over any time span. Since our cycle gains more and more elements, eventually all of its internal buffers become full, backpressuring source forever. To be able to process more elements from source elements would need to leave the cycle somehow.

If we modify our feedback loop by replacing the Merge junction with a MergePreferred we can avoid the deadlock. MergePreferred is unfair as it always tries to consume from a preferred input port if there are elements available before trying the other lower priority input ports. Since we feed back through the preferred port it is always guaranteed that the elements in the cycles can flow.

```scala

    // WARNING! The graph below stops consuming from "source" after a few steps
    RunnableGraph.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val merge = b.add(MergePreferred[Int](1))
      val bcast = b.add(Broadcast[Int](2))

      source ~> merge ~> Flow[Int].map { s => println(s); s } ~> bcast ~> Sink.ignore
                merge.preferred              <~                  bcast
      ClosedShape
    })

```

If we run the example we see that the same sequence of numbers are printed over and over again, but the processing does not stop. Hence, we avoided the deadlock, but source is still back-pressured forever, because buffer space is never recovered: the only action we see is the circulation of a couple of initial elements from source.

#### Note

What we see here is that in certain cases we need to choose between boundedness and liveness. Our first example would not deadlock if there would be an infinite buffer in the loop, or vice versa, if the elements in the cycle would be balanced (as many elements are removed as many are injected) then there would be no deadlock.

To make our cycle both live (not deadlocking) and fair we can introduce a dropping element on the feedback arc. In this case we chose the buffer() operation giving it a dropping strategy OverflowStrategy.dropHead.

```scala

    RunnableGraph.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val merge = b.add(Merge[Int](2))
      val bcast = b.add(Broadcast[Int](2))

      source ~> merge ~> Flow[Int].map { s => println(s); s } ~> bcast ~> Sink.ignore
          merge <~ Flow[Int].buffer(10, OverflowStrategy.dropHead) <~ bcast
      ClosedShape
    })

```

If we run this example we see that

    The flow of elements does not stop, there are always elements printed
    We see that some of the numbers are printed several times over time (due to the feedback loop) but on average the numbers are increasing in the long term

This example highlights that one solution to avoid deadlocks in the presence of potentially unbalanced cycles (cycles where the number of circulating elements are unbounded) is to drop elements. An alternative would be to define a larger buffer with OverflowStrategy.fail which would fail the stream instead of deadlocking it after all buffer space has been consumed.

As we discovered in the previous examples, the core problem was the unbalanced nature of the feedback loop. We circumvented this issue by adding a dropping element, but now we want to build a cycle that is balanced from the beginning instead. To achieve this we modify our first graph by replacing the Merge junction with a ZipWith. Since ZipWith takes one element from source and from the feedback arc to inject one element into the cycle, we maintain the balance of elements.

```scala

    // WARNING! The graph below never processes any elements
    RunnableGraph.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val zip = b.add(ZipWith[Int, Int, Int]((left, right) => right))
      val bcast = b.add(Broadcast[Int](2))

      source ~> zip.in0
      zip.out.map { s => println(s); s } ~> bcast ~> Sink.ignore
      zip.in1             <~                bcast
      ClosedShape
    })

```

Still, when we try to run the example it turns out that no element is printed at all! After some investigation we realize that:

    In order to get the first element from source into the cycle we need an already existing element in the cycle
    In order to get an initial element in the cycle we need an element from source

These two conditions are a typical “chicken-and-egg” problem. The solution is to inject an initial element into the cycle that is independent from source. We do this by using a Concat junction on the backwards arc that injects a single element using Source.single.

```scala

    RunnableGraph.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val zip = b.add(ZipWith((left: Int, right: Int) => left))
      val bcast = b.add(Broadcast[Int](2))
      val concat = b.add(Concat[Int]())
      val start = Source.single(0)

      source ~> zip.in0
      zip.out.map { s => println(s); s } ~> bcast ~> Sink.ignore
      zip.in1 <~ concat <~ start
                 concat         <~          bcast
      ClosedShape
    })

```

When we run the above example we see that processing starts and never stops. The important takeaway from this example is that balanced cycles often need an initial “kick-off” element to be injected into the cycle.
