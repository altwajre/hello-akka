# Hierarchical Structure
- Like in an economic organization, actors naturally form hierarchies. 
- One actor, which is to oversee a certain function in the program, might want to split up its task into more manageable pieces. 
- For this purpose it starts child actors which it supervises. 
- The only prerequisite is to know that each actor has exactly one supervisor, which is the actor that created it.
- The quintessential feature of actor systems is that tasks are split up and delegated until they become small enough to be handled in one piece. 
- In doing so:
    - The task itself is clearly structured.
    - The resulting actors can be reasoned about in terms of:
        - Which messages they should process.
        - How they should react normally and how failure should be handled. 
- If one actor does not have the means for dealing with a certain situation:
    - It sends a corresponding failure message to its supervisor, asking for help. 
    - The recursive structure then allows to handle failure at the right level.
- Compare this to layered software design:
    - Defensive programming tries not to leak any failure out and keep everything “under the carpet”.
    - If the problem is communicated to the right person, a better solution can be found.
- The difficulty in designing such a system is how to decide who should supervise what. 
- There is no single best solution, but there are a few guidelines which might be helpful.

### If one actor manages the work another actor is doing, (e.g. by passing on sub-tasks)
- Then the manager should supervise the child. 
- The reason is that the manager knows which kind of failures are expected and how to handle them.

### If one actor carries very important data (i.e. its state shall not be lost if avoidable)
- This actor should source out any possibly dangerous sub-tasks to children it supervises.
- And handle failures of these children as appropriate. 
- Depending on the nature of the requests, it may be best to create a new child for each request.
- This simplifies state management for collecting the replies. 
- This is known as the **Error Kernel Pattern** from Erlang.

### If one actor depends on another actor for carrying out its duty
- It should watch that other actor’s liveness and act upon receiving a termination notice. 
- This is different from supervision, as the watching party has no influence on the supervisor strategy.
- And it should be noted that a functional dependency alone is not a criterion for deciding where to place a certain child actor in the hierarchy.

# Configuration Container
- The actor system as a collaborating ensemble of actors is the natural unit for managing shared facilities like scheduling services, configuration, logging, etc. 
- Several actor systems with different configuration may co-exist within the same JVM without problems.
    - There is no global shared state within Akka itself. 
    - Couple this with the transparent communication between actor systems.
    - Then actor systems themselves can be used as building blocks in a functional hierarchy.

# Actor Best Practices
## Actors should be like nice co-workers
- They should do their job efficiently without bothering everyone else needlessly and avoid hogging resources. 
- Translated to programming this means to process events and generate responses, or more requests, in an event-driven manner. 
- Actors should not block on some external entity:
    - I.e. passively wait while occupying a Thread, which might be a lock, a network socket, etc.
    - If this is unavoidable, see below (TODO: Where???).
    
## Do not pass mutable objects between actors
- In order to ensure that, prefer immutable messages. 
- If the encapsulation of actors is broken by exposing their mutable state to the outside, you are back in normal Java concurrency land with all the drawbacks.

## Actors are made to be containers for behavior and state
- Embracing this means to not routinely send behavior within messages.
- This may be tempting using Scala closures. 
- One of the risks is to accidentally share mutable state between actors.
- This violation of the actor model unfortunately breaks all the properties which make programming in actors such a nice experience.
    
## Top-level actors are the innermost part of your Error Kernel
- Create them sparingly and prefer truly hierarchical systems. 
- This has benefits with respect to:
    - Fault-handling (both considering the granularity of configuration and the performance).
    - It also reduces the strain on the guardian actor, which is a single point of contention if over-used.

# What you should not concern yourself with
- An actor system manages the resources it is configured to use in order to run the actors which it contains. 
- There may be millions of actors within one such system, after all the mantra is to view them as abundant.
    - They weigh in at an overhead of roughly 300 bytes per instance. 
- The exact order in which messages are processed in large systems is not controllable by the application author.
    - This is also not intended. 
    - Take a step back and relax while Akka does the heavy lifting under the hood.

# Terminating ActorSystem
- When you know everything is done for your application, you can call the `terminate` method of `ActorSystem`. 
- That will stop the guardian actor, which in turn will recursively stop all its child actors, the system guardian.
- If you want to execute some operations while terminating `ActorSystem`, look at [CoordinatedShutdown](TODO).

