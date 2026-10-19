import java.util.*;

/**
 * PROJECT 3: Fast Lookup & In-Memory Search Engine (Hashing)
 *
 * Maintains circular buffer storage, a TreeMap for ordered term queries,
 * hash maps for O(1) doc id lookup and inverted term postings.
 */
public class DocumentStore {

    private final CircularBuffer<Document> log;
    private final TreeMap<String, Set<Document>> termIndex;
    private final HashMap<String, Document> docIdIndex;
    private final HashMap<String, HashSet<Document>> invertedIndex;
    private int activeCount;

    /** Timestamp ascending, then docId ascending (ingestion tie-break). */
    private static final Comparator<Document> DOC_ORDER =
            Comparator.comparingLong(Document::getTimestamp).thenComparing(Document::getDocId);

    public DocumentStore() {
        this.log = new CircularBuffer<>();
        this.termIndex = new TreeMap<>();
        this.docIdIndex = new HashMap<>();
        this.invertedIndex = new HashMap<>();
        this.activeCount = 0;
    }

    private List<String> tokenize(String text) {
        if (text == null) {
            return Collections.emptyList();
        }

        String normalized = text.toLowerCase(Locale.ROOT);
        String[] parts = normalized.split("[^a-z0-9]+");
        ArrayList<String> terms = new ArrayList<>(parts.length);
        for (String p : parts) {
            if (!p.isEmpty()) {
                terms.add(p);
            }
        }
        return terms;
    }

    /**
     * Normalizes a single query string to one index term (same rule as {@link #searchTerm}).
     */
    private String normalizeQueryTerm(String term) {
        if (term == null) {
            return null;
        }
        List<String> tokens = tokenize(term);
        if (tokens.isEmpty()) {
            return null;
        }
        return tokens.get(0);
    }

    public void addDocument(Document document) {
        if (document == null) {
            throw new IllegalArgumentException("document cannot be null");
        }
        if (docIdIndex.containsKey(document.getDocId())) {
            throw new IllegalArgumentException("duplicate docId: " + document.getDocId());
        }

        log.addLast(document);

        HashSet<String> uniqueTerms = new HashSet<>(tokenize(document.getText()));
        for (String t : uniqueTerms) {
            Set<Document> postings = termIndex.computeIfAbsent(t, k -> new TreeSet<>(DOC_ORDER));
            postings.add(document);

            HashSet<Document> inv = invertedIndex.computeIfAbsent(t, k -> new HashSet<>());
            inv.add(document);
        }

        docIdIndex.put(document.getDocId(), document);
        activeCount++;
    }

    public boolean containsDocument(String docId) {
        if (docId == null) {
            return false;
        }
        Document d = docIdIndex.get(docId);
        return d != null && d.isActive();
    }

    public int size() {
        return activeCount;
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public List<Document> searchTerm(String term) {
        String normalized = normalizeQueryTerm(term);
        if (normalized == null) {
            return Collections.emptyList();
        }

        HashSet<Document> docs = invertedIndex.get(normalized);
        if (docs == null || docs.isEmpty()) {
            return Collections.emptyList();
        }

        ArrayList<Document> results = new ArrayList<>(docs.size());
        for (Document d : docs) {
            if (d != null && d.isActive()) {
                results.add(d);
            }
        }
        results.sort(DOC_ORDER);
        return results;
    }

    /**
     * Multi-term AND search. Intersects posting lists smallest-first to minimize
     * {@code retainAll} work on the smallest running candidate set.
     */
    public List<Document> searchAllTerms(List<String> terms) {
        if (terms == null || terms.isEmpty()) {
            return Collections.emptyList();
        }

        ArrayList<String> normalized = new ArrayList<>(terms.size());
        for (String raw : terms) {
            String nt = normalizeQueryTerm(raw);
            if (nt == null) {
                return Collections.emptyList();
            }
            normalized.add(nt);
        }

        if (normalized.isEmpty()) {
            return Collections.emptyList();
        }

        ArrayList<HashSet<Document>> sets = new ArrayList<>(normalized.size());
        for (String nt : normalized) {
            HashSet<Document> s = invertedIndex.get(nt);
            if (s == null || s.isEmpty()) {
                return Collections.emptyList();
            }
            sets.add(s);
        }

        sets.sort(Comparator.comparingInt(HashSet::size));

        HashSet<Document> candidate = new HashSet<>(sets.get(0));
        for (int i = 1; i < sets.size(); i++) {
            candidate.retainAll(sets.get(i));
            if (candidate.isEmpty()) {
                return Collections.emptyList();
            }
        }

        ArrayList<Document> results = new ArrayList<>(candidate.size());
        for (Document d : candidate) {
            if (d != null && d.isActive()) {
                results.add(d);
            }
        }
        results.sort(DOC_ORDER);
        return results;
    }

    public List<String> prefixSearch(String prefix) {
        String p = (prefix == null) ? "" : prefix.toLowerCase(Locale.ROOT);
        String fromKey = p;
        String toKey = p + Character.MAX_VALUE;
        NavigableMap<String, Set<Document>> sub = termIndex.subMap(fromKey, true, toKey, true);
        return new ArrayList<>(sub.navigableKeySet());
    }

    public List<String> rangeTerms(String from, String to) {
        if (from == null || to == null) {
            return Collections.emptyList();
        }

        String f = from.toLowerCase(Locale.ROOT);
        String t = to.toLowerCase(Locale.ROOT);
        if (f.compareTo(t) > 0) {
            return Collections.emptyList();
        }

        NavigableMap<String, Set<Document>> sub = termIndex.subMap(f, true, t, true);
        return new ArrayList<>(sub.navigableKeySet());
    }

    public boolean deleteDocument(String docId) {
        if (docId == null) {
            return false;
        }

        Document doc = docIdIndex.get(docId);
        if (doc == null || !doc.isActive()) {
            return false;
        }

        HashSet<String> uniqueTerms = new HashSet<>(tokenize(doc.getText()));
        for (String t : uniqueTerms) {
            Set<Document> treePostings = termIndex.get(t);
            if (treePostings != null) {
                treePostings.remove(doc);
                if (treePostings.isEmpty()) {
                    termIndex.remove(t);
                }
            }

            HashSet<Document> inv = invertedIndex.get(t);
            if (inv != null) {
                inv.remove(doc);
                if (inv.isEmpty()) {
                    invertedIndex.remove(t);
                }
            }
        }

        doc.markAsDeleted();
        activeCount--;
        return true;
    }

    public Iterator<Document> iterateLiveDocuments() {
        ArrayList<Document> live = new ArrayList<>(Math.max(16, activeCount));
        CircularBuffer<Document>.CircularBufferIterator it = log.iterator();
        while (it.hasNext()) {
            Document d = it.next();
            if (d != null && d.isActive()) {
                live.add(d);
            }
        }
        return live.iterator();
    }

    @Override
    public String toString() {
        return "DocumentStore{activeDocuments=" + size() + "}";
    }
}
