# Supervision and Monitoring
- This chapter outlines the concept behind supervision, the primitives offered and their semantics.

# What Supervision Means
- As described in [Actor Systems](../02-actor-system#hierarchical-structure) supervision describes a dependency relationship between actors: 
    - The supervisor delegates tasks to subordinates and therefore must respond to their failures. 
    - When a subordinate detects a failure (i.e. throws an exception):
        - It suspends itself and all its subordinates.
        - Sends a message to its supervisor, signaling failure. 
    - Depending on the nature of the work to be supervised and the nature of the failure, the supervisor has a choice of the following four options:
        - **Resume** the subordinate, keeping its accumulated internal state.
        - **Restart** the subordinate, clearing out its accumulated internal state.
        - **Stop** the subordinate permanently.
        - **Escalate** the failure, thereby failing itself.
- It is important to always view an actor as part of a supervision hierarchy.
    - This explains the existence of the fourth choice (as a supervisor also is subordinate to another supervisor higher up).
    - It has implications on the first three: 
        - Resuming an actor resumes all its subordinates
        - Restarting an actor entails restarting all its subordinates.
        - Terminating an actor will also terminate all its subordinates. 
- The default behavior of the `preRestart` hook of the `Actor` class is to terminate all its children before restarting.
    - This hook can be overridden.
    - The recursive restart applies to all children left after this hook has been executed.
- Each supervisor is configured with a function translating all possible failure causes (i.e. exceptions) into one of the four choices given above.
    - This function does not take the failed actor’s identity as an input. 
    - It is quite easy to come up with examples of structures where this might not seem flexible enough.
        - E.g. wishing for different strategies to be applied to different subordinates. 
- At this point it is vital to understand that supervision is about forming a recursive fault handling structure. 
- If you try to do too much at one level, it will become hard to reason about.
    - The recommended way in this case is to add a level of supervision.
- Akka implements a specific form called “parental supervision”. 
    - Actors can only be created by other actors—where the top-level actor is provided by the library.
    - Each created actor is supervised by its parent. 
    - This restriction makes the formation of actor supervision hierarchies implicit and encourages sound design decisions. 
    - This guarantees that actors cannot be orphaned or attached to supervisors from the outside, which might otherwise catch them unawares. 
    - This yields a natural and clean shutdown procedure for (sub-trees of) actor applications.
- Parent-child communication happens by special system messages that have their own mailboxes separate from user messages. 
    - This implies that supervision related events are not deterministically ordered relative to ordinary messages. 
    - In general, the user cannot influence the order of normal messages and failure notifications. 
    - See [Message Ordering](TODO).

# The Top-Level Supervisors
![](https://doc.akka.io/docs/akka/current/general/guardians.png)

- An actor system will during its creation start at least three actors, shown in the image above. 
- For more information about the consequences for actor paths see [Top-Level Scopes for Actor Paths](TODO).

## /user: The Guardian Actor
- The actor which is probably most interacted with is the parent of all user-created actors, the guardian named `/user`. 
- Actors created using `system.actorOf()` are children of this actor. 
- This means that:
    - When this guardian terminates, all normal actors in the system will be shutdown, too. 
    - This guardian’s supervisor strategy determines how the top-level normal actors are supervised. 
- Since Akka 2.1 it is possible to configure this using:
    - The setting `akka.actor.guardian-supervisor-strategy`.
    - It takes the fully-qualified class-name of a `SupervisorStrategyConfigurator`. 
- When the guardian escalates a failure:
    - The root guardian’s response will be to terminate the guardian.
    - This in effect will shut down the whole actor system.

## /system: The System Guardian
- This special guardian has been introduced in order to:
    - Achieve an orderly shut-down sequence where logging remains active while all normal actors terminate.
    - Even though logging itself is implemented using actors. 
- This is realized by:
    - Having the system guardian watch the user guardian.
    - Initiate its own shut-down upon reception of the Terminated message. 
- The top-level system actors are supervised using a strategy which will:
    - **Terminate** the child on `ActorInitializationException` and `ActorKilledException`. 
    - **Restart** the child indefinitely upon all other types of `Exception`.
    - **Escalate** all other `Throwable`s, which will shut down the whole actor system.

## /: The Root Guardian
- The root guardian is the grand-parent of all so-called “top-level” actors.
- It supervises all the special actors mentioned in [Top-Level Scopes for Actor Paths](TODO) using the `SupervisorStrategy.stoppingStrategy`.
- This will **terminate** the child upon any type of `Exception`. 
- All other `Throwable`s will be **escalated** ... but to whom?
    - Since every real actor has a supervisor, the supervisor of the root guardian cannot be a real actor. 
    - This means that it is “outside of the bubble”, and is called the “bubble-walker”. 
    - This is a synthetic `ActorRef` which in effect stops its child upon the first sign of trouble.
    - And sets the actor system’s `isTerminated` status to `true` as soon as the root guardian is fully terminated.

# What Restarting Means
- Causes for the failure fall into three categories:
    - Systematic (i.e. programming) error for the specific message received.
    - Transient failure of some external resource used during processing the message.
    - Corrupt internal state of the actor.
- If the failure is not recognizable we must assume that the state is corrupt and must be cleared out.
- If the supervisor decides that its other children or itself is not affected by the corruption it is best to restart the child. 
- This is carried out by:
    - Creating a new instance of the underlying Actor class.
    - Replacing the failed instance with the fresh one inside the child’s ActorRef.
    - The new actor then resumes processing its mailbox.
- The restart is not visible outside of the actor itself.
- The message during which the failure occurred is not re-processed.
- The sequence of events during a restart is the following:
    1. suspend the actor (which means that it will not process normal messages until resumed), and recursively suspend all children.
    2. call the old instance’s preRestart hook (defaults to sending termination requests to all children and calling postStop).
    3. wait for all children which were requested to terminate (using context.stop()) during preRestart to actually terminate; this—like all actor operations—is non-blocking, the termination notice from the last killed child will effect the progression to the next step.
    4. create new actor instance by invoking the originally provided factory again.
    5. invoke postRestart on the new instance (which by default also calls preStart).
    6. send restart request to all children which were not killed in step 3; restarted children will follow the same process recursively, from step 2.
    7. resume the actor.





# What Lifecycle Monitoring Means





# One-For-One Strategy vs. All-For-One Strategy




