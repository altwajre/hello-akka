# Part 4: Working with Device Groups - Overview 
## Actor hierarchy guidelines:
- In general, prefer larger granularity. 
- Introducing more fine-grained actors than needed causes more problems than it solves.
- Add finer granularity when the system requires:
    - Higher concurrency.
    - Complex conversations between actors that have many states. We will see a very good example for this in the next chapter.
    - Sufficient state that it makes sense to divide into smaller actors.
    - Multiple unrelated responsibilities. Using separate actors allows individuals to fail and be restored with little impact on others.

# Device manager hierarchy
# The Registration Protocol
# Adding registration support to device actors
# Adding registration support to device group actors
# Creating device manager actors
# What’s next?
