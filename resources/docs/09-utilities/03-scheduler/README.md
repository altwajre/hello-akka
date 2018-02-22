# Scheduler - Overview

Sometimes the need for making things happen in the future arises, and where do you go look then? Look no further than ActorSystem! There you find the scheduler method that returns an instance of akka.actor.Scheduler, this instance is unique per ActorSystem and is used internally for scheduling things to happen at specific points in time.

You can schedule sending of messages to actors and execution of tasks (functions or Runnable). You will get a Cancellable back that you can call cancel on to cancel the execution of the scheduled operation.

When scheduling periodic or single messages in an actor to itself it is recommended to use the Actor Timers instead of using the Scheduler directly.

The scheduler in Akka is designed for high-throughput of thousands up to millions of triggers. The prime use-case being triggering Actor receive timeouts, Future timeouts, circuit breakers and other time dependent events which happen all-the-time and in many instances at the same time. The implementation is based on a Hashed Wheel Timer, which is a known datastructure and algorithm for handling such use cases, refer to the Hashed and Hierarchical Timing Wheels whitepaper by Varghese and Lauck if you’d like to understand its inner workings.

The Akka scheduler is not designed for long-term scheduling (see akka-quartz-scheduler instead for this use case) nor is it to be used for highly precise firing of the events. The maximum amount of time into the future you can schedule an event to trigger is around 8 months, which in practice is too much to be useful since this would assume the system never went down during that period. If you need long-term scheduling we highly recommend looking into alternative schedulers, as this is not the use-case the Akka scheduler is implemented for.
Warning

The default implementation of Scheduler used by Akka is based on job buckets which are emptied according to a fixed schedule. It does not execute tasks at the exact time, but on every tick, it will run everything that is (over)due. The accuracy of the default Scheduler can be modified by the akka.scheduler.tick-duration configuration property.

# Some examples

Scala

    import akka.actor.Actor
    import akka.actor.Props
    import scala.concurrent.duration._

Java

Schedule to send the “foo”-message to the testActor after 50ms:

Scala

    //Use the system's dispatcher as ExecutionContext
    import system.dispatcher

    //Schedules to send the "foo"-message to the testActor after 50ms
    system.scheduler.scheduleOnce(50 milliseconds, testActor, "foo")

Java

Schedule a function, that sends the current time to the testActor, to be executed after 50ms:

Scala

    //Schedules a function to be executed (send a message to the testActor) after 50ms
    system.scheduler.scheduleOnce(50 milliseconds) {
      testActor ! System.currentTimeMillis
    }

Java

Schedule to send the “Tick”-message to the tickActor after 0ms repeating every 50ms:

Scala

    val Tick = "tick"
    class TickActor extends Actor {
      def receive = {
        case Tick ⇒ //Do something
      }
    }
    val tickActor = system.actorOf(Props(classOf[TickActor], this))
    //Use system's dispatcher as ExecutionContext
    import system.dispatcher

    //This will schedule to send the Tick-message
    //to the tickActor after 0ms repeating every 50ms
    val cancellable =
      system.scheduler.schedule(
        0 milliseconds,
        50 milliseconds,
        tickActor,
        Tick)

    //This cancels further Ticks to be sent
    cancellable.cancel()

Java

Warning

If you schedule functions or Runnable instances you should be extra careful to not close over unstable references. In practice this means not using this inside the closure in the scope of an Actor instance, not accessing sender() directly and not calling the methods of the Actor instance directly. If you need to schedule an invocation schedule a message to self instead (containing the necessary parameters) and then call the method when the message is received.

# From akka.actor.ActorSystem

/**
 * Light-weight scheduler for running asynchronous tasks after some deadline
 * in the future. Not terribly precise but cheap.
 */
def scheduler: Scheduler

Warning

All scheduled task will be executed when the ActorSystem is terminated, i.e. the task may execute before its timeout.

# The Scheduler interface

The actual scheduler implementation is loaded reflectively upon ActorSystem start-up, which means that it is possible to provide a different one using the akka.scheduler.implementation configuration property. The referenced class must implement the following interface:

