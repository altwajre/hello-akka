# Terminology, Concepts - Overview
- In this chapter we attempt to establish a common terminology to define a solid ground for communicating about concurrent, distributed systems which Akka targets.
- For many of these terms, there is no single agreed definition. 
- We simply seek to give working definitions that will be used in the scope of the Akka documentation.

# Concurrency vs. Parallelism
- Concurrency and parallelism are related concepts, but there are small differences. 
- **Concurrency** means that two or more tasks are making progress even though they might not be executing simultaneously. 
    - E.g. with time slicing where parts of tasks are executed sequentially and mixed with parts of other tasks. 
- **Parallelism** on the other hand arise when the execution can be truly simultaneous.

# Asynchronous vs. Synchronous
- A method call is considered **synchronous** if the caller cannot make progress until the method returns a value or throws an exception. 
- An **asynchronous** call allows the caller to progress after a finite number of steps.
    - The completion of the method may be signalled via some additional mechanism.
    - E.g a registered callback, a Future, or a message.
- A synchronous API may use blocking to implement synchrony, but this is not a necessity. 
- A very CPU intensive task might give a similar behavior as blocking. 
- In general, it is preferred to use asynchronous APIs, as they guarantee that the system is able to progress. 
- Actors are asynchronous by nature: 
    - An actor can progress after a message send without waiting for the actual delivery to happen.

# Non-blocking vs. Blocking
- We talk about **blocking** if the delay of one thread can indefinitely delay some of the other threads. 
    - E.g. a resource which can be used exclusively by one thread using mutual exclusion. 
    - If a thread holds on to the resource indefinitely (e.g. an infinite loop) other threads waiting on the resource can not progress. 
- **Non-blocking** means that no thread is able to indefinitely delay others.
- Non-blocking operations are preferred to blocking ones, as the overall progress of the system is not trivially guaranteed when it contains blocking operations.

# Deadlock vs. Starvation vs. Live-lock
- **Deadlock** arises when several participants are waiting on each other to reach a specific state to be able to progress. 
    - None of them can progress without some other participant to reach a certain state (a “Catch-22” problem) all affected subsystems stall. 
    - It is closely related to blocking, as it is necessary that a participant thread be able to delay the progression of other threads indefinitely.
- **Starvation** happens, when there are participants that can make progress, but there might be one or more that cannot. 
    - A typical scenario is the case of a naive scheduling algorithm that always selects high-priority tasks over low-priority ones. 
    - If the number of incoming high-priority tasks is constantly high enough, no low-priority ones will be ever finished.
- **Livelock** is similar to deadlock as none of the participants make progress. 
    - Instead of being frozen in a state of waiting for others to progress, the participants continuously change their state. 
    - E.g. two participants have two identical resources available. 
    - They each try to get the resource, but they also check if the other needs the resource, too. 
    - If the resource is requested by the other participant, they try to get the other instance of the resource. 
    - In the unfortunate case it might happen that the two participants “bounce” between the two resources, never acquiring it, but always yielding to the other.

# Race Condition
- We call it a **Race condition** when an assumption about the ordering of a set of events might be violated by external non-deterministic effects. 
- Race conditions often arise when:
    - Multiple threads have a shared mutable state.
    - The operations of thread on the state might be interleaved causing unexpected behavior. 
- While this is a common case, shared state is not necessary to have race conditions, for example: 
    - One example could be: a client sending unordered packets (e.g UDP datagrams) P1, P2 to a server. 
    - As the packets might potentially travel via different network routes, it is possible that the server receives P2 first and P1 afterwards. 
    - If the messages contain no information about their sending order it is impossible to determine by the server that they were sent in a different order. 
    - Depending on the meaning of the packets this can cause race conditions.

# Non-blocking Guarantees (Progress Conditions)
## Wait-freedom
- A method is wait-free if every call is guaranteed to finish in a finite number of steps. 
- If a method is bounded wait-free then the number of steps has a finite upper bound.
- From this definition, wait-free methods are:
    - Never blocking, therefore deadlock can not happen.
    - Free of starvation, as each participant can progress when the call finishes.

## Lock-freedom
- Lock-freedom is a weaker property than wait-freedom. 
- In the case of lock-free calls, infinitely often some method finishes in a finite number of steps. 
- From this definition, lock-free calls are: 
    - Free of deadlock.
    - Not free of starvation, as there is no guarantee that all of them eventually finish.

## Obstruction-freedom
- Obstruction-freedom is the weakest non-blocking guarantee discussed here. 
- A method is called obstruction-free if:
    - There is a point in time after which it executes in isolation (other threads become suspended)
    - It finishes in a bounded number of steps. 
- All lock-free objects are obstruction-free, but the opposite is not true.
- _Optimistic concurrency control_ (OCC) methods are usually obstruction-free. 
- The OCC approach is that every participant tries to execute its operation on the shared object.
    - But if a participant detects conflicts from others, it rolls back the modifications, and tries again according to some schedule.
    - If there is a point in time where one of the participants is the only one trying, the operation will succeed.

# Recommended literature
- [The Art of Multiprocessor Programming](https://www.safaribooksonline.com/library/view/the-art-of/9780123973375/)
- [Java Concurrency in Practice](https://www.safaribooksonline.com/library/view/java-concurrency-in/0321349601/)