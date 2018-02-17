# Actor References, Paths and Addresses - Overview
- This chapter describes how actors are identified and located within a possibly distributed _Actor System_. 
- It ties into the central idea that:
    - [Actor Systems](../02-actor-system) form intrinsic supervision hierarchies. 
    - Communication between actors is transparent with respect to their placement across multiple network nodes.
    
![](https://doc.akka.io/docs/akka/current/general/ActorPath.png)
The above image displays the relationship between the most important entities within an _Actor System_

# What is an Actor Reference?
- An actor reference is a subtype of `ActorRef`
    - Whose foremost purpose is to support sending messages to the actor it represents. 
- Each actor has access to:
    - Its canonical (local) reference through the `self` field.
    - A reference representing the sender of the current message through the `sender()` method.

## Different types of actor references

### Purely local actor references
- Used by _Actor Systems_ which are not configured to support networking functions. 
- These actor references will not function if sent across a network connection to a remote JVM.

### Local actor references when remoting is enabled
- Used by _Actor Systems_ for references which represent actors within the same JVM.
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
- **`PromiseActorRef`**:
    - The special representation of a `Promise` for the purpose of being completed by the response from an actor. 
    - `akka.pattern.ask` creates this actor reference.
- **`DeadLetterActorRef`**:
    - The default implementation of the dead letters service to which Akka routes all messages whose destinations are shut down or non-existent.
- **`EmptyLocalActorRef`**:
    - Is what Akka returns when looking up a non-existent local actor path.
    - It is equivalent to a `DeadLetterActorRef`.
    - It retains its path so that Akka can send it over the network and compare it to other existing actor references for that path, some of which might have been obtained before the actor died.

### One-off internal implementations
- There is an actor reference which does not represent an actor but acts only as a pseudo-supervisor for the root guardian.
    - We call it “the one who walks the bubbles of space-time”.
- The first logging service started before actually firing up actor creation facilities:
    - A fake actor reference which accepts log events and prints them directly to standard output.
    - It is `Logging.StandardOutLogger`.

# What is an Actor Path?
- Since actors are created in a strictly hierarchical fashion:
    - There exists a unique sequence of actor names.
        - Given by recursively following the supervision links between child and parent down towards the root of the _Actor System_. 
    - This sequence can be seen as enclosing folders in a file system, hence we adopted the name “path” to refer to it.
- An actor path consists of:
    - An anchor, which identifies the _Actor System_.
    - Followed by the concatenation of the path elements, from root guardian to the designated actor.
    - The path elements are the names of the traversed actors and are separated by slashes.

## What is the Difference Between Actor Reference and Path?
- An **actor reference** designates a single actor and the life-cycle of the reference matches that actor’s life-cycle.
- An **actor path** represents a name which may or may not be inhabited by an actor.
    - The path itself does not have a life-cycle.
    - It never becomes invalid. 
- You can create an actor path without creating an actor.
- You cannot create an actor reference without creating corresponding actor.
- You can create an actor, terminate it, and then create a new actor with the same actor path. 
- The newly created actor is a new incarnation of the actor, it is not the same actor. 
- An actor reference to the old incarnation is not valid for the new incarnation. 
- Messages sent to the old actor reference will not be delivered to the new incarnation even though they have the same path.

## Actor Path Anchors
- Each actor path:
    - Has an address component, describing the protocol and location by which the corresponding actor is reachable.
    - Followed by the names of the actors in the hierarchy from the root up. 
```
"akka://my-sys/user/service-a/worker1"                   // purely local
"akka.tcp://my-sys@host.example.com:5678/user/service-b" // remote
```
- Here, `akka.tcp` is the default remote transport.
- Other transports are pluggable. 
- The interpretation of the host and port part depends on the transport mechanism used.
- It must abide by the URI structural rules.

## Logical Actor Paths
- The unique path obtained by following the parental supervision links, towards the root guardian. 
- This path matches exactly the creation ancestry of an actor.
- It is completely deterministic as soon as the _Actor System_’s remoting configuration is set.

## Physical Actor Paths
- Configuration-based remote deployment means that an actor may be created on a different network host than its parent.
- In this case, following the actor path from the root guardian up entails traversing the network, which is a costly operation. 
- Therefore, each actor also has a **physical path**:
    - Starting at the _root guardian_ of the _Actor System_ where the actual actor object resides. 
- Using this path as **sender reference** when querying other actors:
    - Will let them reply directly to this actor, minimizing delays incurred by routing.
- A physical actor path never spans multiple _Actor Systems_ or JVMs. 
- This means that the logical path (supervision hierarchy) and the physical path (actor deployment) of an actor may diverge if one of its ancestors is remotely supervised.

## Actor path alias or symbolic link?
- As in some real file-systems you might think of a “path alias” or “symbolic link” for an actor:
    - I.e. one actor may be reachable using more than one path. 
- However, actor hierarchy is different from file system hierarchy. 
- You cannot freely create actor paths like symbolic links to refer to arbitrary actors. 
- An actor path must be either:
    - A logical path which represents supervision hierarchy.
    - A physical path which represents actor deployment.

# How are Actor References obtained?
- There are two general categories to how actor references may be obtained: 
    - By creating actors.
    - By looking them up, with these two sub-categories:
        - Creating actor references from concrete actor paths.
        - Querying the logical actor hierarchy.

## Creating Actors
- An _Actor System_ is typically started:
    - By creating actors beneath the guardian actor using the `ActorSystem.actorOf` method.
    - Then using `ActorContext.actorOf` from within the created actors to spawn the actor tree. 
- These methods return a reference to the newly created actor. 
- Each actor has direct access (through its `ActorContext`) to references for its parent, itself and its children. 
- These references may be sent within messages to other actors, enabling those to reply directly.

## Looking up Actors by Concrete Path
- In addition, actor references may be looked up using the `ActorSystem.actorSelection` method. 
- The selection can be used for communicating with said actor.
    - The actor corresponding to the selection is looked up when delivering each message.
- To acquire an `ActorRef` that is bound to the life-cycle of a specific actor:
    - You need to send the built-in `Identify` message to the actor.
    - Use the `sender()` reference of a reply from the actor.

### Absolute vs. Relative Paths
- There is also `ActorContext.actorSelection`, which is available inside any actor as context.actorSelection. 
- This yields an actor selection much like its twin on `ActorSystem`.
    - Instead of looking up the path starting from the root of the actor tree, it starts out on the current actor. 
- Path elements consisting of two dots ("..") may be used to access the parent actor. 
- You can for example send a message to a specific sibling:
```scala
context.actorSelection("../brother") ! msg
```
- Absolute paths may of course also be looked up on context in the usual way:
```scala
context.actorSelection("/user/serviceA") ! msg
```

## Querying the Logical Actor Hierarchy
- Since the _Actor System_ forms a file-system like hierarchy, matching on paths is possible in the same way as supported by Unix shells: 
    - You may replace parts of path element names with wildcards (`*` and `?`) to formulate a selection.
    - This may match zero or more actual actors. 
    - Because the result is not a single actor reference, it has a different type `ActorSelection`.
    - It does not support the full set of operations an `ActorRef` does. 
    - Selections may be formulated using the `ActorSystem.actorSelection` and `ActorContext.actorSelection` methods.
    - They do support sending messages:
```scala
context.actorSelection("../*") ! msg
```
- This will send `msg` to all siblings including the current actor. 
- As for references obtained using `actorSelection`:
    - A traversal of the supervision hierarchy is done in order to perform the message send. 
- As the exact set of actors which match a selection may change even while a message is making its way to the recipients:
    - It is not possible to watch a selection for liveliness changes. 
    - In order to do that, resolve the uncertainty by sending a request and gathering all answers.
    - Extracting the sender references, and then watch all discovered concrete actors. 
    - This scheme of resolving a selection may be improved upon in a future release.

## Summary: actorOf vs. actorSelection
- **`actorOf`** only ever creates a new actor.
    - It creates it as a direct child of the context on which this method is invoked.
    - Which may be any actor or _Actor System_.
- **`actorSelection`** only ever looks up existing actors when messages are delivered.
    - It does not create actors, or verify existence of actors when the selection is created.

# Actor Reference and Path Equality





# Reusing Actor Paths





# The Interplay with Remote Deployment





# What is the Address part used for?





# Top-Level Scopes for Actor Paths










