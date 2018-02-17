# Concurrency vs. Parallelism

# Asynchronous vs. Synchronous

# Non-blocking vs. Blocking

# Deadlock vs. Starvation vs. Live-lock

# Race Condition

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