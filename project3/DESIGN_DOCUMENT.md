# Project 3 Design Document

**Student Name:** Tyler Gaines  
**Date:** 4/23/2026

---

## 1. System Architecture

### 1.1 Index Structure Overview

```
DocumentStore contains:
1. CircularBuffer<Document> log              // Project 1 storage (append-only)
2. TreeMap<String, Set<Document>> termIndex  // Ordered vocabulary → postings
3. HashMap<String, HashSet<Document>> invertedIndex  // Term → postings (hash)
4. HashMap<String, Document> docIdIndex      // docId → Document (hash)
5. int activeCount                           // O(1) size()
```

**Explain the role of each structure:**

**buffer (`log`):** Same as Project 2: append-only ingestion order, documents are never physically removed. See the Project 2 design document for buffer behavior and iteration semantics.

**termIndex (TreeMap):** Unchanged in role from Project 2: keys are normalized terms in sorted order; each value is a posting set (implemented as `TreeSet` with a fixed `Comparator` so postings stay sorted for deterministic behavior). Used for **prefix** and **range** queries via `subMap`, which require an ordered map.

**invertedIndex (`HashMap<String, HashSet<Document>>`):**

- **Purpose:** Map each normalized term to the set of documents that contain that term, for **average O(1)** lookup of postings by term.
- **What operations use it:** `searchTerm`, `searchAllTerms` (posting fetch per term).
- **Why a hash table:** Vocabulary lookup by string key is the hot path for term search; hashing avoids scanning the tree or the log.

**docIdIndex (`HashMap<String, Document>`):**

- **Purpose:** Map each `docId` to its `Document` object (including logically deleted documents still in the buffer).
- **What operations use it:** `addDocument` (duplicate check and insert), `containsDocument`, `deleteDocument` (find document without scanning the log).
- **Why a hash table:** Id lookup by string should be **O(1) average**, replacing the Project 2 linear scan over the buffer.

### 1.2 Why Three Indexes?

**Question:** Why not just use invertedIndex for everything?

**Answer:** The inverted index is keyed only by **exact term**. It does not support **sorted enumeration of keys** or **lexicographic ranges** in the way a `TreeMap` does. Operations like `prefixSearch("prog")` and `rangeTerms("beta", "gamma")` need ordered keys or sub-range views. Those are natural on `termIndex` and would be inefficient or awkward to simulate on a plain hash map without a separate ordered structure (which would duplicate the vocabulary anyway).

**Question:** Why not just use termIndex for everything?

**Answer:** A `TreeMap` gives **O(log n)** lookups on vocabulary size *n*, and posting sets may still require iteration. For high-traffic **exact-term** search and **docId** checks, hashing provides **O(1) average** bucket access to the right posting set or document. Keeping both structures matches common systems practice: **hash for point lookups**, **tree (or trie) for ordered queries**.

---

## 2. Duplicate docId Handling

### 2.1 Implementation

**Where do you check for duplicates?** At the start of `addDocument`, before writing to the buffer or any index: `docIdIndex.containsKey(document.getDocId())`.

**What happens if duplicate is found?** Throw `IllegalArgumentException` immediately; nothing is appended and no indexes are updated.

**Pseudocode:**

```
addDocument(Document d):
    if d is null: throw IllegalArgumentException
    if docIdIndex contains d.docId: throw IllegalArgumentException  // any status

    log.addLast(d)

    uniqueTerms = unique tokenize(d.text)
    for each term t in uniqueTerms:
        termIndex[t].add(d)           // TreeSet ordered by timestamp, docId
        invertedIndex[t].add(d)       // HashSet

    docIdIndex.put(d.docId, d)
    activeCount++
```

### 2.2 Edge Cases

**What if a document was deleted and we try to add the same docId again?** The old row remains in `docIdIndex` (and in the buffer) with status `DELETED`. `containsKey` is still true, so **add is rejected**.

**Should this be allowed or rejected?** **Rejected**, per assignment spec: duplicate `docId` is disallowed regardless of status.

**Justify your decision:** Logical deletion preserves history and buffer positions; allowing reuse of the same `docId` would either overwrite the map entry (breaking references in the log) or require ambiguous identity rules. Rejecting duplicates keeps **one canonical `Document` per `docId`** for the lifetime of the store.

