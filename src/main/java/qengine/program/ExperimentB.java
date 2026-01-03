package qengine.program;

import fr.boreal.model.logicalElements.api.Substitution;
import org.eclipse.rdf4j.rio.RDFFormat;
import qengine.model.RDFTriple;
import qengine.model.StarQuery;
import qengine.parser.RDFTriplesParser;
import qengine.parser.StarQuerySparQLParser;
import qengine.storage.RDFGiantTable;
import qengine.storage.RDFHexaStore;
import qengine.storage.RDFStorage;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Experiment B: Index Optimization × Query Selectivity
 *
 * Factorial 2² design to evaluate:
 * 1. Effectiveness of indexation (RDFHexaStore vs RDFGiantTable)
 * 2. Whether indexation helps more on different query types
 *
 * Factor 1 (Optimization): RDFGiantTable (no index) vs RDFHexaStore (6 indexes)
 * Factor 2 (Selectivity): High (< 100 results) vs Low (≥ 100 results)
 *
 * 4 Configurations:
 * B1: No index + High selectivity
 * B2: Indexed + High selectivity
 * B3: No index + Low selectivity
 * B4: Indexed + Low selectivity
 */
public class ExperimentB {

    private static final String DATASET_DIR = "watdiv-mini-projet-partie-2/testsuite/dataset/";
    private static final String QUERIES_DIR = "watdiv-mini-projet-partie-2/testsuite/queries/100_deduplicated/";
    private static final String SELECTIVITY_DIR = "results/phase5/";
    private static final String OUTPUT_DIR = "results/phase5/";

