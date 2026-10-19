import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Student-written tests for Project 3.
 */
public class StudentTests {

    private DocumentStore store;

    @Before
    public void setUp() {
        store = new DocumentStore();
    }

    /**
     * Strategy: intersect posting lists in ascending order of set size (smallest first).
     * This minimizes the size of the working set before each retainAll, so fewer
     * comparisons when a rare term has few documents and common terms have many.
     * <p>
     * Scenario: "rare" appears once; "common" and "frequent" appear in many docs.
     * Only doc1 has all three. Reordering query terms must not change the result set.
     */
    @Test
    public void testSearchAllTermsStrategy() {
        store.addDocument(new Document("doc1", "rare common frequent"));
        store.addDocument(new Document("doc2", "common frequent"));
        store.addDocument(new Document("doc3", "common frequent"));
        store.addDocument(new Document("doc4", "common frequent"));

        List<String> terms = Arrays.asList("rare", "common", "frequent");
        List<Document> results = store.searchAllTerms(terms);
        assertEquals(1, results.size());
        assertEquals("doc1", results.get(0).getDocId());

        List<String> reordered = Arrays.asList("frequent", "rare", "common");
        List<Document> results2 = store.searchAllTerms(reordered);
        assertEquals("Order of terms in query should not change results",
                results.size(), results2.size());
        assertEquals("doc1", results2.get(0).getDocId());
    }

    /**
     * Scale: many documents make O(1) {@code containsDocument} essential vs scanning the log.
     * Verifies docId index stays consistent after adds. (Edge case: middle and last ids.)
     */
    @Test
    public void testHashPerformanceOrEdgeCase() {
        final int n = 5000;
        for (int i = 0; i < n; i++) {
            store.addDocument(new Document("id-" + i, "token shared " + i));
        }
        assertTrue(store.containsDocument("id-0"));
        assertTrue(store.containsDocument("id-2500"));
        assertTrue(store.containsDocument("id-" + (n - 1)));
        assertFalse(store.containsDocument("id-missing"));
        assertEquals(n, store.size());
    }
}