Scala

    /**
     * An Akka scheduler service. This one needs one special behavior: if
     * Closeable, it MUST execute all outstanding tasks upon .close() in order
     * to properly shutdown all dispatchers.
     *
     * Furthermore, this timer service MUST throw IllegalStateException if it
     * cannot schedule a task. Once scheduled, the task MUST be executed. If
     * executed upon close(), the task may execute before its timeout.
     *
     * Scheduler implementation are loaded reflectively at ActorSystem start-up
     * with the following constructor arguments:
     *  1) the system’s com.typesafe.config.Config (from system.settings.config)
     *  2) a akka.event.LoggingAdapter
     *  3) a java.util.concurrent.ThreadFactory
     *
     * Please note that this scheduler implementation is higly optimised for high-throughput
     * and high-frequency events. It is not to be confused with long-term schedulers such as
     * Quartz. The scheduler will throw an exception if attempts are made to schedule too far
     * into the future (which by default is around 8 months (`Int.MaxValue` seconds).
     */
    trait Scheduler {
      /**
       * Schedules a message to be sent repeatedly with an initial delay and
       * frequency. E.g. if you would like a message to be sent immediately and
       * thereafter every 500ms you would set delay=Duration.Zero and
       * interval=Duration(500, TimeUnit.MILLISECONDS)
       *
       * Java & Scala API
       */
      final def schedule(
        initialDelay: FiniteDuration,
        interval:     FiniteDuration,
        receiver:     ActorRef,
        message:      Any)(implicit
        executor: ExecutionContext,
                           sender: ActorRef = Actor.noSender): Cancellable =
        schedule(initialDelay, interval, new Runnable {
          def run = {
            receiver ! message
            if (receiver.isTerminated)
              throw new SchedulerException("timer active for terminated actor")
          }
        })

      /**
       * Schedules a function to be run repeatedly with an initial delay and a
       * frequency. E.g. if you would like the function to be run after 2 seconds
       * and thereafter every 100ms you would set delay = Duration(2, TimeUnit.SECONDS)
       * and interval = Duration(100, TimeUnit.MILLISECONDS). If the execution of
       * the function takes longer than the interval, the subsequent execution will
       * start immediately after the prior one completes (there will be no overlap
       * of the function executions). In such cases, the actual execution interval
       * will differ from the interval passed to this method.
       *
       * If the function throws an exception the repeated scheduling is aborted,
       * i.e. the function will not be invoked any more.
       *
       * Scala API
       */
      final def schedule(
        initialDelay: FiniteDuration,
        interval:     FiniteDuration)(f: ⇒ Unit)(
        implicit
        executor: ExecutionContext): Cancellable =
        schedule(initialDelay, interval, new Runnable { override def run = f })

      /**
       * Schedules a `Runnable` to be run repeatedly with an initial delay and
       * a frequency. E.g. if you would like the function to be run after 2
       * seconds and thereafter every 100ms you would set delay = Duration(2,
       * TimeUnit.SECONDS) and interval = Duration(100, TimeUnit.MILLISECONDS). If
       * the execution of the runnable takes longer than the interval, the
       * subsequent execution will start immediately after the prior one completes
       * (there will be no overlap of executions of the runnable). In such cases,
       * the actual execution interval will differ from the interval passed to this
       * method.
       *
       * If the `Runnable` throws an exception the repeated scheduling is aborted,
       * i.e. the function will not be invoked any more.
       *
       * @throws IllegalArgumentException if the given delays exceed the maximum
       * reach (calculated as: `delay / tickNanos > Int.MaxValue`).
       *
       * Java API
       */
      def schedule(
        initialDelay: FiniteDuration,
        interval:     FiniteDuration,
        runnable:     Runnable)(implicit executor: ExecutionContext): Cancellable

      /**
       * Schedules a message to be sent once with a delay, i.e. a time period that has
       * to pass before the message is sent.
       *
       * @throws IllegalArgumentException if the given delays exceed the maximum
       * reach (calculated as: `delay / tickNanos > Int.MaxValue`).
       *
       * Java & Scala API
       */
      final def scheduleOnce(
        delay:    FiniteDuration,
        receiver: ActorRef,
        message:  Any)(implicit
        executor: ExecutionContext,
                       sender: ActorRef = Actor.noSender): Cancellable =
        scheduleOnce(delay, new Runnable {
          override def run = receiver ! message
        })

      /**
       * Schedules a function to be run once with a delay, i.e. a time period that has
       * to pass before the function is run.
       *
       * @throws IllegalArgumentException if the given delays exceed the maximum
       * reach (calculated as: `delay / tickNanos > Int.MaxValue`).
       *
       * Scala API
       */
      final def scheduleOnce(delay: FiniteDuration)(f: ⇒ Unit)(
        implicit
        executor: ExecutionContext): Cancellable =
        scheduleOnce(delay, new Runnable { override def run = f })

      /**
       * Schedules a Runnable to be run once with a delay, i.e. a time period that
       * has to pass before the runnable is executed.
       *
       * @throws IllegalArgumentException if the given delays exceed the maximum
       * reach (calculated as: `delay / tickNanos > Int.MaxValue`).
       *
       * Java & Scala API
       */
      def scheduleOnce(
        delay:    FiniteDuration,
        runnable: Runnable)(implicit executor: ExecutionContext): Cancellable

      /**
       * The maximum supported task frequency of this scheduler, i.e. the inverse
       * of the minimum time interval between executions of a recurring task, in Hz.
       */
      def maxFrequency: Double

    }

Java


# The Cancellable interface

Scheduling a task will result in a Cancellable (or throw an IllegalStateException if attempted after the scheduler’s shutdown). This allows you to cancel something that has been scheduled for execution.
Warning

This does not abort the execution of the task, if it had already been started. Check the return value of cancel to detect whether the scheduled task was canceled or will (eventually) have run.

/**
 * Signifies something that can be cancelled
 * There is no strict guarantee that the implementation is thread-safe,
 * but it should be good practice to make it so.
 */
trait Cancellable {
  /**
   * Cancels this Cancellable and returns true if that was successful.
   * If this cancellable was (concurrently) cancelled already, then this method
   * will return false although isCancelled will return true.
   *
   * Java & Scala API
   */
  def cancel(): Boolean

  /**
   * Returns true if and only if this Cancellable has been successfully cancelled
   *
   * Java & Scala API
   */
  def isCancelled: Boolean
}