    // Experimental configuration
    private static final int WARMUP_ITERATIONS = 20;
    private static final int MEASUREMENT_REPETITIONS = 10;

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: ExperimentB <optimization> <selectivity>");
            System.err.println("  optimization: noindex or indexed");
            System.err.println("  selectivity: high or low");
            System.exit(1);
        }

        String optimization = args[0];
        String selectivity = args[1];

        try {
            Files.createDirectories(Paths.get(OUTPUT_DIR));

            System.out.println("=" .repeat(60));
            System.out.println("EXPERIMENT B: INDEX × SELECTIVITY");
            System.out.println("=" .repeat(60));
            System.out.println("Configuration: " + optimization + " optimization, " + selectivity + " selectivity");
            System.out.println();

            runExperiment(optimization, selectivity);

            System.out.println("\\n" + "=".repeat(60));
            System.out.println("EXPERIMENT B CONFIGURATION COMPLETED");
            System.out.println("=" .repeat(60));

        } catch (Exception e) {
            System.err.println("Error during Experiment B: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void runExperiment(String optimization, String selectivity) throws IOException {
        // Determine config ID
        String configId;
        if (optimization.equals("noindex") && selectivity.equals("high")) configId = "B1";
        else if (optimization.equals("indexed") && selectivity.equals("high")) configId = "B2";
        else if (optimization.equals("noindex") && selectivity.equals("low")) configId = "B3";
        else if (optimization.equals("indexed") && selectivity.equals("low")) configId = "B4";
        else {
            throw new IllegalArgumentException("Invalid configuration: " + optimization + ", " + selectivity);
        }

        System.out.println("Configuration ID: " + configId);
        System.out.println("Storage: " + (optimization.equals("indexed") ? "RDFHexaStore (6 indexes)" : "RDFGiantTable (no index)"));
        System.out.println();

        // Load dataset
        System.out.println("--- Loading Dataset ---");
        long loadStart = System.currentTimeMillis();
        String datasetPath = DATASET_DIR + "data_500K.nt";
        RDFStorage store = loadDataset(datasetPath, optimization);
        long loadTime = System.currentTimeMillis() - loadStart;
        System.out.println("Dataset loaded in " + loadTime + " ms");
        System.out.println("Dataset size: " + store.size() + " triples");
        System.out.println("Storage type: " + store.getClass().getSimpleName());

        // Load queries based on selectivity
        System.out.println("\\n--- Loading Queries ---");
        String selectivityFile = SELECTIVITY_DIR + "queries_" + selectivity + "_selectivity.csv";
        List<QueryInfo> queries = loadQueriesFromSelectivityFile(selectivityFile);
        System.out.println("Loaded " + queries.size() + " " + selectivity + "-selectivity queries");

        // Warmup
        System.out.println("\\n--- Warmup Phase ---");
        long warmupStart = System.currentTimeMillis();
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            for (QueryInfo qInfo : queries) {
                Iterator<Substitution> results = store.match(qInfo.query);
                consumeResults(results);
            }
        }
        long warmupTime = System.currentTimeMillis() - warmupStart;
        System.out.println("Warmup completed in " + warmupTime + " ms");

        // Measurement
        System.out.println("\\n--- Measurement Phase ---");
        long measureStart = System.currentTimeMillis();
        List<QueryResult> results = new ArrayList<>();

        for (QueryInfo qInfo : queries) {
            double totalTime = 0;
            for (int rep = 0; rep < MEASUREMENT_REPETITIONS; rep++) {
                long startNanos = System.nanoTime();
                Iterator<Substitution> queryResults = store.match(qInfo.query);
                consumeResults(queryResults);
                long endNanos = System.nanoTime();
                totalTime += (endNanos - startNanos) / 1_000_000.0;
            }
            double meanTime = totalTime / MEASUREMENT_REPETITIONS;
            results.add(new QueryResult(qInfo.template, qInfo.queryIndex, meanTime));
        }
        long measureTime = System.currentTimeMillis() - measureStart;
        System.out.println("Measurement completed in " + measureTime + " ms");

        // Export results
        String outputFile = OUTPUT_DIR + "experiment_B_" + configId + ".csv";
        exportResults(configId, optimization, selectivity, results, outputFile);
        System.out.println("\\n✓ Results saved to: " + outputFile);

        // Print summary
        double meanTime = results.stream().mapToDouble(r -> r.timeMean).average().orElse(0);
        System.out.println("\\nSummary:");
        System.out.println("  Config: " + configId + " (" + optimization + ", " + selectivity + " sel.)");
        System.out.println("  Queries: " + results.size());
        System.out.println("  Mean time: " + String.format("%.4f", meanTime) + " ms");
    }

    private static RDFStorage loadDataset(String datasetPath, String optimization) throws IOException {
        RDFStorage store;
        if (optimization.equals("indexed")) {
            store = new RDFHexaStore(); // 6 indexes
        } else {
            store = new RDFGiantTable(); // No indexation
        }

        FileReader rdfFile = new FileReader(datasetPath);
        try (RDFTriplesParser parser = new RDFTriplesParser(rdfFile, RDFFormat.NTRIPLES)) {
            while (parser.hasNext()) {
                RDFTriple triple = parser.next();
                store.add(triple);
            }
        }

        return store;
    }

    private static List<QueryInfo> loadQueriesFromSelectivityFile(String selectivityFile) throws IOException {
        List<QueryInfo> queries = new ArrayList<>();

        // Read CSV file with template and query_index
        try (BufferedReader reader = new BufferedReader(new FileReader(selectivityFile))) {
            String line = reader.readLine(); // Skip header
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 2) {
                    String template = parts[0];
                    int queryIndex = Integer.parseInt(parts[1]);

                    // Load the specific query from the queryset file
                    String querysetPath = QUERIES_DIR + template + ".queryset";
                    StarQuery query = loadSpecificQuery(querysetPath, queryIndex);
                    if (query != null) {
                        queries.add(new QueryInfo(template, queryIndex, query));
                    }
                }
            }
        }

        return queries;
    }

    private static StarQuery loadSpecificQuery(String querysetPath, int targetIndex) throws IOException {
        try (StarQuerySparQLParser parser = new StarQuerySparQLParser(querysetPath)) {
            int currentIndex = 0;
            while (parser.hasNext()) {
                StarQuery query = (StarQuery) parser.next();
                if (currentIndex == targetIndex) {
                    return query;
                }
                currentIndex++;
            }
        }
        return null;
    }

    private static void consumeResults(Iterator<Substitution> results) {
        while (results.hasNext()) {
            results.next();
        }
    }

    // TODO: Fix csv formatting
    private static void exportResults(String configId, String optimization, String selectivity,
                                     List<QueryResult> results, String outputFile) throws IOException {
        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write("config_id,optimization,selectivity,template,query_index,time_ms\n");
            for (QueryResult r : results) {
                writer.write(String.format("%s,%s,%s,%s,%d,%.6f\n",
                    configId, optimization, selectivity, r.template, r.queryIndex, r.timeMean));
            }
        }
    }

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

    static class QueryResult {
        String template;
        int queryIndex;
        double timeMean;

        QueryResult(String template, int queryIndex, double timeMean) {
            this.template = template;
            this.queryIndex = queryIndex;
            this.timeMean = timeMean;
        }
    }
}
