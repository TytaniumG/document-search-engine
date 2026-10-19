import java.util.List;
import java.util.ArrayList;
import java.util.Random;

/**
 * Benchmark for measuring DocumentStore performance in Project 3.
 */
public class Benchmark {

    private static Document generateDocument(int id) {
        StringBuilder text = new StringBuilder();

        text.append("the document contains information about ");

        text.append("topic").append(id % 10).append(" ");
        text.append("category").append(id % 20).append(" ");

        text.append("keyword").append(id % 100).append(" ");

        text.append("unique").append(id).append(" ");

        text.append("with relevant data and metadata");

        return new Document("doc-" + id, text.toString());
    }

    /**
     * Optional tree-based benchmark: walks termIndex via prefix and scans postings.
     */
    public static double benchmarkTreeSearch(DocumentStore store, int numSearches) {
        return 0.0;
    }

    public static double benchmarkHashSearch(DocumentStore store, int numSearchesPerTerm) {
        List<String> allTerms = store.prefixSearch("");
        if (allTerms.isEmpty() || numSearchesPerTerm <= 0) {
            return 0.0;
        }

        Random rnd = new Random(42);
        int warm = Math.min(100, allTerms.size());
        for (int i = 0; i < warm; i++) {
            String t = allTerms.get(rnd.nextInt(allTerms.size()));
            List<Document> r = store.searchTerm(t);
            if (r != null) {
                r.size();
            }
        }

        long totalSearches = 0L;
        long start = System.nanoTime();
        for (String term : allTerms) {
            for (int j = 0; j < numSearchesPerTerm; j++) {
                List<Document> results = store.searchTerm(term);
                totalSearches++;
                if (results != null) {
                    results.size();
                }
            }
        }
        long elapsed = System.nanoTime() - start;

        if (totalSearches == 0) {
            return 0.0;
        }
        return (elapsed / (double) totalSearches) / 1000.0;
    }

    public static double benchmarkMultiTermSearch(DocumentStore store, int numTerms, int numSearches) {
        List<String> allTerms = store.prefixSearch("");
        if (allTerms.size() < numTerms || numSearches <= 0 || numTerms <= 0) {
            return 0.0;
        }

        Random rnd = new Random(12345);
        ArrayList<List<String>> queries = new ArrayList<>(numSearches);
        for (int q = 0; q < numSearches; q++) {
            ArrayList<String> qt = new ArrayList<>(numTerms);
            for (int t = 0; t < numTerms; t++) {
                qt.add(allTerms.get(rnd.nextInt(allTerms.size())));
            }
            queries.add(qt);
        }

        int warm = Math.min(50, queries.size());
        for (int i = 0; i < warm; i++) {
            List<Document> r = store.searchAllTerms(queries.get(i));
            if (r != null) {
                r.size();
            }
        }

        long start = System.nanoTime();
        for (int i = 0; i < numSearches; i++) {
            List<Document> results = store.searchAllTerms(queries.get(i));
            if (results != null) {
                results.size();
            }
        }
        long elapsed = System.nanoTime() - start;

        return (elapsed / (double) numSearches) / 1000.0;
    }

    public static double benchmarkIntersectionStrategy(DocumentStore store, int numSearches) {
        return 0.0;
    }

    public static void main(String[] args) {
        System.out.println("=== Project 3: Search Performance Benchmark ===\n");

        int[] sizes = {10_000, 100_000};

        for (int n : sizes) {
            System.out.println("Dataset size: " + n + " documents");
            System.out.println("=".repeat(50));

            DocumentStore store = new DocumentStore();

            System.out.print("Building index... ");
            long buildStart = System.nanoTime();

            for (int i = 0; i < n; i++) {
                store.addDocument(generateDocument(i));
            }

            long buildTime = (System.nanoTime() - buildStart) / 1_000_000;
            System.out.println(buildTime + " ms");

            System.out.println("  Active documents: " + store.size());
            List<String> allTerms = store.prefixSearch("");
            System.out.println("  Unique terms in index: " + allTerms.size());
            System.out.println();

            System.out.println("SEARCH PERFORMANCE:");

            double avgHashSearch = benchmarkHashSearch(store, 20);
            System.out.println("  Average term search time (hash): "
                    + String.format("%.2f", avgHashSearch) + " μs");

            System.out.println("\nMulti-term search:");
            for (int numTerms : new int[]{2, 3, 5}) {
                if (allTerms.size() >= numTerms) {
                    double avgTime = benchmarkMultiTermSearch(store, numTerms, 1000);
                    System.out.println("  " + numTerms + " terms: "
                            + String.format("%.2f", avgTime) + " μs");
                }
            }

            System.out.println();
            System.out.println("=".repeat(50));
            System.out.println();
        }

        System.out.println("=== Benchmark Complete ===\n");
    }
}