---

## 3. searchAllTerms() Strategy

### 3.1 Intersection Algorithm

**Pseudocode:**

```
searchAllTerms(List<String> terms):
    if terms is null or empty: return empty list

    normalized = []
    for each raw in terms:
        nt = normalizeQueryTerm(raw)   // tokenize; use first token; empty → abort
        if nt is null: return empty list
        normalized.add(nt)

    sets = []
    for each nt in normalized:
        s = invertedIndex.get(nt)
        if s is null or empty: return empty list
        sets.add(s)

    sort sets by increasing size

    candidate = copy of sets[0]
    for i = 1 .. sets.length-1:
        candidate.retainAll(sets[i])
        if candidate is empty: return empty list

    results = active documents from candidate
    sort results by (timestamp asc, docId asc)
    return results
```

### 3.2 Intersection Order Strategy

**Question:** What order do you intersect the sets?

**My strategy:** After collecting each term’s posting `HashSet` from `invertedIndex`, **sort those sets by size (smallest first)**. Copy the smallest set into `candidate`, then repeatedly `candidate.retainAll(nextSet)` in increasing size order.

**Why this order?** `retainAll(B)` work is driven by the **size of the smaller** of the two operand sets in typical implementations. Starting from the **globally smallest** posting list minimizes the size of `candidate` before each subsequent intersection, reducing comparisons when later sets are large.

**Example demonstrating the benefit:**

Given terms: ["java", "algorithm", "sorting"]

- "java" has 1000 matching documents  
- "algorithm" has 500 matching documents  
- "sorting" has 10 matching documents  

**My algorithm would:**

**Step 1:** Sort sets by size → intersect order: sorting (10), algorithm (500), java (1000).

**Step 2:** `candidate = copy(10)`; `candidate.retainAll(500)` → at most 10 elements.

**Step 3:** `candidate.retainAll(1000)` → at most size of `candidate` after step 2.

**Result:** The expensive intersections never run `retainAll` starting from 1000 elements.

**Why this is efficient:** Naive order (e.g. largest first) would start `candidate` at 1000 and intersect down; smallest-first caps early work near **k**, the size of the smallest posting list (in the best case, the result cannot be larger than the smallest list).

### 3.3 RT Complexity Analysis

**My searchAllTerms() complexity:** **O(q log q + k q + r log r)** (matching the assignment’s target shape), with hash lookups treated as **O(1) average**.

**Explanation:**

- Getting sets for *q* terms: **O(q)** hash lookups → **O(q)** average.  
- Sorting *q* set references by size: **O(q log q)**.  
- Intersecting: each `retainAll` is bounded by the current candidate size; with smallest-first ordering, the total intersection work is **O(k q)** in the worst case where *k* is the smallest set size (often better in practice).  
- Sorting final results: **O(r log r)** for *r* returned documents.

**Variables:**

- *q* = number of query terms  
- *r* = size of result set  
- *k* = size of smallest posting set among the query terms  

---

## 4. Load Factor & HashMap Performance (25 points)

### 4.1 Load Factor Basics

**What is Java HashMap's default load factor?** **0.75**.

**What does load factor mean?** It is the maximum **average entries per bucket** the map tolerates before it **resizes** (rehashes) the table. When `size > capacity * loadFactor`, the internal array grows.

**What happens when load factor is exceeded?** The table **doubles in capacity** (default resize policy), entries are **rehashed** into the new buckets, and inserts continue.

### 4.2 Impact on Performance

**How does load factor affect lookup performance?**

- **Low load factor (e.g., 0.5):** Fewer keys per bucket on average → fewer collisions → faster lookups, but **more wasted space**.  
- **High load factor (e.g., 0.9):** Smaller table → **more collisions** (longer chains or more treeified buckets in modern JDK) → slower lookups, less memory.

**How does load factor affect collision behavior?** Higher load factor increases collision rate before the next resize; lookups degrade from near O(1) toward more comparisons per key.

**How does load factor affect memory usage?** Lower load factor → **larger** backing array for the same number of entries → higher memory footprint.

### 4.3 Resizing Costs

