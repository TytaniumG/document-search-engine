# Project 3: Benchmark Results

## Raw Timing Data

### Dataset: 10,000 Documents

- **Index build time:** 118 ms  
- **Active documents:** 10,000  
- **Unique terms in index:** 10,140  
- **Average term search time (hash / `invertedIndex`):** 1.50 μs  

**Multi-term `searchAllTerms` (average per query):**

Query terms | Time (μs)
2             5.07      
3             3.04      
5             3.00      

### Dataset: 100,000 Documents

- **Index build time:** 657 ms  
- **Active documents:** 100,000  
- **Unique terms in index:** 100,140  
- **Average term search time (hash / `invertedIndex`):** 1.53 μs  

**Multi-term `searchAllTerms` (average per query):**

Query terms | Time (μs) 
2             1.32      
3             2.25      
5             2.61      

## Brief Analysis

### Term search (hash)

Average time per `searchTerm` stays essentially flat when going from 10k to 100k documents (~1.50 μs vs ~1.53 μs). That matches **O(1) average** behavior for a `HashMap` lookup plus work proportional to the **posting list** returned (here, many terms still point to large sets of documents, so sorting that list dominates more than the map probe).

Compared to Project 2, where term lookup was a **single `TreeMap` lookup**, the hash-based inverted index targets **constant-time access to the posting list head**; with large posting lists, building and sorting the result list can still cost noticeable time, which is why times are in the low microsecond range rather than “free.”

### Multi-term search

Multi-term latency is sensitive to **which random terms** co-occur in each query (posting sizes and overlap), JVM warmup, and CPU caches, so **10k vs 100k rows are not strictly monotonic** in the table above. In general, cost grows with **more terms** and with **larger intermediate sets** if the intersection order is poor. Starting intersections from the **smallest posting list** keeps the working set small early, which is the intended effect of the implementation.

### Index build

Build time scales **roughly linearly** with document count (118 ms → 657 ms for a 10× increase in documents), dominated by tokenization and updates to **three** structures (`log`, `termIndex`, `invertedIndex`, plus `docIdIndex`).

### Takeaway

Hashing makes **doc id checks** and **term → posting list** access very fast on average, while the **TreeMap** is still required for **prefix** and **range** queries that need sorted keys. The trade-off is **extra memory** and the obligation to keep **all indexes consistent** on every add and logical delete.