# Location Transparency - Overview
- The previous section describes how actor paths are used to enable location transparency. 
- This special feature deserves some extra explanation, because the related term “transparent remoting” was used quite differently in the context of programming languages, platforms and technologies.

# Distributed by Default
- Everything in Akka is designed to work in a distributed setting: 
    - All interactions of actors use purely message passing and everything is asynchronous. 
- This effort has been undertaken to ensure that all functions are available equally when running within a single JVM or on a cluster of hundreds of machines. 
- The key for enabling this is to:
    - Go from remote to local by way of optimization.
    - Instead of trying to go from local to remote by way of generalization. 
    - See [A Note on Distributed Computing](https://doc.akka.io/docs/misc/smli_tr-94-29.pdf).

# Ways in which Transparency is Broken
- Designing for distributed execution poses some restrictions on what is possible. 
- The most obvious one is that all messages sent over the wire must be serializable. 
- This **includes closures**, which are used as actor factories (i.e. within `Props`).
    - If the actor is to be created on a remote node.
- Another consequence is that everything needs to be aware of all interactions being fully asynchronous:
    - In a computer network, it may take several minutes for a message to reach its recipient.
    - The probability for a message to be lost is much higher than within one JVM.

# How is Remoting Used?
- We took the idea of transparency to the limit in that there is nearly no API for the remoting layer of Akka: 
    - It is purely driven by configuration. 
- Just write your application according to the principles outlined in the previous sections.
- Then specify remote deployment of actor sub-trees in the configuration file. 
- This way, your application can be scaled out without having to touch the code. 
- The only piece of the API which allows programmatic influence on remote deployment is that:
    - `Props` contain a field which may be set to a specific Deploy instance.
    - This has the same effect as putting an equivalent deployment into the configuration file.
    - If both are given, configuration file wins.

# Peer-to-Peer vs. Client-Server
- Akka Remoting is a communication module for connecting actor systems in a peer-to-peer fashion.
    - It is the foundation for Akka Clustering. 
- The design of remoting is driven by two (related) design decisions:
1. Communication between involved systems is symmetric: 
    - If a system A can connect to a system B then system B must also be able to connect to system A independently.
2. The role of the communicating systems are symmetric in regards to connection patterns: 
    - There is no system that only accepts connections, and there is no system that only initiates connections.
- The consequence of these decisions is that:
    - It is not possible to safely create pure client-server setups with predefined roles (violates assumption 2). 
    - For client-server setups it is better to use _HTTP_ or _Akka I/O_.
- **Important:** Using setups involving **Network Address Translation**, **Load Balancers** or **Docker** containers violates assumption 1:
    - Additional steps need to be taken in the network configuration to allow symmetric communication between involved systems. 
    - In such situations Akka can be configured to bind to a different network address than the one used for establishing connections between Akka nodes. 
    - See [Akka behind NAT or in a Docker container](TODO).

# Marking Points for Scaling Up with Routers
- It is possible to scale up onto more cores by multiplying actor sub-trees which support parallelization.
    - E.g. a search engine processing different queries in parallel. 
- The clones can then be routed to in different fashions, e.g. round-robin. 
- The only thing necessary to achieve this is that:
    - The developer needs to declare a certain actor as “withRouter”.
- Then a router actor will be created which will:
    - Spawn up a configurable number of children of the desired type.
    - Route to them in the configured fashion. 
- Once such a router has been declared, its configuration can be freely overridden from the configuration file.
- Including mixing it with the remote deployment of (some of) the children. 
- See [Routing](TODO).