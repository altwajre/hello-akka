# What is an Actor? - Overview
- An actor is a container for **State**, **Behavior**, a **Mailbox**, **Child Actors** and a **Supervisor Strategy**. 
- All of this is encapsulated behind an **Actor Reference**. 
- Actors have an explicit lifecycle:
    - They are not automatically destroyed when no longer referenced.
    - After having created one, it is your responsibility to make sure that it will eventually be terminated as well.
    - This gives you control over how resources are released [when an Actor terminates](#when-an-actor-terminates).

# Actor Reference
# State
# Behavior
# Mailbox
# Child Actors
# Supervisor Strategy
# When an Actor Terminates
