package qengine.program;

import fr.boreal.model.logicalElements.api.Substitution;
import org.eclipse.rdf4j.rio.RDFFormat;
import qengine.model.RDFTriple;
import qengine.model.StarQuery;
import qengine.parser.RDFTriplesParser;
import qengine.parser.StarQuerySparQLParser;
import qengine.storage.RDFHexaStore;
import qengine.storage.RDFStorage;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Performance Measurement with Custom Timing Harness
 *
 * This program implements rigorous performance benchmarking:
 * 1. Proper warmup phase (50 executions)
 * 2. Multiple repetitions (30 per query)
 * 3. Statistical aggregation (mean, median, std dev, percentiles)
 * 4. Result consumption (prevent dead code elimination)
 * 5. Measurement isolation (one query at a time)
 *
 * Measures WARM performance (JIT-optimized, steady-state)
 */
public class BenchmarkRunner {

    private static final String DATASET_DIR = "watdiv-mini-projet-partie-2/testsuite/dataset/";
    private static final String QUERIES_DIR = "watdiv-mini-projet-partie-2/testsuite/queries/100_deduplicated/";
    private static final String OUTPUT_DIR = "results/phase4/";

    // Benchmarking configuration
    private static final int WARMUP_ITERATIONS = 50;
    private static final int MEASUREMENT_REPETITIONS = 30;
    private static final boolean VERBOSE = false;          // Set true for detailed output

