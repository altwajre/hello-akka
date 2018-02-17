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




# What Restarting Means





# What Lifecycle Monitoring Means





# One-For-One Strategy vs. All-For-One Strategy




