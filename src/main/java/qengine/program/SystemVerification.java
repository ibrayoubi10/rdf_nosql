package qengine.program;

import fr.boreal.model.formula.api.FOFormula;
import fr.boreal.model.formula.api.FOFormulaConjunction;
import fr.boreal.model.kb.api.FactBase;
import fr.boreal.model.logicalElements.api.Substitution;
import fr.boreal.model.query.api.FOQuery;
import fr.boreal.model.query.api.Query;
import fr.boreal.model.queryEvaluation.api.FOQueryEvaluator;
import fr.boreal.query_evaluation.generic.GenericFOQueryEvaluator;
import fr.boreal.storage.natives.SimpleInMemoryGraphStore;
import org.eclipse.rdf4j.rio.RDFFormat;
import qengine.model.RDFTriple;
import qengine.model.StarQuery;
import qengine.parser.RDFTriplesParser;
import qengine.parser.StarQuerySparQLParser;
import qengine.storage.RDFHexaStore;
import qengine.storage.RDFStorage;
import qengine.storage.ColleagueWrapper;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Correctness and Completeness Verification
 *
 * This program:
 * 1. Uses InteGraal as the oracle
 * 2. Tests custom implementations (RDFHexaStore) against the oracle
 * 3. Validates on a sample of queries from the deduplicated workload
 * 4. Generates correctness report
 */
public class SystemVerification {

    private static final String DATASET_DIR = "watdiv-mini-projet-partie-2/testsuite/dataset/";
    private static final String QUERIES_DIR = "watdiv-mini-projet-partie-2/testsuite/queries/100_deduplicated/";
    private static final String OUTPUT_DIR = "results/phase3/";
    private static final int SAMPLE_SIZE = 100; // Test on 100 random queries
    private static final Random RANDOM = new Random(42); // Fixed seed for reproducibility