**When does HashMap resize?** When the number of entries exceeds `capacity * loadFactor` (after an insert that crosses the threshold).

**What is the cost of resizing?** **O(n)** over the current *n* entries to allocate a larger array and rehash each entry into new buckets.

**In your benchmark with 100K documents:**

- **Initial HashMap capacity:** default **16** for `docIdIndex` and `invertedIndex` (unless constructed otherwise).  
- **Expected number of resizes:** logarithmic in final size: roughly **log₂(N/16)** rounds of doubling as *N* grows for each map’s peak entry count (exact count depends on distinct docIds vs distinct terms).  
- **Impact on total insertion time:** Resizes add **spikes** during bulk load; most of build time is still dominated by **tokenization** and **TreeMap / TreeSet** maintenance for `termIndex`.

### 4.4 Optimization Opportunity

**Could you improve performance by pre-sizing the HashMap?** Yes. If you expect roughly **D** documents and **T** distinct terms, constructors such as `new HashMap<>(expectedSize)` (with size derived from `expectedSize / 0.75f` ceiling) can **reduce reallocations** during bulk ingestion. Trade-off: **higher upfront memory** if estimates are too large.

---

## 6. Index Consistency

### 6.1 Synchronization Requirements

**List all operations that modify indexes:**

1. **addDocument()** must update:

   - `log` (append)  
   - `termIndex` (each unique term’s posting set)  
   - `invertedIndex` (same terms)  
   - `docIdIndex` (docId → document)  
   - `activeCount`  

2. **deleteDocument()** must update:

   - `termIndex` (remove document from each term’s postings; remove empty keys)  
   - `invertedIndex` (remove document from each term’s set; remove empty keys)  
   - document status (`markAsDeleted`)  
   - `activeCount` (decrement)  
   - **Must not** remove from `log` or `docIdIndex` (spec).

### 6.2 Failure Modes

**Scenario 1:** Document in buffer but not in docIdIndex  

**How could this happen?** Bug: `addDocument` wrote to `log` but failed before `docIdIndex.put`, or manual corruption.

**What would break?** `containsDocument` / `deleteDocument` would not find the document; indexes and buffer would disagree.

**Scenario 2:** Document in invertedIndex but not in termIndex  

**How could this happen?** Bug updating only one structure on add/delete.

**What would break?** `searchTerm` / `searchAllTerms` vs `prefixSearch` / `rangeTerms` would disagree; instructors’ consistency tests would fail.

**Scenario 3:** DELETED document still in invertedIndex  

**How could this happen?** Bug skipping removal on delete.

**What would break?** Deleted documents could reappear in search results (if not filtered), violating logical deletion and test expectations.

---

## 7. Operation Design & Complexity (30 points)

### 7.1 containsDocument()

**Algorithm:**

```
containsDocument(String docId):
    if docId is null: return false
    d = docIdIndex.get(docId)
    return d != null && d.isActive()
```

**RT Complexity:** **O(1) average** (hash lookup + constant checks).

**Explanation:** `HashMap.get` is O(1) average; no scan of the circular buffer.

### 7.3 deleteDocument() - Improved from Project 2

**Project 2 complexity:** **O(m + L + t log n)** — included **O(m)** scan of the buffer to find the document by id.

**Project 3 complexity:** **O(L + t log n)** (assignment target).

**What changed?** Lookup is **O(1) average** via `docIdIndex.get(docId)` instead of **O(m)** linear search over `m` log entries.

**Algorithm:**

```
deleteDocument(String docId):
    if docId is null: return false
    doc = docIdIndex.get(docId)
    if doc is null or not doc.isActive(): return false

    for each unique term t in tokenize(doc.text):
        remove doc from termIndex[t]; remove empty keys
        remove doc from invertedIndex[t]; remove empty keys

    doc.markAsDeleted()
    activeCount--
    return true
```

**Bottleneck now:** Retokenizing text (**O(L)**) and **per-term** updates on the `TreeMap` / `TreeSet` (**O(t log n)**-shaped work for vocabulary and posting-tree maintenance), not buffer scanning.

---

## 8. Performance Analysis (35 points)

### 8.1 Benchmark Setup

**Dataset sizes tested:** **10,000** and **100,000** documents (see `Benchmark.java`).

