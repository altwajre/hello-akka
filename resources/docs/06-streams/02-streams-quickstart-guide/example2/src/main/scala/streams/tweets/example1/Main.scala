package streams.tweets.example1

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.{Done, NotUsed}
import streams.tweets.example2

import scala.concurrent.Future

final case class Author(handle: String)

final case class Hashtag(name: String)

final case class Tweet(author: example2.Author, timestamp: Long, body: String) {
  def hashtags: Set[example2.Hashtag] = body.split(" ").collect {
    case t if t.startsWith("#") ⇒ example2.Hashtag(t.replaceAll("[^#\\w]", ""))
  }.toSet
}

object Main extends App {

  implicit val system = ActorSystem("reactive-tweets")
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  val akkaTag = example2.Hashtag("#akka")

  val tweets: Source[example2.Tweet, NotUsed] = Source(
    example2.Tweet(example2.Author("rolandkuhn"), System.currentTimeMillis, "#akka rocks!") ::
      example2.Tweet(example2.Author("patriknw"), System.currentTimeMillis, "#akka !") ::
      example2.Tweet(example2.Author("bantonsson"), System.currentTimeMillis, "#akka !") ::
      example2.Tweet(example2.Author("drewhk"), System.currentTimeMillis, "#akka !") ::
      example2.Tweet(example2.Author("ktosopl"), System.currentTimeMillis, "#akka on the rocks!") ::
      example2.Tweet(example2.Author("mmartynas"), System.currentTimeMillis, "wow #akka !") ::
      example2.Tweet(example2.Author("akkateam"), System.currentTimeMillis, "#akka rocks!") ::
      example2.Tweet(example2.Author("bananaman"), System.currentTimeMillis, "#bananas rock!") ::
      example2.Tweet(example2.Author("appleman"), System.currentTimeMillis, "#apples rock!") ::
      example2.Tweet(example2.Author("drama"), System.currentTimeMillis, "we compared #apples to #oranges!") ::
      Nil)

  val result: Future[Done] = tweets
    .map(_.hashtags) // Get all sets of hashtags ...
    .reduce(_ ++ _) // ... and reduce them to a single set, removing duplicates across all tweets
    .mapConcat(identity) // Flatten the stream of tweets to a stream of hashtags
    .map(_.name.toUpperCase) // Convert all hashtags to upper case
    .runWith(Sink.foreach(println)) // Attach the Flow to a Sink that will finally print the hashtags

  result.onComplete(_ => system.terminate())

}
