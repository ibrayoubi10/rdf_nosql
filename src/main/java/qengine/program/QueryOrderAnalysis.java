package qengine.program;

import fr.boreal.model.logicalElements.api.Substitution;
import qengine.model.RDFTriple;
import qengine.model.StarQuery;
import qengine.parser.RDFTriplesParser;
import org.eclipse.rdf4j.rio.RDFFormat;
import qengine.parser.StarQuerySparQLParser;
import qengine.storage.RDFHexaStore;
import qengine.storage.RDFStorage;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Query Execution Order Analysis
 *
 * Tests whether the order in which queries are executed affects measured performance.
 *
 * Simplified configuration:
 * - 500K dataset
 * - 25 queries
 * - 2 orderings: original + shuffled
 * - 10 repetitions per ordering
 * - Statistical test: paired t-test
 */
public class QueryOrderAnalysis {

    private static final String BASE_DIR = "watdiv-mini-projet-partie-2/testsuite/";
    private static final String DATASET_DIR = BASE_DIR + "dataset/";
    private static final String QUERIES_DIR = BASE_DIR + "queries/100_deduplicated/";
    private static final String RESULTS_DIR = "results/phase7/";

    private static final int NUM_REPETITIONS = 10;
    private static final int WARMUP_ITERATIONS = 20;

    // Selected 25 queries
    private static final List<QueryDef> QUERY_SET = Arrays.asList(
        // High selectivity (Q_1_*)
        new QueryDef("Q_1_eligibleregion", 43),
        new QueryDef("Q_1_eligibleregion", 52),
        new QueryDef("Q_1_includes", 10),
        new QueryDef("Q_1_subscribes", 42),
        new QueryDef("Q_1_nationality", 0),
        new QueryDef("Q_1_nationality", 4),
        new QueryDef("Q_1_nationality", 22),
        new QueryDef("Q_1_likes", 0),
        new QueryDef("Q_1_nationality", 40),

        // Medium selectivity (Q_2_*)
        new QueryDef("Q_2_includes_eligibleRegion", 18),
        new QueryDef("Q_2_includes_eligibleRegion", 62),
        new QueryDef("Q_2_includes_eligibleRegion", 68),
        new QueryDef("Q_2_likes_nationality", 6),
        new QueryDef("Q_2_likes_nationality", 17),
        new QueryDef("Q_2_subscribes_likes", 4),
        new QueryDef("Q_2_tag_homepage", 44),

        // Low selectivity (Q_3_*)
        new QueryDef("Q_3_location_nationality_gender", 6),
        new QueryDef("Q_3_location_nationality_gender", 33),
        new QueryDef("Q_3_location_nationality_gender", 34),
        new QueryDef("Q_3_location_nationality_gender", 91),
        new QueryDef("Q_3_location_gender_type", 4),
        new QueryDef("Q_3_location_gender_type", 7),
        new QueryDef("Q_3_nationality_gender_type", 4),

        // Very low selectivity (Q_4_*)
        new QueryDef("Q_4_location_nationality_gender_type", 3),
        new QueryDef("Q_4_location_nationality_gender_type", 45)
    );

    static class QueryDef {
        String template;
        int index;

        QueryDef(String template, int index) {
            this.template = template;
            this.index = index;
        }

        String getPath() {
            return QUERIES_DIR + template + ".queryset";
        }

        String getId() {
            return template + "_" + index;
        }

        @Override
        public String toString() {
            return getId();
        }
    }

    static class WorkloadResult {
        String ordering;
        int repetition;
        List<QueryResult> queryResults;
        double totalTimeMs;

        WorkloadResult(String ordering, int repetition) {
            this.ordering = ordering;
            this.repetition = repetition;
            this.queryResults = new ArrayList<>();
            this.totalTimeMs = 0.0;
        }

        void addQueryResult(QueryDef query, int position, double timeMs, long resultCount) {
            queryResults.add(new QueryResult(query.getId(), position, timeMs, resultCount));
            totalTimeMs += timeMs;
        }
    }

