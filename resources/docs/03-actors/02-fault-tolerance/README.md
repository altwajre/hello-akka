# Fault Tolerance - Overview
- As explained in [Actor Systems](../../02-general-concepts/02-actor-system):
    - Each actor is the supervisor of its children, and as such each actor defines fault handling supervisor strategy. 
- This strategy cannot be changed afterwards as it is an integral part of the actor system’s structure.

# Fault Handling in Practice
- First, let us look at a sample that illustrates one way to handle data store errors:
    - Which is a typical source of failure in real world applications. 
- Of course it depends on the actual application what is possible to do when the data store is unavailable:
    - But in this sample we use a best effort re-connect approach.
- [Read the following source code](https://doc.akka.io/docs/akka/2.5.9/fault-tolerance-sample.html?language=scala). 
- The inlined comments explain the different pieces of the fault handling and why they are added. 
- It is also highly recommended to run this sample as it is easy to follow the log output to understand what is happening at runtime.




# Creating a Supervisor Strategy





# Supervision of Top-Level Actors





# Test Application










