package qengine.program;

import fr.boreal.model.logicalElements.api.Substitution;
import qengine.model.RDFTriple;
import qengine.model.StarQuery;
import qengine.parser.RDFTriplesParser;
import org.eclipse.rdf4j.rio.RDFFormat;
import qengine.parser.StarQuerySparQLParser;
import qengine.storage.ColleagueWrapper;
import qengine.storage.InteGraalWrapper;
import qengine.storage.RDFHexaStore;
import qengine.storage.RDFStorage;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * System Comparison Benchmark
 *
 * Compares three RDF query engine implementations:
 * 1. Our Prototype (RDFHexaStore)
 * 2. Colleague Implementation (RDFHexaStore)
 * 3. InteGraal (reference implementation)
 *
 * Methodology:
 * - Uses identical dataset (data_500K.nt)
 * - Uses identical measurement protocol (50 warmup + 30 measurement iterations)
 * - Uses identical JVM configuration (4GB heap)
 * - Ensures fair comparison by measuring only query execution time
 */
public class SystemComparison {

    private static final String BASE_DIR = "watdiv-mini-projet-partie-2/testsuite/";
    private static final String DATASET_DIR = BASE_DIR + "dataset/";
    private static final String QUERIES_DIR = BASE_DIR + "queries/100_deduplicated/";
    private static final String RESULTS_DIR = "results/phase6/";

    private static final int WARMUP_ITERATIONS = 50;
    private static final int MEASUREMENT_ITERATIONS = 30;

    // Query definitions from Experiment B
    private static final List<QueryDef> QUERY_SET = Arrays.asList(
        // High selectivity queries (Q_1_*)
        new QueryDef("Q_1_eligibleregion", 43),
        new QueryDef("Q_1_eligibleregion", 52),
        new QueryDef("Q_1_includes", 10),
        new QueryDef("Q_1_subscribes", 42),
        new QueryDef("Q_1_nationality", 0),
        new QueryDef("Q_1_nationality", 4),
        new QueryDef("Q_1_nationality", 22),
        new QueryDef("Q_1_nationality", 40),
        new QueryDef("Q_1_likes", 0),
        new QueryDef("Q_1_likes", 5),

        // Medium selectivity queries (Q_2_*)
        new QueryDef("Q_2_includes_eligibleRegion", 18),
        new QueryDef("Q_2_includes_eligibleRegion", 38),
        new QueryDef("Q_2_includes_eligibleRegion", 62),
        new QueryDef("Q_2_includes_eligibleRegion", 68),
        new QueryDef("Q_2_includes_eligibleRegion", 74),
        new QueryDef("Q_2_includes_eligibleRegion", 92),
        new QueryDef("Q_2_likes_nationality", 6),
        new QueryDef("Q_2_likes_nationality", 17),
        new QueryDef("Q_2_likes_nationality", 52),
        new QueryDef("Q_2_subscribes_likes", 4),
        new QueryDef("Q_2_subscribes_likes", 39),
        new QueryDef("Q_2_subscribes_likes", 61),
        new QueryDef("Q_2_tag_homepage", 44),

        // Low selectivity queries (Q_3_*)
        new QueryDef("Q_3_location_nationality_gender", 6),
        new QueryDef("Q_3_location_nationality_gender", 33),
        new QueryDef("Q_3_location_nationality_gender", 34),
        new QueryDef("Q_3_location_nationality_gender", 43),
        new QueryDef("Q_3_location_nationality_gender", 91),
        new QueryDef("Q_3_location_nationality_gender", 92),
        new QueryDef("Q_3_location_gender_type", 4),
        new QueryDef("Q_3_location_gender_type", 7),
        new QueryDef("Q_3_nationality_gender_type", 4),

        // Very low selectivity queries (Q_4_*)
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

        @Override
        public String toString() {
            return template + "_" + index;
        }
    }

    static class BenchmarkResult {
        String systemName;
        String template;
        int queryIndex;
        double meanMs;
        double medianMs;
        double stdDevMs;
        double p95Ms;
        long resultCount;

        String toCsv() {
            return String.format("%s,%s,%d,%.6f,%.6f,%.6f,%.6f,%d",
                systemName, template, queryIndex, meanMs, medianMs, stdDevMs, p95Ms, resultCount);
        }
    }

    public static void main(String[] args) throws IOException {
        System.out.println("=== System Comparison Benchmark ===\n");

        // Create results directory
        Files.createDirectories(Paths.get(RESULTS_DIR));

        // Load dataset path
        String datasetPath = DATASET_DIR + "data_500K.nt";
        System.out.println("Dataset: " + datasetPath);
        System.out.println("Queries: " + QUERY_SET.size() + " queries");
        System.out.println("Warmup: " + WARMUP_ITERATIONS + " iterations");
        System.out.println("Measurement: " + MEASUREMENT_ITERATIONS + " iterations\n");

        // Benchmark each system
        List<BenchmarkResult> allResults = new ArrayList<>();

        System.out.println("--- System 1: Our Prototype (RDFHexaStore) ---");
        allResults.addAll(benchmarkSystem("OurPrototype", datasetPath,
                RDFHexaStore::new));

        System.out.println("\n--- System 2: Colleague Implementation (RDFHexaStore) ---");
        allResults.addAll(benchmarkSystem("Colleague", datasetPath,
                ColleagueWrapper::new));

        System.out.println("\n--- System 3: InteGraal (Reference) ---");
        allResults.addAll(benchmarkSystem("InteGraal", datasetPath,
                InteGraalWrapper::new));

        // Export results
        exportResults(allResults);

        System.out.println("\n=== Benchmark Complete ===");
        System.out.println("Results saved to: " + RESULTS_DIR);
    }

