# What is an Actor - Overview
- An actor is a container for [State](#state), [Behavior](#behavior), a [Mailbox](#mailbox), [Child Actors](#child-actors) and a [Supervisor Strategy](#supervisor-strategy). 
- All of this is encapsulated behind an [Actor Reference](#actor-reference). 
- Actors have an explicit lifecycle:
    - They are not automatically destroyed when no longer referenced.
    - After having created one, it is your responsibility to make sure that it will eventually be terminated as well.
    - This gives you control over how resources are released [when an Actor terminates](#when-an-actor-terminates).

# Actor Reference
- An actor object needs to be shielded from the outside in order to benefit from the actor model. 
- Therefore, actors are represented to the outside using actor references, which are objects that can be passed around freely and without restriction. 
- This split into inner and outer object enables transparency for all the desired operations: 
    - Restarting an actor without needing to update references elsewhere.
    - Placing the actual actor object on remote hosts.
    - Sending messages to actors in completely different applications. 
- It is not possible to look inside an actor and get hold of its state from the outside.

# State
- Actor objects will typically contain some variables which reflect possible states the actor may be in. 
- Some examples include:
    - A simple counter.
    - A set of listeners (Actor references).
    - Pending requests.
    - An explicit state machine (e.g. using the [FSM module](TODO)).
- These data are what make an actor valuable, and they must be protected from corruption by other actors. 
- The good news is that Akka actors conceptually each have their own light-weight thread.
    - It is completely shielded from the rest of the system. 
    - Instead of having to synchronize access using locks you can just write your actor code without worrying about concurrency at all.
- Behind the scenes Akka will run sets of actors on sets of real threads.
    - Typically many actors share one thread.
    - Subsequent invocations of one actor may end up being processed on different threads. 
    - Akka ensures that this implementation detail does not affect the single-threadedness of handling the actor’s state.
- Because the internal state is vital to an actor’s operations, having inconsistent state is fatal. 
    - When the actor fails and is restarted by its supervisor, the state will be created from scratch, like upon first creating the actor. 
    - This is to enable the ability of self-healing of the system.
    - An actor’s state can be automatically recovered to the state before a restart by persisting received messages and replaying them after restart (see [Persistence](TODO)).
  
# Behavior
- Every time a message is processed, it is matched against the current behavior of the actor. 
- Behavior means a function which defines the actions to be taken in reaction to the message at that point in time:
    - E.g. forward a request if the client is authorized, deny it otherwise. 
    - This behavior may change over time, e.g. because different clients obtain authorization over time.
    - Or because the actor may go into an “out-of-service” mode and later come back. 
- These changes are achieved by either:
    - Encoding them in state variables which are read from the behavior logic.
    - The function itself may be swapped out at runtime, see the `become` and `unbecome` operations. 
- The initial behavior defined during construction of the actor object is special.
    - A restart of the actor will reset its behavior to this initial one.

# Mailbox
- An actor’s purpose is the processing of messages, and these messages were sent to the actor from other actors (or from outside the actor system). 
- The piece which connects sender and receiver is the actor’s mailbox.
- Each actor has exactly one mailbox to which all senders enqueue their messages. 
- Enqueuing happens in the time-order of send operations:
    - Sending multiple messages to the same target from the same actor, will enqueue them in the same order.
    - Messages sent from different actors may not have a defined order at runtime due to the apparent randomness of distributing actors across threads. 
- There are different mailbox implementations to choose from:
    - The default being a **FIFO mailbox**: 
        - The order of the messages processed by the actor matches the order in which they were enqueued. 
        - This is usually a good default, but applications may need to prioritize some messages over others. 
    - A **priority mailbox**:
        - Will enqueue not always at the end but at a position as given by the message priority.
        - This might even be at the front. 
        - While using such a queue, the order of messages processed will naturally be defined by the queue’s algorithm and in general not be FIFO.
- Akka differs from some other actor model implementations:
    - The current behavior must always handle the next dequeued message.
    - There is no scanning the mailbox for the next matching one. 
    - Failure to handle a message will typically be treated as a failure, unless this behavior is overridden.
  
# Child Actors
- Each actor is potentially a supervisor: 
    - If it creates children for delegating sub-tasks, it will automatically supervise them. 
- The list of children is maintained within the actor’s context and the actor has access to it. 
- Modifications to the list are done by creating (`context.actorOf(...)`) or stopping (`context.stop(child)`) children.
- These actions are reflected immediately. 
- The actual creation and termination actions happen behind the scenes in an asynchronous way, so they do not “block” their supervisor.

# Supervisor Strategy
- The final piece of an actor is its strategy for handling faults of its children. 
- Fault handling is then done transparently by Akka:
    - Applying one of the strategies described in [Supervision](TODO) and [Monitoring](TODO) for each incoming failure. 
    - As this strategy is fundamental to how an actor system is structured, it cannot be changed once an actor has been created.
- Considering that there is only one such strategy for each actor:
    - The children should be grouped beneath intermediate supervisors with matching strategies.
    - Structure the actor systems according to the splitting of tasks into sub-tasks.

# When an Actor Terminates
- It will free up its resources.
- Drain all remaining messages from its mailbox into the system’s **dead letter mailbox** which will forward them to the `EventStream` as `DeadLetters`. 
- The mailbox is then replaced within the actor reference with a system mailbox, redirecting all new messages to the `EventStream` as `DeadLetters`. 
- This is done on a best effort basis, do not rely on it in order to construct “guaranteed delivery”.
- The reason for not just silently dumping the messages was inspired by our tests: 
    - We register the `TestEventListener` on the event bus to which the dead letters are forwarded.
    - That will log a warning for every dead letter received.
    - This has been very helpful for deciphering test failures more quickly. 
    - It is conceivable that this feature may also be of use for other purposes.
