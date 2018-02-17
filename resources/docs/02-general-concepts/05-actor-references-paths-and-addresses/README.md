# Actor References, Paths and Addresses - Overview
- This chapter describes how actors are identified and located within a possibly distributed actor system. 
- It ties into the central idea that:
    - [Actor Systems](../02-actor-system) form intrinsic supervision hierarchies. 
    - Communication between actors is transparent with respect to their placement across multiple network nodes.
    
![](https://doc.akka.io/docs/akka/current/general/ActorPath.png)
The above image displays the relationship between the most important entities within an actor system

# What is an Actor Reference?
- An actor reference is a subtype of `ActorRef`
    - Whose foremost purpose is to support sending messages to the actor it represents. 
- Each actor has access to:
    - Its canonical (local) reference through the `self` field.
    - A reference representing the sender of the current message through the `sender()` method.

## Different types of actor references

### Purely local actor references
- Used by actor systems which are not configured to support networking functions. 
- These actor references will not function if sent across a network connection to a remote JVM.

### Local actor references when remoting is enabled
- Used by actor systems for references which represent actors within the same JVM.
- They include protocol and remote addressing information in order to be reachable when sent over the network.

### Local actor reference subtype for routers
- I.e. actors mixing in the `Router` trait. 
- Its logical structure is the same as for the aforementioned local references.
- Sending a message to them dispatches to one of their children directly instead.

### Remote actor references
- Represent actors which are reachable using remote communication.
- I.e. sending messages to them will serialize the messages transparently and send them to the remote JVM.

### Special types of actor references
- Behave like local actor references for all practical purposes.
- **PromiseActorRef**:
    - The special representation of a `Promise` for the purpose of being completed by the response from an actor. 
    - `akka.pattern.ask` creates this actor reference.
- **DeadLetterActorRef**:
    - The default implementation of the dead letters service to which Akka routes all messages whose destinations are shut down or non-existent.
- **EmptyLocalActorRef**:
    - Is what Akka returns when looking up a non-existent local actor path.
    - It is equivalent to a `DeadLetterActorRef`.
    - It retains its path so that Akka can send it over the network and compare it to other existing actor references for that path.
    - Some of which might have been obtained before the actor died.

### And then there are some **one-off internal** implementations which you should never really see:
- There is an actor reference which does not represent an actor but acts only as a pseudo-supervisor for the root guardian, we call it “the one who walks the bubbles of space-time”.
- The first logging service started before actually firing up actor creation facilities is a fake actor reference which accepts log events and prints them directly to standard output; it is Logging.StandardOutLogger.




# What is an Actor Path?





# How are Actor References obtained?





# Actor Reference and Path Equality





# Reusing Actor Paths





# The Interplay with Remote Deployment





# What is the Address part used for?





# Top-Level Scopes for Actor Paths










