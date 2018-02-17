# Message Delivery Reliability - Overview
- Akka helps you build reliable applications which make use of multiple processor cores.
    - In one machine (“scaling up”) or distributed across a computer network (“scaling out”). 
- The key abstraction to make this work is that all interactions between actors happen via message passing.
    - Which is why the precise semantics of how messages are passed between actors deserve their own chapter.
- In order to give some context to the discussion below, consider an application which spans multiple network hosts. 
- The basic mechanism for communication is the same whether sending to an actor on the local JVM or to a remote actor.
- There will be observable differences in the latency of delivery and the reliability. 
- In case of a remote message send there are obviously more steps involved which means that more can go wrong. 
- Another aspect is that local sending will just pass a reference to the message inside the same JVM.
    - Without any restrictions on the underlying object which is sent.
- Whereas a remote transport will place a limit on the message size.
- Writing your actors such that every interaction could possibly be remote is the safe, pessimistic bet. 
- It means to only rely on those properties which are always guaranteed and which are discussed in detail below. 
- This has some overhead in the actor’s implementation. 
- If you are willing to sacrifice full location transparency:
    - For example in case of a group of closely collaborating actors.
    - You can place them always on the same JVM and enjoy stricter guarantees on message delivery. 
    - The details of this trade-off are discussed further below.
- As a supplementary part we give a few pointers at how to build stronger reliability on top of the built-in ones. 
- The chapter closes by discussing the role of the “Dead Letter Office”.

# The General Rules
- These are the rules for message sends:
    - **at-most-once delivery**, i.e. no guaranteed delivery.
    - **message ordering per sender–receiver pair**.
- The first rule is typically found also in other actor implementations while the second is specific to Akka.

## Discussion: What does “at-most-once” mean?
- When it comes to describing the semantics of a delivery mechanism, there are three basic categories:
    - **at-most-once** delivery means that for each message handed to the mechanism, that message is delivered zero or one times.
        - In more casual terms it means that messages may be lost.
    - **at-least-once** delivery means that for each message handed to the mechanism potentially multiple attempts are made at delivering it.
        - Such that at least one succeeds.
        - In more casual terms this means that messages may be duplicated but not lost.
    - **exactly-once** delivery means that for each message handed to the mechanism exactly one delivery is made to the recipient.
        - The message can neither be lost nor duplicated.
- The first one is the cheapest.
    - Highest performance, least implementation overhead.
    - It can be done in a fire-and-forget fashion without keeping state at the sending end or in the transport mechanism. 
- The second one requires retries to counter transport losses.
    - This means keeping state at the sending end and having an acknowledgement mechanism at the receiving end. 
- The third is most expensive.
    - Has consequently worst performance.
    - In addition to the second it requires state to be kept at the receiving end in order to filter out duplicate deliveries.

## Discussion: Why No Guaranteed Delivery?
- At the core of the problem lies the question what exactly this guarantee shall mean:
1. The message is sent out on the network?
2. The message is received by the other host?
3. The message is put into the target actor’s mailbox?
4. The message is starting to be processed by the target actor?
5. The message is processed successfully by the target actor?
- Each one of these have different challenges and costs.
- It is obvious that there are conditions under which any message passing library would be unable to comply.
- Think for example about configurable mailbox types and how a bounded mailbox would interact with point 1.
- Or even what it would mean to decide upon the “successfully” part of point 5.
- Along those same lines goes the reasoning in [Nobody Needs Reliable Messaging](http://www.infoq.com/articles/no-reliable-messaging). 
- The only meaningful way for a sender to know whether an interaction was successful:
    - Is by receiving a business-level acknowledgement message.
    - This is not something Akka could make up on its own.
- Akka embraces distributed computing and makes the fallibility of communication explicit through message passing.
    - Therefore it does not try to lie and emulate a **leaky abstraction**. 
    - This is a model that has been used with great success in Erlang and requires the users to design their applications around it.
- Another angle on this issue is that by providing only basic guarantees:
    - Those use cases which do not need stronger reliability do not pay the cost of their implementation.
    - It is always possible to add stronger reliability on top of basic ones.
    - It is not possible to retro-actively remove reliability in order to gain more performance.

## Discussion: Message Ordering
- The rule more specifically is that:
    - _For a given pair of actors, messages sent directly from the first to the second will not be received out-of-order_. 
- The word _directly_ emphasizes that this guarantee only applies when sending with the `tell` operator to the final destination.
- Not when employing mediators or other message dissemination features (unless stated otherwise).
- The guarantee is illustrated in the following:
```
Actor A1 sends messages M1, M2, M3 to A2
Actor A3 sends messages M4, M5, M6 to A2
```
- This means that:
1. If M1 is delivered it must be delivered before M2 and M3
2. If M2 is delivered it must be delivered before M3
3. If M4 is delivered it must be delivered before M5 and M6
4. If M5 is delivered it must be delivered before M6
5. A2 can see messages from A1 interleaved with messages from A3
6. Since there is no guaranteed delivery, any of the messages may be dropped, i.e. not arrive at A2







































# The Rules for In-JVM (Local) Message Sends





# Higher-level abstractions





# Dead Letters










