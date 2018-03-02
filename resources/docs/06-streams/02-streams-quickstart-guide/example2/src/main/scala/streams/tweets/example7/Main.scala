package streams.tweets.example7

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._

import scala.concurrent.Future

final case class Author(handle: String)

final case class Hashtag(name: String)

final case class Tweet(author: Author, timestamp: Long, body: String) {
  def hashtags: Set[Hashtag] = body.split(" ").collect {
    case t if t.startsWith("#") ⇒ Hashtag(t.replaceAll("[^#\\w]", ""))
  }.toSet
}

object Main extends App {

  implicit val system = ActorSystem("reactive-tweets")
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  val akkaTag = Hashtag("#akka")

  val tweets: Source[Tweet, NotUsed] = Source(
    Tweet(Author("rolandkuhn"), System.currentTimeMillis, "#akka rocks!") ::
      Tweet(Author("patriknw"), System.currentTimeMillis, "#akka !") ::
      Tweet(Author("bantonsson"), System.currentTimeMillis, "#akka !") ::
      Tweet(Author("drewhk"), System.currentTimeMillis, "#akka !") ::
      Tweet(Author("ktosopl"), System.currentTimeMillis, "#akka on the rocks!") ::
      Tweet(Author("mmartynas"), System.currentTimeMillis, "wow #akka !") ::
      Tweet(Author("akkateam"), System.currentTimeMillis, "#akka rocks!") ::
      Tweet(Author("bananaman"), System.currentTimeMillis, "#bananas rock!") ::
      Tweet(Author("appleman"), System.currentTimeMillis, "#apples rock!") ::
      Tweet(Author("drama"), System.currentTimeMillis, "we compared #apples to #oranges!") ::
      Nil)

  def tweetsIn5SecondsFromNow = {
    Thread.sleep(5000)
    tweets
  }

  println("Running...")

  val sumSink = Sink.fold[Int, Int](0)(_ + _)
  val counterRunnableGraph: RunnableGraph[Future[Int]] = tweetsIn5SecondsFromNow
      .filter(_.hashtags contains akkaTag)
      .map(t ⇒ 1)
      .toMat(sumSink)(Keep.right)

  // materialize the stream once in the morning
  val morningTweetsCount: Future[Int] = counterRunnableGraph.run()
  // and once in the evening, reusing the flow
  val eveningTweetsCount: Future[Int] = counterRunnableGraph.run()

  morningTweetsCount.foreach(println)
  eveningTweetsCount.foreach(println)

  morningTweetsCount.zip(eveningTweetsCount).onComplete(_ => system.terminate())

}