    public static void main(String[] args) {
        try {
            Files.createDirectories(Paths.get(OUTPUT_DIR));

            System.out.println("=" .repeat(60));
            System.out.println("CORRECTNESS AND COMPLETENESS VERIFICATION");
            System.out.println("=" .repeat(60));
            System.out.println();

            // Test on 500K dataset
            System.out.println("Testing on 500K dataset...");
            CorrectnessReport myReport500K = runVerification(
                DATASET_DIR + "data_500K.nt",
                "500K",
                "MyStore"
            );

            CorrectnessReport colleagueReport500K = runVerification(
                    DATASET_DIR + "data_500K.nt",
                    "500K",
                    "MyStore"
            );

            // Generate reports
            generateReport(myReport500K, "500K", "MyStore");
            generateSummary(myReport500K);
            generateReport(colleagueReport500K, "500K", "ColleagueStore");
            generateSummary(colleagueReport500K);

            System.out.println("\n" + "=".repeat(60));
            System.out.println("VERIFICATION COMPLETED");
            System.out.println("=" .repeat(60));

        } catch (Exception e) {
            System.err.println("Error during correctness verification: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static CorrectnessReport runVerification(String datasetPath, String datasetName, String store) throws IOException {
        System.out.println("\n--- Loading Dataset ---");
        long startLoad = System.currentTimeMillis();

        // Load data into oracle (InteGraal)
        List<RDFTriple> rdfTriples = loadDataset(datasetPath);
        FactBase oracleStore = new SimpleInMemoryGraphStore();
        for (RDFTriple triple : rdfTriples) {
            oracleStore.add(triple);
        }

        // Load data into custom implementation
        RDFStorage customStore;
        if (store.equals("Colleague")) {
            customStore = new ColleagueWrapper();
        } else if (store.equals("MyStore")) {
            customStore = new RDFHexaStore();
        } else {
            throw new IllegalArgumentException("Unknown store type: " + store);
        }

        customStore.addAll(rdfTriples);

        long loadTime = System.currentTimeMillis() - startLoad;
        System.out.println("Loaded " + rdfTriples.size() + " triples in " + loadTime + " ms");

        // Load queries and sample them
        System.out.println("\n--- Loading and Sampling Queries ---");
        List<QueryFile> allQueries = loadAllQueries(QUERIES_DIR);
        System.out.println("Total queries available: " + allQueries.size());

        // Sample queries
        List<QueryFile> sampleQueries = sampleQueries(allQueries, SAMPLE_SIZE);
        System.out.println("Sampled " + sampleQueries.size() + " queries for testing");

        // Run verification
        System.out.println("\n--- Running Verification ---");
        FOQueryEvaluator<FOFormula> evaluator = GenericFOQueryEvaluator.defaultInstance();
        CorrectnessReport report = new CorrectnessReport(datasetName);

        for (int i = 0; i < sampleQueries.size(); i++) {
            QueryFile qf = sampleQueries.get(i);
            System.out.printf("[%d/%d] Testing %s (query %d)...%n",
                i + 1, sampleQueries.size(), qf.template, qf.queryIndex);

            try {
                // Evaluate with oracle (InteGraal)
                FOQuery<FOFormulaConjunction> foQuery = qf.query.asFOQuery();
                Set<Substitution> oracleResults = iteratorToSet(evaluator.evaluate(foQuery, oracleStore));

                // Evaluate with custom implementation
                Set<Substitution> customResults = iteratorToSet(customStore.match(qf.query));

                // Compare results
                boolean correct = oracleResults.containsAll(customResults); // No false positives
                boolean complete = customResults.containsAll(oracleResults); // No missing results

                QueryVerification verification = new QueryVerification(
                    qf.template,
                    qf.queryIndex,
                    qf.query,
                    oracleResults.size(),
                    customResults.size(),
                    correct,
                    complete
                );

                report.addVerification(verification);

                if (correct && complete) {
                    System.out.println("  ✓ CORRECT AND COMPLETE");
                } else if (!correct && !complete) {
                    System.out.println("  ✗ INCORRECT (has false positives and missing results)");
                } else if (!correct) {
                    System.out.println("  ✗ INCORRECT (has false positives)");
                } else {
                    System.out.println("  ✗ INCOMPLETE (missing results)");
                }

            } catch (Exception e) {
                System.out.println("  ✗ ERROR: " + e.getMessage());
                report.addError(qf.template, qf.queryIndex, e.getMessage());
            }
        }

        return report;
    }

    private static List<RDFTriple> loadDataset(String datasetPath) throws IOException {
        List<RDFTriple> rdfTriples = new ArrayList<>();
        FileReader rdfFile = new FileReader(datasetPath);

        try (RDFTriplesParser parser = new RDFTriplesParser(rdfFile, RDFFormat.NTRIPLES)) {
            while (parser.hasNext()) {
                rdfTriples.add(parser.next());
            }
        }

        return rdfTriples;
    }

    private static List<QueryFile> loadAllQueries(String queryDir) throws IOException {
        List<QueryFile> queries = new ArrayList<>();
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
                    Query query = parser.next();
                    if (query instanceof StarQuery starQuery) {
                        queries.add(new QueryFile(templateName, queryIndex++, starQuery));
                    }
                }
            }
        }