    private static List<BenchmarkResult> benchmarkSystem(
            String systemName,
            String datasetPath,
            StorageFactory factory) throws IOException {

        System.out.println("Loading dataset...");
        RDFStorage store = factory.create();
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
        System.out.println("Loaded " + triplesLoaded + " triples (store size: " + store.size() + ")");

        // Run benchmarks
        List<BenchmarkResult> results = new ArrayList<>();
        int queryNum = 0;

        for (QueryDef queryDef : QUERY_SET) {
            queryNum++;
            System.out.printf("\rQuery %d/%d: %s[%d]... ",
                queryNum, QUERY_SET.size(), queryDef.template, queryDef.index);

            try {
                // Load query
                StarQuery query = loadQuery(queryDef);

                // Warmup phase
                for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                    Iterator<Substitution> iter = store.match(query);
                    while (iter.hasNext()) iter.next();
                }

                // Measurement phase
                List<Double> times = new ArrayList<>();
                long resultCount = 0;

                for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
                    long start = System.nanoTime();
                    Iterator<Substitution> iter = store.match(query);
                    long count = 0;
                    while (iter.hasNext()) {
                        iter.next();
                        count++;
                    }
                    long elapsed = System.nanoTime() - start;
                    times.add(elapsed / 1_000_000.0);  // Convert to ms
                    if (i == 0) resultCount = count;
                }

                // Compute statistics
                BenchmarkResult result = new BenchmarkResult();
                result.systemName = systemName;
                result.template = queryDef.template;
                result.queryIndex = queryDef.index;
                result.meanMs = computeMean(times);
                result.medianMs = computeMedian(times);
                result.stdDevMs = computeStdDev(times);
                result.p95Ms = computePercentile(times, 0.95);
                result.resultCount = resultCount;

                results.add(result);

                System.out.printf("%.2f ms (%.0f results)", result.meanMs, (double)resultCount);

            } catch (Exception e) {
                System.out.println("ERROR: " + e.getMessage());
                e.printStackTrace();
            }
        }

        System.out.println("\n" + systemName + " complete: " + results.size() + " queries benchmarked");
        return results;
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

    private static void exportResults(List<BenchmarkResult> results) throws IOException {
        // Export all results
        String csvPath = RESULTS_DIR + "system_comparison_results.csv";
        try (PrintWriter writer = new PrintWriter(new FileWriter(csvPath))) {
            writer.println("system,template,query_index,mean_ms,median_ms,stddev_ms,p95_ms,result_count");
            for (BenchmarkResult result : results) {
                writer.println(result.toCsv());
            }
        }
        System.out.println("\nResults exported to: " + csvPath);

        // Export summary statistics per system
        Map<String, List<BenchmarkResult>> bySystem = results.stream()
            .collect(Collectors.groupingBy(r -> r.systemName));

        String summaryPath = RESULTS_DIR + "system_comparison_summary.csv";
        try (PrintWriter writer = new PrintWriter(new FileWriter(summaryPath))) {
            writer.println("system,query_count,mean_time_ms,median_time_ms,stddev_ms,p95_ms,throughput_qps");

            for (Map.Entry<String, List<BenchmarkResult>> entry : bySystem.entrySet()) {
                String systemName = entry.getKey();
                List<BenchmarkResult> systemResults = entry.getValue();

                List<Double> means = systemResults.stream()
                    .map(r -> r.meanMs)
                    .collect(Collectors.toList());

                double overallMean = computeMean(means);
                double overallMedian = computeMedian(means);
                double overallStdDev = computeStdDev(means);
                double overallP95 = computePercentile(means, 0.95);
                double throughput = 1000.0 / overallMean;  // queries per second

                writer.println(String.format("%s,%d,%.4f,%.4f,%.4f,%.4f,%.4f",
                    systemName, systemResults.size(), overallMean, overallMedian,
                    overallStdDev, overallP95, throughput));
            }
        }
        System.out.println("Summary exported to: " + summaryPath);
    }

    private static double computeMean(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private static double computeMedian(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int size = sorted.size();
        if (size % 2 == 0) {
            return (sorted.get(size/2 - 1) + sorted.get(size/2)) / 2.0;
        } else {
            return sorted.get(size/2);
        }
    }

    private static double computeStdDev(List<Double> values) {
        double mean = computeMean(values);
        double variance = values.stream()
            .mapToDouble(v -> Math.pow(v - mean, 2))
            .average()
            .orElse(0.0);
        return Math.sqrt(variance);
    }

    private static double computePercentile(List<Double> values, double percentile) {
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index);
    }

    @FunctionalInterface
    interface StorageFactory {
        RDFStorage create();
    }
}
