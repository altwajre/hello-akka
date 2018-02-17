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
    - Explicit state machine (e.g. using the [FSM module](TODO)).
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
    - An actor’s state can be automatically recovered to the state before a restart by persisting received messages and replaying them after restart.
        - See [Persistence](TODO).
  
# Behavior
# Mailbox
# Child Actors
# Supervisor Strategy
# When an Actor Terminates