    static class QueryResult {
        String queryId;
        int position;
        double timeMs;
        long resultCount;

        QueryResult(String queryId, int position, double timeMs, long resultCount) {
            this.queryId = queryId;
            this.position = position;
            this.timeMs = timeMs;
            this.resultCount = resultCount;
        }
    }

    public static void main(String[] args) throws IOException {
        System.out.println("=== Query Order Analysis ===\n");

        // Create results directory
        Files.createDirectories(Paths.get(RESULTS_DIR));

        // Dataset
        String datasetPath = DATASET_DIR + "data_500K.nt";
        System.out.println("Dataset: " + datasetPath);
        System.out.println("Queries: " + QUERY_SET.size() + " queries");
        System.out.println("Repetitions: " + NUM_REPETITIONS + " per ordering");
        System.out.println("Orderings: Original + Shuffled");
        System.out.println();

        // Create orderings
        List<QueryDef> originalOrder = new ArrayList<>(QUERY_SET);
        List<QueryDef> shuffledOrder = new ArrayList<>(QUERY_SET);
        Collections.shuffle(shuffledOrder, new Random(42)); // Fixed seed for reproducibility

        System.out.println("Original order (first 5): " +
            originalOrder.stream().limit(5).map(QueryDef::getId).collect(Collectors.joining(", ")));
        System.out.println("Shuffled order (first 5): " +
            shuffledOrder.stream().limit(5).map(QueryDef::getId).collect(Collectors.joining(", ")));
        System.out.println();

        // Load dataset once
        System.out.println("Loading dataset...");
        RDFStorage store = new RDFHexaStore();
        FileReader rdfFile = new FileReader(datasetPath);
        int triplesLoaded = 0;
        try (RDFTriplesParser parser = new RDFTriplesParser(rdfFile, RDFFormat.NTRIPLES)) {
            while (parser.hasNext()) {
                RDFTriple triple = parser.next();
                if (store.add(triple)) {
                    triplesLoaded++;
                }
            }
        }
        System.out.println("Loaded " + triplesLoaded + " triples\n");

        // Load all queries
        Map<String, StarQuery> queries = loadAllQueries();
        System.out.println("Loaded " + queries.size() + " unique queries\n");

        // Warmup
        System.out.println("Warming up system (" + WARMUP_ITERATIONS + " iterations)...");
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            for (QueryDef queryDef : originalOrder) {
                StarQuery query = queries.get(queryDef.getId());
                Iterator<Substitution> iter = store.match(query);
                while (iter.hasNext()) iter.next();
            }
        }
        System.out.println("Warmup complete\n");

        // Run experiments
        List<WorkloadResult> allResults = new ArrayList<>();

        // Original ordering
        System.out.println("=== ORIGINAL ORDERING ===");
        for (int rep = 1; rep <= NUM_REPETITIONS; rep++) {
            System.out.print("Repetition " + rep + "/" + NUM_REPETITIONS + "... ");
            WorkloadResult result = executeWorkload("original", rep, originalOrder, queries, store);
            allResults.add(result);
            System.out.printf("%.2f ms total%n", result.totalTimeMs);
        }

        System.out.println();

        // Shuffled ordering
        System.out.println("=== SHUFFLED ORDERING ===");
        for (int rep = 1; rep <= NUM_REPETITIONS; rep++) {
            System.out.print("Repetition " + rep + "/" + NUM_REPETITIONS + "... ");
            WorkloadResult result = executeWorkload("shuffled", rep, shuffledOrder, queries, store);
            allResults.add(result);
            System.out.printf("%.2f ms total%n", result.totalTimeMs);
        }

        System.out.println();

        // Export results
        exportResults(allResults);

