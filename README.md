# In-Memory Document Search Engine

A Java search engine that stores documents in memory and answers keyword, multi-term, prefix, and range queries quickly. It combines several coordinated data structures and is benchmarked against its theoretical performance. Built for a data structures course (Jan–May 2026).

## Features

- **Fast term lookup:** a hash-based inverted index gives O(1) average-case lookup of the documents containing a term
- **Multi-term AND search:** posting lists are intersected smallest-first to minimize work on large lists
- **Prefix and range queries:** a `TreeMap` of terms supports ordered queries in O(log n) plus the number of matches
- **Fast delete and existence checks:** a hash map keyed by document ID makes lookups and deletes O(1) on average instead of scanning
- **Logical deletion:** deleted documents are marked inactive and removed from every index, and never appear in results
- **Consistent indexes:** four synchronized data structures stay in sync through every insert and delete

## How It Works

| Structure | Role |
|---|---|
| `CircularBuffer<Document>` | Append-only log of all documents; array-backed, starts at capacity 8 and doubles when full |
| `TreeMap<String, Set<Document>>` | Sorted term index for prefix and range queries; postings ordered by timestamp, then document ID |
| `HashMap<String, Document>` | Document ID index for O(1) existence checks and deletes |
| `HashMap<String, HashSet<Document>>` | Inverted index for O(1) average term lookup |

**Tokenizing:** text is lowercased and split on any non-alphanumeric character. Each document's unique terms are indexed once.

**Result ordering:** results are sorted by timestamp, with document ID as the tie-breaker.

## API

`DocumentStore` supports:

| Method | Description |
|---|---|
| `addDocument(Document)` | Index a document (rejects null and duplicate IDs) |
| `deleteDocument(String docId)` | Logically delete a document and remove it from all indexes |
| `containsDocument(String docId)` | Check whether an active document exists |
| `searchTerm(String term)` | All active documents containing a term |
| `searchAllTerms(List<String> terms)` | Documents containing every term (AND search) |
| `prefixSearch(String prefix)` | Indexed terms that start with a prefix |
| `rangeTerms(String from, String to)` | Indexed terms in an inclusive alphabetical range |
| `iterateLiveDocuments()` | Active documents in insertion order |
| `size()` / `isEmpty()` | Number of active documents |

## Performance

`Benchmark.java` builds synthetic datasets of 10K and 100K documents and measures index build time, single-term search latency, and multi-term (2, 3, and 5 term) search latency. In the synthetic data, most terms appear in only one document, while a handful appear in every document.

### Results

| | 10,000 documents | 100,000 documents |
|---|---|---|
| Index build time | 118 ms | 657 ms |
| Unique terms indexed | 10,140 | 100,140 |
| Average term search (hash index) | 1.50 µs | 1.53 µs |
| Multi-term search, 2 terms | 5.07 µs | 1.32 µs |
| Multi-term search, 3 terms | 3.04 µs | 2.25 µs |
| Multi-term search, 5 terms | 3.00 µs | 2.61 µs |

### Analysis

**Term search:** Average search time stays essentially flat when the dataset grows 10x (1.50 µs vs 1.53 µs), which matches the O(1) average-case behavior of a `HashMap` lookup. The remaining cost comes from building and sorting the result list, which grows with the size of the posting list returned.

**Multi-term search:** Latency depends on which random terms land in each query (posting list sizes and how much they overlap), plus JVM warmup and CPU caches, so the 10K and 100K rows are not strictly ordered. In general, cost grows with more query terms and with larger intermediate sets. Intersecting from the smallest posting list keeps the working set small from the start.

**Index build:** Build time grows roughly linearly with document count (118 ms to 657 ms for 10x the documents). It is dominated by tokenizing and by updating four structures: the circular buffer log, the term tree, the inverted index, and the document ID index.

### Trade-offs

Hashing makes document ID checks and term-to-postings lookups very fast on average. The `TreeMap` is still needed for prefix and range queries, which require sorted keys. The cost is extra memory and the need to keep every index consistent on every add and delete.

## Build and Run

Requires Java 8 or newer. From the project root:

```bash
# Compile
javac -d bin src/*.java

# Run the benchmark
java -cp bin Benchmark
```

## Project Structure

```
src/
├── CircularBuffer.java   # Generic array-backed circular buffer
├── Document.java         # Immutable document with ACTIVE/DELETED status
├── DocumentStore.java    # Multi-index store and search logic
└── Benchmark.java        # Performance benchmark
```

## Tech

Java