    public static void main(String[] args) {
        try {
            Files.createDirectories(Paths.get(OUTPUT_DIR));

            System.out.println("=" .repeat(60));
            System.out.println("PERFORMANCE MEASUREMENT (CUSTOM TIMING HARNESS)");
            System.out.println("=" .repeat(60));
            System.out.println();

            // Run baseline benchmarks on our prototype
            System.out.println("Configuration:");
            System.out.println("  Warmup iterations: " + WARMUP_ITERATIONS);
            System.out.println("  Measurement repetitions: " + MEASUREMENT_REPETITIONS);
            System.out.println("  Dataset: 500K (for baseline validation)");
            System.out.println();

            // Benchmark on 500K dataset
            System.out.println("Starting baseline benchmark on 500K dataset...");
            BenchmarkReport report = runBenchmark(
                DATASET_DIR + "data_500K.nt",
                "500K",
                QUERIES_DIR
            );

            // Generate results
            generateDetailedCSV(report, "500K");
            generateSummaryCSV(report, "500K");
            printSummary(report);

            System.out.println("\\n" + "=".repeat(60));
            System.out.println("BASELINE COMPLETED");
            System.out.println("=" .repeat(60));

        } catch (Exception e) {
            System.err.println("Error during benchmarking: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static BenchmarkReport runBenchmark(String datasetPath, String datasetName, String queryDir) throws IOException {
        BenchmarkReport report = new BenchmarkReport(datasetName);

        // Step 1: Load dataset
        System.out.println("\\n--- Loading Dataset ---");
        long loadStart = System.currentTimeMillis();
        RDFStorage store = loadDataset(datasetPath);
        long loadTime = System.currentTimeMillis() - loadStart;
        System.out.println("Dataset loaded in " + loadTime + " ms");
        System.out.println("Dataset size: " + store.size() + " triples");
        report.dataLoadTimeMs = loadTime;
        report.tripleCount = store.size();

        // Step 2: Load queries
        System.out.println("\\n--- Loading Queries ---");
        List<QueryInfo> queries = loadAllQueries(queryDir);
        System.out.println("Loaded " + queries.size() + " queries");

        // Step 3: Global warmup phase
        System.out.println("\\n--- Global Warmup Phase ---");
        System.out.println("Executing " + WARMUP_ITERATIONS + " warmup iterations...");
        long warmupStart = System.currentTimeMillis();
        performGlobalWarmup(store, queries);
        long warmupTime = System.currentTimeMillis() - warmupStart;
        System.out.println("Warmup completed in " + warmupTime + " ms");
        report.warmupTimeMs = warmupTime;

        // Step 4: Measurement phase
        System.out.println("\\n--- Measurement Phase ---");
        System.out.println("Measuring " + queries.size() + " queries with " +
            MEASUREMENT_REPETITIONS + " repetitions each");

        long measurementStart = System.currentTimeMillis();
        for (int i = 0; i < queries.size(); i++) {
            QueryInfo qInfo = queries.get(i);

            if ((i + 1) % 50 == 0 || i == 0) {
                System.out.printf("[%d/%d] Measuring queries...%n", i + 1, queries.size());
            }

            QueryBenchmark benchmark = benchmarkQuery(store, qInfo);
            report.addBenchmark(benchmark);

            if (VERBOSE) {
                System.out.printf("  %s query %d: %.2f ms (±%.2f)%n",
                    qInfo.template, qInfo.queryIndex,
                    benchmark.meanMs, benchmark.stdDevMs);
            }
        }
        long measurementTime = System.currentTimeMillis() - measurementStart;
        report.measurementTimeMs = measurementTime;

        System.out.println("Measurement phase completed in " + measurementTime + " ms");

        return report;
    }

    /**
     * Perform global warmup to ensure JVM is fully optimized before measurements.
     * Executes all queries multiple times to trigger JIT compilation.
     */
    private static void performGlobalWarmup(RDFStorage store, List<QueryInfo> queries) {
        // Execute all queries multiple times to warm up the JVM
        for (int iteration = 0; iteration < WARMUP_ITERATIONS; iteration++) {
            for (QueryInfo qInfo : queries) {
                // Execute query and consume results (don't record time)
                Iterator<Substitution> results = store.match(qInfo.query);
                consumeResults(results);
            }
        }
    }

    /**
     * Benchmark a single query with proper methodology:
     * 1. Execute MEASUREMENT_REPETITIONS times
     * 2. Record all execution times
     * 3. Calculate statistics
     */
    private static QueryBenchmark benchmarkQuery(RDFStorage store, QueryInfo qInfo) {
        List<Double> executionTimes = new ArrayList<>();
        int resultCount = -1;

        for (int rep = 0; rep < MEASUREMENT_REPETITIONS; rep++) {
            long startNanos = System.nanoTime();

            // Execute query and consume ALL results
            Iterator<Substitution> results = store.match(qInfo.query);
            int count = consumeResults(results);

            long endNanos = System.nanoTime();
            double timeMs = (endNanos - startNanos) / 1_000_000.0;

            executionTimes.add(timeMs);

            // Store result count (should be same for all repetitions)
            if (resultCount == -1) {
                resultCount = count;
            }
        }

        // Calculate statistics
        return calculateStatistics(qInfo, executionTimes, resultCount);
    }

    /**
     * Consume all results from iterator to prevent dead code elimination.
     * This ensures the query is actually executed fully.
     */
    private static int consumeResults(Iterator<Substitution> results) {
        int count = 0;
        while (results.hasNext()) {
            Substitution sub = results.next();
            // Access the substitution to prevent optimization
            // Use volatile assignment pattern
            volatileConsumer = sub.toString().length();
            count++;
        }
        return count;
    }

    // Volatile variable to prevent dead code elimination
    private static volatile int volatileConsumer;

    /**
     * Calculate statistical measures from execution times.
     */
    private static QueryBenchmark calculateStatistics(QueryInfo qInfo, List<Double> times, int resultCount) {
        Collections.sort(times);

        double mean = times.stream().mapToDouble(d -> d).average().orElse(0);
        double median = times.get(times.size() / 2);

        // Calculate standard deviation
        double variance = times.stream()
            .mapToDouble(t -> Math.pow(t - mean, 2))
            .sum() / times.size();
        double stdDev = Math.sqrt(variance);

        // Calculate percentiles
        double p95 = times.get((int) (times.size() * 0.95));
        double p99 = times.get((int) (times.size() * 0.99));
        double min = times.getFirst();
        double max = times.getLast();

        // Calculate coefficient of variation (stability metric)
        double cv = stdDev / mean;

        return new QueryBenchmark(
            qInfo.template,
            qInfo.queryIndex,
            qInfo.query,
            resultCount,
            mean,
            median,
            stdDev,
            min,
            max,
            p95,
            p99,
            cv,
            times
        );
    }

    private static RDFStorage loadDataset(String datasetPath) throws IOException {
        RDFStorage store = new RDFHexaStore();
        FileReader rdfFile = new FileReader(datasetPath);

        try (RDFTriplesParser parser = new RDFTriplesParser(rdfFile, RDFFormat.NTRIPLES)) {
            while (parser.hasNext()) {
                RDFTriple triple = parser.next();
                store.add(triple);
            }
        }

        return store;
    }

    private static List<QueryInfo> loadAllQueries(String queryDir) throws IOException {
        List<QueryInfo> queries = new ArrayList<>();
        File dir = new File(queryDir);

        if (!dir.exists() || !dir.isDirectory()) {
            throw new IOException("Query directory not found: " + queryDir);
        }

        File[] queryFiles = dir.listFiles((d, name) -> name.endsWith(".queryset"));
        if (queryFiles == null || queryFiles.length == 0) {
            throw new IOException("No .queryset files found in: " + queryDir);
        }

        Arrays.sort(queryFiles);

        for (File queryFile : queryFiles) {
            String templateName = queryFile.getName().replace(".queryset", "");

            try (StarQuerySparQLParser parser = new StarQuerySparQLParser(queryFile.getAbsolutePath())) {
                int queryIndex = 0;
                while (parser.hasNext()) {
                    StarQuery query = (StarQuery) parser.next();
                    queries.add(new QueryInfo(templateName, queryIndex++, query));
                }
            }
        }

        return queries;
    }

    private static void generateDetailedCSV(BenchmarkReport report, String datasetName) throws IOException {
        String csvFile = OUTPUT_DIR + "benchmark_detailed_" + datasetName + ".csv";

        try (FileWriter writer = new FileWriter(csvFile)) {
            writer.write("template,query_index,result_count,mean_ms,median_ms,stddev_ms,min_ms,max_ms,p95_ms,p99_ms,cv,dataset\n");

            for (QueryBenchmark b : report.benchmarks) {
                writer.write(String.format("%s,%d,%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%s\n",
                    b.template,
                    b.queryIndex,
                    b.resultCount,
                    b.meanMs,
                    b.medianMs,
                    b.stdDevMs,
                    b.minMs,
                    b.maxMs,
                    b.p95Ms,
                    b.p99Ms,
                    b.coefficientOfVariation,
                    datasetName
                ));
            }
        }

        System.out.println("\\n✓ Detailed results saved to: " + csvFile);
    }

    private static void generateSummaryCSV(BenchmarkReport report, String datasetName) throws IOException {
        String csvFile = OUTPUT_DIR + "benchmark_summary_" + datasetName + ".csv";

        // Calculate aggregate statistics
        double overallMean = report.benchmarks.stream()
            .mapToDouble(b -> b.meanMs)
            .average()
            .orElse(0);

        double overallMedian = report.benchmarks.stream()
            .mapToDouble(b -> b.medianMs)
            .sorted()
            .skip(report.benchmarks.size() / 2)
            .findFirst()
            .orElse(0);

        double overallStdDev = Math.sqrt(
            report.benchmarks.stream()
                .mapToDouble(b -> Math.pow(b.meanMs - overallMean, 2))
                .sum() / report.benchmarks.size()
        );

        try (FileWriter writer = new FileWriter(csvFile)) {
            writer.write("metric,value,unit\n");
            writer.write(String.format("dataset,%s,\n", datasetName));
            writer.write(String.format("triple_count,%d,triples\n", report.tripleCount));
            writer.write(String.format("query_count,%d,queries\n", report.benchmarks.size()));
            writer.write(String.format("data_load_time,%.2f,ms\n", report.dataLoadTimeMs));
            writer.write(String.format("warmup_time,%.2f,ms\n", report.warmupTimeMs));
            writer.write(String.format("measurement_time,%.2f,ms\n", report.measurementTimeMs));
            writer.write(String.format("warmup_iterations,%d,iterations\n", WARMUP_ITERATIONS));
            writer.write(String.format("measurement_repetitions,%d,repetitions\n", MEASUREMENT_REPETITIONS));
            writer.write(String.format("overall_mean_query_time,%.4f,ms\n", overallMean));
            writer.write(String.format("overall_median_query_time,%.4f,ms\n", overallMedian));
            writer.write(String.format("overall_stddev_query_time,%.4f,ms\n", overallStdDev));
            writer.write(String.format("throughput,%.2f,queries/sec\n", 1000.0 / overallMean));
        }

        System.out.println("✓ Summary saved to: " + csvFile);
    }

    private static void printSummary(BenchmarkReport report) {
        System.out.println("\\n" + "=".repeat(60));
        System.out.println("BENCHMARK SUMMARY");
        System.out.println("=" .repeat(60));

        double overallMean = report.benchmarks.stream()
            .mapToDouble(b -> b.meanMs)
            .average()
            .orElse(0);

        double overallMedian = report.benchmarks.stream()
            .mapToDouble(b -> b.medianMs)
            .sorted()
            .skip(report.benchmarks.size() / 2)
            .findFirst()
            .orElse(0);

        System.out.println("\\nDataset: " + report.datasetName);
        System.out.println("Triples: " + report.tripleCount);
        System.out.println("Queries benchmarked: " + report.benchmarks.size());
        System.out.println();
        System.out.println("Timing:");
        System.out.println("  Data load: " + String.format("%.2f", report.dataLoadTimeMs / 1000.0) + " sec");
        System.out.println("  Warmup: " + String.format("%.2f", report.warmupTimeMs / 1000.0) + " sec");
        System.out.println("  Measurement: " + String.format("%.2f", report.measurementTimeMs / 1000.0) + " sec");
        System.out.println();
        System.out.println("Performance:");
        System.out.println("  Mean query time: " + String.format("%.4f", overallMean) + " ms");
        System.out.println("  Median query time: " + String.format("%.4f", overallMedian) + " ms");
        System.out.println("  Throughput: " + String.format("%.2f", 1000.0 / overallMean) + " queries/sec");
        System.out.println();

        // Variance analysis
        long highVarianceCount = report.benchmarks.stream()
            .filter(b -> b.coefficientOfVariation > 0.5)
            .count();

        System.out.println("Stability:");
        System.out.println("  High variance queries (CV > 0.5): " + highVarianceCount +
            " (" + String.format("%.1f", 100.0 * highVarianceCount / report.benchmarks.size()) + "%)");

        if (highVarianceCount > 0) {
            System.out.println("  Note: High variance may indicate GC interference or insufficient warmup");
        }

        // Performance distribution
        System.out.println("\\nQuery time distribution:");
        long fast = report.benchmarks.stream().filter(b -> b.meanMs < 1.0).count();
        long medium = report.benchmarks.stream().filter(b -> b.meanMs >= 1.0 && b.meanMs < 10.0).count();
        long slow = report.benchmarks.stream().filter(b -> b.meanMs >= 10.0).count();

        System.out.println("  Fast (<1 ms): " + fast +
            " (" + String.format("%.1f", 100.0 * fast / report.benchmarks.size()) + "%)");
        System.out.println("  Medium (1-10 ms): " + medium +
            " (" + String.format("%.1f", 100.0 * medium / report.benchmarks.size()) + "%)");
        System.out.println("  Slow (>10 ms): " + slow +
            " (" + String.format("%.1f", 100.0 * slow / report.benchmarks.size()) + "%)");
    }

    // Helper classes
    static class QueryInfo {
        String template;
        int queryIndex;
        StarQuery query;

        QueryInfo(String template, int queryIndex, StarQuery query) {
            this.template = template;
            this.queryIndex = queryIndex;
            this.query = query;
        }
    }

    static class QueryBenchmark {
        String template;
        int queryIndex;
        StarQuery query;
        int resultCount;
        double meanMs;
        double medianMs;
        double stdDevMs;
        double minMs;
        double maxMs;
        double p95Ms;
        double p99Ms;
        double coefficientOfVariation;
        List<Double> allTimes;

        QueryBenchmark(String template, int queryIndex, StarQuery query, int resultCount,
                      double meanMs, double medianMs, double stdDevMs,
                      double minMs, double maxMs, double p95Ms, double p99Ms,
                      double cv, List<Double> allTimes) {
            this.template = template;
            this.queryIndex = queryIndex;
            this.query = query;
            this.resultCount = resultCount;
            this.meanMs = meanMs;
            this.medianMs = medianMs;
            this.stdDevMs = stdDevMs;
            this.minMs = minMs;
            this.maxMs = maxMs;
            this.p95Ms = p95Ms;
            this.p99Ms = p99Ms;
            this.coefficientOfVariation = cv;
            this.allTimes = allTimes;
        }
    }

    static class BenchmarkReport {
        String datasetName;
        long tripleCount;
        double dataLoadTimeMs;
        double warmupTimeMs;
        double measurementTimeMs;
        List<QueryBenchmark> benchmarks = new ArrayList<>();

        BenchmarkReport(String datasetName) {
            this.datasetName = datasetName;
        }

        void addBenchmark(QueryBenchmark b) {
            benchmarks.add(b);
        }
    }
}
