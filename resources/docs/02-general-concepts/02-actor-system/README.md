# Hierarchical Structure
- The difficulty in designing such a system is how to decide who should supervise what. 
- There is no single best solution, but there are a few guidelines which might be helpful:
    - If one actor manages the work another actor is doing, e.g. by passing on sub-tasks:
        - Then the manager should supervise the child. 
        - The reason is that the manager knows which kind of failures are expected and how to handle them.
    - If one actor carries very important data (i.e. its state shall not be lost if avoidable):
        - This actor should source out any possibly dangerous sub-tasks to children it supervises.
        - And handle failures of these children as appropriate. 
        - Depending on the nature of the requests, it may be best to create a new child for each request.
        - This simplifies state management for collecting the replies. 
        - This is known as the **Error Kernel Pattern** from Erlang.
    - If one actor depends on another actor for carrying out its duty:
        - It should watch that other actor’s liveness and act upon receiving a termination notice. 
        - This is different from supervision, as the watching party has no influence on the supervisor strategy.
        - And it should be noted that a functional dependency alone is not a criterion for deciding where to place a certain child actor in the hierarchy.

# Configuration Container


# Actor Best Practices


# What you should not concern yourself with


# Terminating ActorSystem


