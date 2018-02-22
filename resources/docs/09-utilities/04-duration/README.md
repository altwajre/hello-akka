# Duration - Overview

Durations are used throughout the Akka library, wherefore this concept is represented by a special data type, scala.concurrent.duration.Duration. Values of this type may represent infinite (Duration.Inf, Duration.MinusInf) or finite durations, or be Duration.Undefined.

# Finite vs. Infinite

Since trying to convert an infinite duration into a concrete time unit like seconds will throw an exception, there are different types available for distinguishing the two kinds at compile time:

    FiniteDuration is guaranteed to be finite, calling toNanos and friends is safe
    Duration can be finite or infinite, so this type should only be used when finite-ness does not matter; this is a supertype of FiniteDuration


# Scala

In Scala durations are constructable using a mini-DSL and support all expected arithmetic operations:

import scala.concurrent.duration._

val fivesec = 5.seconds
val threemillis = 3.millis
val diff = fivesec - threemillis
assert(diff < fivesec)
val fourmillis = threemillis * 4 / 3 // you cannot write it the other way around
val n = threemillis / (1 millisecond)

Note

You may leave out the dot if the expression is clearly delimited (e.g. within parentheses or in an argument list), but it is recommended to use it if the time unit is the last token on a line, otherwise semi-colon inference might go wrong, depending on what starts the next line.

# Java

Java provides less syntactic sugar, so you have to spell out the operations as method calls instead:

import scala.concurrent.duration.Duration;
import scala.concurrent.duration.Deadline;

final Duration fivesec = Duration.create(5, "seconds");
final Duration threemillis = Duration.create("3 millis");
final Duration diff = fivesec.minus(threemillis);
assert diff.lt(fivesec);
assert Duration.Zero().lt(Duration.Inf());


# Deadline

Durations have a brother named Deadline, which is a class holding a representation of an absolute point in time, and support deriving a duration from this by calculating the difference between now and the deadline. This is useful when you want to keep one overall deadline without having to take care of the book-keeping wrt. the passing of time yourself:

val deadline = 10.seconds.fromNow
// do something
val rest = deadline.timeLeft

In Java you create these from durations:

final Deadline deadline = Duration.create(10, "seconds").fromNow();
final Duration rest = deadline.timeLeft();