        System.out.println("\n=== Experiment Complete ===");
        System.out.println("Results saved to: " + RESULTS_DIR);
    }

    private static Map<String, StarQuery> loadAllQueries() throws IOException {
        Map<String, StarQuery> queries = new HashMap<>();

        for (QueryDef queryDef : QUERY_SET) {
            String queryId = queryDef.getId();
            if (!queries.containsKey(queryId)) {
                StarQuery query = loadQuery(queryDef);
                queries.put(queryId, query);
            }
        }

        return queries;
    }

    private static StarQuery loadQuery(QueryDef queryDef) throws IOException {
        try (StarQuerySparQLParser parser = new StarQuerySparQLParser(queryDef.getPath())) {
            int currentIndex = 0;
            while (parser.hasNext()) {
                StarQuery query = (StarQuery) parser.next();
                if (currentIndex == queryDef.index) {
                    return query;
                }
                currentIndex++;
            }
        }
        throw new IOException("Query index " + queryDef.index + " not found in " + queryDef.template);
    }

    private static WorkloadResult executeWorkload(
            String ordering,
            int repetition,
            List<QueryDef> queryOrder,
            Map<String, StarQuery> queries,
            RDFStorage store) {

        WorkloadResult result = new WorkloadResult(ordering, repetition);

        for (int position = 0; position < queryOrder.size(); position++) {
            QueryDef queryDef = queryOrder.get(position);
            StarQuery query = queries.get(queryDef.getId());

            // Measure query execution
            long start = System.nanoTime();
            Iterator<Substitution> iter = store.match(query);
            long count = 0;
            while (iter.hasNext()) {
                iter.next();
                count++;
            }
            long elapsed = System.nanoTime() - start;
            double timeMs = elapsed / 1_000_000.0;

            result.addQueryResult(queryDef, position + 1, timeMs, count);
        }

        return result;
    }

    private static void exportResults(List<WorkloadResult> results) throws IOException {
        // Detailed results
        String detailedPath = RESULTS_DIR + "order_experiment_detailed.csv";
        try (PrintWriter writer = new PrintWriter(new FileWriter(detailedPath))) {
            writer.println("ordering,repetition,query_id,position,time_ms,result_count");

            for (WorkloadResult workload : results) {
                for (QueryResult query : workload.queryResults) {
                    writer.println(String.format("%s,%d,%s,%d,%.6f,%d",
                        workload.ordering,
                        workload.repetition,
                        query.queryId,
                        query.position,
                        query.timeMs,
                        query.resultCount));
                }
            }
        }
        System.out.println("\nDetailed results: " + detailedPath);

        // Workload totals
        String totalsPath = RESULTS_DIR + "order_experiment_totals.csv";
        try (PrintWriter writer = new PrintWriter(new FileWriter(totalsPath))) {
            writer.println("ordering,repetition,total_time_ms");

            for (WorkloadResult workload : results) {
                writer.println(String.format("%s,%d,%.6f",
                    workload.ordering,
                    workload.repetition,
                    workload.totalTimeMs));
            }
        }
        System.out.println("Workload totals: " + totalsPath);

        // Summary statistics
        Map<String, List<Double>> totalsByOrdering = results.stream()
            .collect(Collectors.groupingBy(
                r -> r.ordering,
                Collectors.mapping(r -> r.totalTimeMs, Collectors.toList())
            ));

        String summaryPath = RESULTS_DIR + "order_experiment_summary.txt";
        try (PrintWriter writer = new PrintWriter(new FileWriter(summaryPath))) {
            writer.println("Query Order Analysis Summary");
            writer.println("=====================================\n");

            for (Map.Entry<String, List<Double>> entry : totalsByOrdering.entrySet()) {
                String ordering = entry.getKey();
                List<Double> times = entry.getValue();

                double mean = times.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                double stddev = Math.sqrt(times.stream()
                    .mapToDouble(t -> Math.pow(t - mean, 2))
                    .average().orElse(0.0));

                writer.println(ordering.toUpperCase() + " ORDERING:");
                writer.println(String.format("  Mean total time: %.2f ms", mean));
                writer.println(String.format("  Std deviation: %.2f ms", stddev));
                writer.println(String.format("  Repetitions: %d", times.size()));
                writer.println();
            }
        }
        System.out.println("Summary: " + summaryPath);
    }
}