**Number of searches per measurement:**

- Hash term search: **20** `searchTerm` calls **per distinct term** (full vocabulary from `prefixSearch("")`).  
- Multi-term: **1,000** `searchAllTerms` calls per row in the table, after **50** warmup queries.

**Hardware specs (optional but helpful):** Windows PC; Java HotSpot VM (exact JDK/build may vary). Times vary with CPU load; numbers recorded in `BENCHMARK_RESULTS.md` come from a representative local run.

### 8.2 Multi-Term Search Results

| # Terms | Dataset Size | Search Time (μs) |
|---------|--------------|------------------|
| 2       | 10,000       | 5.07             |
| 3       | 10,000       | 3.04             |
| 5       | 10,000       | 3.00             |
| 2       | 100,000      | 1.32             |
| 3       | 100,000      | 2.25             |
| 5       | 100,000      | 2.61             |

**Analysis:** See `BENCHMARK_RESULTS.md` for raw build times and hash search averages. Multi-term times fluctuate with **random query composition** (posting sizes and overlap) and JVM effects, so 10k vs 100k is not strictly monotonic per row.

**How does performance change with more terms?** More terms mean **more intersections** and a larger **sort** of the result list in the worst case, so cost generally grows with *q*. Random draws can accidentally pick “easy” queries (small overlaps), which can make averages **non-monotonic** in *q* on small benchmark samples.

**Does your intersection strategy matter?** Yes. Intersecting **smallest posting lists first** reduces typical work versus always intersecting in query order, especially when one term is rare and others are common.

**Did you test different intersection orders?** Not as a separate timed benchmark in code; the implementation uses **size-sorted** order only. A/B testing naive vs sorted order would be a straightforward extension.

---

## 9. Limitations & Future Work

### 9.1 Current Limitations

**Limitation 1: Memory Overhead**

**Issue:** Each document reference may appear in **`termIndex`**, **`invertedIndex`**, **`docIdIndex`**, and **`log`**.

**Impact:** Roughly **double posting storage** (tree + hash) for terms, plus id map, increases RAM versus Project 2 alone.

**When would this become a problem?** Very large corpora with **long documents** and **huge vocabularies** (many unique terms).

**Limitation 2: Worst-case hash behavior**

**Issue:** Hash maps degrade under **adversarial** or pathological key distributions (many collisions).

**Impact:** Lookups can approach **O(n)** in the worst case unless mitigated (e.g. better hashing, treeified buckets in JDK).

**When would this become a problem?** Rare in normal text indexing; more relevant in **security-sensitive** services.

---

## 10. Reflection (10 points)

### 10.1 Key Learning

**What was the most important concept you learned?** **Complementary indexes:** no single structure is best for every access pattern; combining **hash maps** for point lookups with **ordered maps** for range/prefix queries mirrors real search infrastructure.

**How does this relate to real search engines?** Inverted files for **term → postings**, B-trees or tries for **lexical navigation**, and doc-id maps for **direct retrieval**—all kept **consistent** on updates.

### 10.2 Design Decisions

**What was your hardest design decision?** Choosing **smallest-first intersection** while keeping **deterministic** result order via a fixed document comparator (timestamp, then `docId`).

**If you could redesign from scratch, what would you change?** Consider **shared posting list objects** or **compressed postings** to cut duplicate structure cost, and optional **pre-sized** hash tables after profiling typical corpus sizes.

### 10.3 Trade-offs

**What's the biggest trade-off in this design?** **Memory for speed and simpler query code:** maintaining **three** indexes doubles vocabulary work on writes.

**Is it worth it? Why or why not?** For read-heavy workloads (many searches per insert), **yes**—microsecond-scale lookups and deletes without scanning the log justify the extra RAM and update logic.

---

## 11. Statement of Collaboration

**Did you discuss this project with anyone?** No collaboration on the implementation beyond course expectations.

**What external resources did you consult?** Course handouts, Java SE documentation for `HashMap`, `TreeMap`, and `Collections`, and the Project 2 design notes for buffer and tree indexing behavior.

**List any code snippets adapted from external sources:** None beyond standard library usage and the provided Project 1 `CircularBuffer` / Project 2–style structure carried forward in the starter materials.
