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
 * Experiment A: Dataset Size × Memory
 *
 * Factorial 2² design to quantify:
 * 1. How performance scales with dataset size (500K vs 2M)
 * 2. Whether additional memory mitigates scaling effects (2GB vs 4GB)
 *
 * Factor 1 (Size): 500K (low) vs 2M (high)
 * Factor 2 (Memory): Set via JVM args externally
 *
 * 4 Configurations:
 * A1: 100K + 4GB (-Xmx4096m -Xms4096m)
 * A2: 100K + 8GB (-Xmx8192m -Xms8192m)
 * A3: 500K + 4GB (-Xmx4096m -Xms4096m)
 * A4: 500K + 8GB (-Xmx8192m -Xms8192m)
 */
public class ExperimentA {

    private static final String DATASET_DIR = "watdiv-mini-projet-partie-2/testsuite/dataset/";
    private static final String QUERIES_DIR = "watdiv-mini-projet-partie-2/testsuite/queries/100_deduplicated/";
    private static final String OUTPUT_DIR = "results/phase5/";

    // Experimental configuration
    private static final int WARMUP_ITERATIONS = 20;
    private static final int MEASUREMENT_REPETITIONS = 10;
    private static final int QUERY_SAMPLE_SIZE = 100;

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: ExperimentA <dataset_size> <heap_mb>");
            System.err.println("  dataset_size: 500K or 2M");
            System.err.println("  heap_mb: 2048 or 4096");
            System.exit(1);
        }

        String datasetSize = args[0];
        String heapMb = args[1];

        try {
            Files.createDirectories(Paths.get(OUTPUT_DIR));

            System.out.println("=" .repeat(60));
            System.out.println("EXPERIMENT A: SIZE × MEMORY");
            System.out.println("=" .repeat(60));
            System.out.println("Configuration: " + datasetSize + " dataset, " + heapMb + "MB heap");
            System.out.println();

            runExperiment(datasetSize, heapMb);

            System.out.println("\\n" + "=".repeat(60));
            System.out.println("EXPERIMENT A CONFIGURATION COMPLETED");
            System.out.println("=" .repeat(60));

        } catch (Exception e) {
            System.err.println("Error during Experiment A: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void runExperiment(String datasetSize, String heapMb) throws IOException {
        // Determine config ID (500K vs 2M datasets, 4GB vs 8GB heap)
        String configId;
        if (datasetSize.equals("500K") && heapMb.equals("4096")) configId = "A1";
        else if (datasetSize.equals("500K") && heapMb.equals("8192")) configId = "A2";
        else if (datasetSize.equals("2M") && heapMb.equals("4096")) configId = "A3";
        else if (datasetSize.equals("2M") && heapMb.equals("8192")) configId = "A4";
        else {
            throw new IllegalArgumentException("Invalid configuration: " + datasetSize + ", " + heapMb);
        }

        System.out.println("Configuration ID: " + configId);
        System.out.println();

        // Load dataset
        System.out.println("--- Loading Dataset ---");
        long loadStart = System.currentTimeMillis();
        String datasetPath = DATASET_DIR + "data_" + datasetSize + ".nt";
        RDFStorage store = loadDataset(datasetPath);
        long loadTime = System.currentTimeMillis() - loadStart;
        System.out.println("Dataset loaded in " + loadTime + " ms");
        System.out.println("Dataset size: " + store.size() + " triples");

        // Load and sample queries
        System.out.println("\\n--- Loading Queries ---");
        List<QueryInfo> allQueries = loadAllQueries(QUERIES_DIR);
        List<QueryInfo> sampleQueries = sampleQueries(allQueries, QUERY_SAMPLE_SIZE);
        System.out.println("Sampled " + sampleQueries.size() + " queries from " + allQueries.size() + " total");

        // Warmup
        System.out.println("\\n--- Warmup Phase ---");
        long warmupStart = System.currentTimeMillis();
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            for (QueryInfo qInfo : sampleQueries) {
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

        for (QueryInfo qInfo : sampleQueries) {
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
        String outputFile = OUTPUT_DIR + "experiment_A_" + configId + ".csv";
        exportResults(configId, datasetSize, heapMb, results, outputFile);
        System.out.println("\\n✓ Results saved to: " + outputFile);

        // Print summary
        double meanTime = results.stream().mapToDouble(r -> r.timeMean).average().orElse(0);
        System.out.println("\\nSummary:");
        System.out.println("  Config: " + configId + " (" + datasetSize + ", " + heapMb + "MB)");
        System.out.println("  Queries: " + results.size());
        System.out.println("  Mean time: " + String.format("%.4f", meanTime) + " ms");
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

        File[] queryFiles = dir.listFiles((d, name) -> name.endsWith(".queryset"));
        if (queryFiles == null) return queries;

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

    private static List<QueryInfo> sampleQueries(List<QueryInfo> allQueries, int sampleSize) {
        if (allQueries.size() <= sampleSize) {
            return new ArrayList<>(allQueries);
        }

        // Stratified sampling: take queries evenly from all templates
        Map<String, List<QueryInfo>> byTemplate = new HashMap<>();
        for (QueryInfo q : allQueries) {
            byTemplate.computeIfAbsent(q.template, k -> new ArrayList<>()).add(q);
        }

        List<QueryInfo> sample = new ArrayList<>();
        int perTemplate = sampleSize / byTemplate.size();
        Random random = new Random(42); // Fixed seed for reproducibility

        for (List<QueryInfo> templateQueries : byTemplate.values()) {
            Collections.shuffle(templateQueries, random);
            sample.addAll(templateQueries.subList(0, Math.min(perTemplate, templateQueries.size())));
        }

        // Fill up to sampleSize if needed
        if (sample.size() < sampleSize) {
            List<QueryInfo> remaining = new ArrayList<>(allQueries);
            remaining.removeAll(sample);
            Collections.shuffle(remaining, random);
            sample.addAll(remaining.subList(0, Math.min(sampleSize - sample.size(), remaining.size())));
        }

        return sample.subList(0, Math.min(sampleSize, sample.size()));
    }

    private static void consumeResults(Iterator<Substitution> results) {
        while (results.hasNext()) {
            results.next();
        }
    }

    // TODO: Fix csv formatting
    private static void exportResults(String configId, String datasetSize, String heapMb,
                                     List<QueryResult> results, String outputFile) throws IOException {
        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write("config_id,dataset_size,heap_mb,template,query_index,time_ms\n");
            for (QueryResult r : results) {
                writer.write(String.format("%s,%s,%s,%s,%d,%.6f\n",
                    configId, datasetSize, heapMb, r.template, r.queryIndex, r.timeMean));
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
