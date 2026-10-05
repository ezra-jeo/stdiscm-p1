**Objective :**  Basic threading

**Task :** Write a program that will create an **x** number of threads that will search for prime numbers to a given **y** number.  The values x and y should be configurable in a separate config file.

**Requirements :**

1. Different printing variations
    1. Print immediately
        - Thread id and time stamp should be included.
    2. Wait until all threads are done then print everything
2. Different task division schemes
    1. Straight division of search range. (ie for 1 - 1000 and 4 threads the division will be 1-250, 251-500, and so forth)
    2. The search is linear but the threads are for divisibility testing of individual numbers.

**At the start and end of every run there will be a printed timestamp of the start and end time.**

**Deliverables :**

1. Source code for all four (4) variants
2. Video demonstration of all four (4) variants
3. All in one zip file organized in four (4) separate folders for each variant
4. Build / compilation instructions
5. Presentation slides with analysis of the different variants focusing on implementation and performance characteristics.

**Learning Objectives :**

a) Demonstrate how number of threads affect the performance of a task.

b) See how interleaving of output happens when threads are in use.

c) Experience possible performance bottlenecks in joining threads.

d) Explore different task divide and conquer schemes.