        return queries;
    }

    private static List<QueryFile> sampleQueries(List<QueryFile> allQueries, int sampleSize) {
        if (allQueries.size() <= sampleSize) {
            return new ArrayList<>(allQueries);
        }

        // Shuffle and take first sampleSize
        List<QueryFile> shuffled = new ArrayList<>(allQueries);
        Collections.shuffle(shuffled, RANDOM);
        return shuffled.subList(0, sampleSize);
    }

    private static Set<Substitution> iteratorToSet(Iterator<Substitution> iter) {
        Set<Substitution> set = new HashSet<>();
        iter.forEachRemaining(set::add);
        return set;
    }

    private static void generateReport(CorrectnessReport report, String datasetName, String store) throws IOException {
        String csvFile = OUTPUT_DIR + "correctness_" + store + "_" + datasetName + ".csv";

        try (FileWriter writer = new FileWriter(csvFile)) {
            writer.write("template,query_index,oracle_results,custom_results,correct,complete,status\n");

            for (QueryVerification v : report.verifications) {
                String status;
                if (v.correct && v.complete) {
                    status = "CORRECT";
                } else if (!v.correct && !v.complete) {
                    status = "INCORRECT_INCOMPLETE";
                } else if (!v.correct) {
                    status = "INCORRECT";
                } else {
                    status = "INCOMPLETE";
                }

                writer.write(String.format("%s,%d,%d,%d,%b,%b,%s\n",
                    v.template,
                    v.queryIndex,
                    v.oracleResultCount,
                    v.customResultCount,
                    v.correct,
                    v.complete,
                    status
                ));
            }
        }

        System.out.println("\n✓ Detailed results saved to: " + csvFile);
    }

    private static void generateSummary(CorrectnessReport report) {
        System.out.println("\n" + "=" .repeat(60));
        System.out.println("CORRECTNESS SUMMARY - " + report.datasetName);
        System.out.println("=" .repeat(60));

        int total = report.verifications.size();
        int correct = (int) report.verifications.stream().filter(v -> v.correct && v.complete).count();
        int incomplete = (int) report.verifications.stream().filter(v -> v.correct && !v.complete).count();
        int incorrect = (int) report.verifications.stream().filter(v -> !v.correct).count();
        int errors = report.errors.size();

        System.out.println("\nQueries Tested: " + total);
        System.out.println("Correct & Complete: " + correct + " (" + String.format("%.1f%%", 100.0 * correct / total) + ")");
        System.out.println("Incomplete: " + incomplete + " (" + String.format("%.1f%%", 100.0 * incomplete / total) + ")");
        System.out.println("Incorrect: " + incorrect + " (" + String.format("%.1f%%", 100.0 * incorrect / total) + ")");
        System.out.println("Errors: " + errors);

        System.out.println("\n" + "-".repeat(60));
        if (correct == total) {
            System.out.println("✓ RESULT: 100% CORRECTNESS - Ready for performance evaluation");
        } else if (correct >= total * 0.95) {
            System.out.println("⚠ RESULT: " + String.format("%.1f%%", 100.0 * correct / total) +
                " correctness - Acceptable, document issues");
        } else {
            System.out.println("✗ RESULT: " + String.format("%.1f%%", 100.0 * correct / total) +
                " correctness - STOP and fix bugs before proceeding");
        }

        // Show problematic queries if any
        if (incorrect > 0 || incomplete > 0) {
            System.out.println("\n" + "-".repeat(60));
            System.out.println("PROBLEMATIC QUERIES:");

            List<QueryVerification> problematic = report.verifications.stream()
                .filter(v -> !v.correct || !v.complete)
                .limit(10)
                .toList();

            for (QueryVerification v : problematic) {
                String issue = !v.correct ? "INCORRECT" : "INCOMPLETE";
                System.out.printf("  - %s query %d: %s (oracle: %d, custom: %d)%n",
                    v.template, v.queryIndex, issue, v.oracleResultCount, v.customResultCount);
            }

            if (incorrect + incomplete > 10) {
                System.out.println("  ... and " + (incorrect + incomplete - 10) + " more");
            }
        }
    }

    // Helper classes
    static class QueryFile {
        String template;
        int queryIndex;
        StarQuery query;

        QueryFile(String template, int queryIndex, StarQuery query) {
            this.template = template;
            this.queryIndex = queryIndex;
            this.query = query;
        }
    }

    static class QueryVerification {
        String template;
        int queryIndex;
        StarQuery query;
        int oracleResultCount;
        int customResultCount;
        boolean correct;
        boolean complete;

        QueryVerification(String template, int queryIndex, StarQuery query,
                         int oracleResultCount, int customResultCount,
                         boolean correct, boolean complete) {
            this.template = template;
            this.queryIndex = queryIndex;
            this.query = query;
            this.oracleResultCount = oracleResultCount;
            this.customResultCount = customResultCount;
            this.correct = correct;
            this.complete = complete;
        }
    }

    static class CorrectnessReport {
        String datasetName;
        List<QueryVerification> verifications = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        CorrectnessReport(String datasetName) {
            this.datasetName = datasetName;
        }

        void addVerification(QueryVerification v) {
            verifications.add(v);
        }

        void addError(String template, int queryIndex, String error) {
            errors.add(template + " query " + queryIndex + ": " + error);
        }
    }
}
