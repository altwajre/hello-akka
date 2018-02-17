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
# Behavior
# Mailbox
# Child Actors
# Supervisor Strategy
# When an Actor Terminates
