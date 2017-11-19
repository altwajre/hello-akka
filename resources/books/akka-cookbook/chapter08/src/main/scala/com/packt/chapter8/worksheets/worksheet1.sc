import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.concurrent.Future

implicit val system = ActorSystem("worksheet")
implicit val materializer = ActorMaterializer()

val tasks = (1 to 5).map(i => s"task$i")

val source: Source[String, NotUsed] = Source(tasks)
val flow: Flow[String, String, NotUsed] = Flow[String].map(x => x.toUpperCase)
val sink: Sink[String, Future[Done]] = Sink.foreach(x => println(s"--> $x"))

val runGraph = source.via(flow).to(sink)

runGraph.run()

Thread.sleep(1000)