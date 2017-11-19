Handling failure in event sourcing

https://www.packtpub.com/mapt/book/all_books/9781785288180/6/ch06lvl1sec68/handling-failure-in-event-sourcing

Backoff supervisor example:

val childProps = Props[SomePersistentActor]
val props = BackoffSupervisor.props(
    Backoff.onStop(
        childProps,
        childName = "someActor",
        minBackoff = 5 seconds,
        maxBackoff = 30 seconds,
        randomFactor = 0.3
    )
)
context.actorOf(props, name = "someSupervisor")