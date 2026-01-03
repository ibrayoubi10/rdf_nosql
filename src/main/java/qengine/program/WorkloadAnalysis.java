package qengine.program;

import fr.boreal.model.formula.api.FOFormula;
import fr.boreal.model.formula.api.FOFormulaConjunction;
import fr.boreal.model.kb.api.FactBase;
import fr.boreal.model.logicalElements.api.Substitution;
import fr.boreal.model.query.api.FOQuery;
import fr.boreal.model.queryEvaluation.api.FOQueryEvaluator;
import fr.boreal.query_evaluation.generic.GenericFOQueryEvaluator;
import fr.boreal.storage.natives.SimpleInMemoryGraphStore;
import org.eclipse.rdf4j.rio.RDFFormat;
import qengine.model.RDFTriple;
import qengine.model.StarQuery;
import qengine.parser.RDFTriplesParser;
import qengine.parser.StarQuerySparQLParser;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Execute Queries and Collect Result Cardinalities
 *
 * This program:
 * 1. Loads the 500K and 2M datasets
 * 2. Executes all queries from the 100-query set
 * 3. Records: template name, query ID, number of results, execution time
 * 4. Exports results to CSV files: workload_analysis_500K.csv and workload_analysis_2M.csv
 */

// TODO: Create interface to share methods with other phases
public class WorkloadAnalysis {

    private static final String DATASET_DIR = "watdiv-mini-projet-partie-2/testsuite/dataset/";
    private static final String QUERIES_DIR = "watdiv-mini-projet-partie-2/testsuite/queries/100/";
    private static final String OUTPUT_DIR = "results/phase2/";

    public static void main(String[] args) {
        try {
            // Create output directory if it doesn't exist
            Files.createDirectories(Paths.get(OUTPUT_DIR));

            System.out.println("=== Query Workload Validation ===\n");

            // Process both datasets
            String[] datasets = {"data_500K.nt", "data_2M.nt"};

            for (String datasetFile : datasets) {
                String datasetName = datasetFile.replace(".nt", "");
                System.out.println("Processing dataset: " + datasetName);

                long startLoad = System.currentTimeMillis();
                FactBase store = loadDataset(DATASET_DIR + datasetFile);
                long loadTime = System.currentTimeMillis() - startLoad;
                System.out.println("Dataset loaded in " + loadTime + " ms");
                System.out.println("Dataset size: " + store.size() + " triples\n");

                // Execute queries and collect results
                List<QueryResult> results = executeQueriesFromDirectory(store, QUERIES_DIR);

                // Export to CSV
                String outputFile = OUTPUT_DIR + "workload_analysis_" + datasetName + ".csv";
                exportToCSV(results, datasetName, outputFile);

                System.out.println("Results exported to: " + outputFile);
                System.out.println("Total queries executed: " + results.size() + "\n");
                System.out.println("----------------------------------------\n");
            }

            System.out.println("Workload analysis completed successfully!");

        } catch (Exception e) {
            System.err.println("Error during workload analysis: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Load an RDF dataset from a file into a FactBase.
     */
    private static FactBase loadDataset(String datasetPath) throws IOException {
        FactBase store = new SimpleInMemoryGraphStore();
        FileReader rdfFile = new FileReader(datasetPath);

        try (RDFTriplesParser parser = new RDFTriplesParser(rdfFile, RDFFormat.NTRIPLES)) {
            while (parser.hasNext()) {
                RDFTriple triple = parser.next();
                store.add(triple);
            }
        }

        return store;
    }

    /**
     * Execute all queries from the query directory and collect statistics.
     */
    private static List<QueryResult> executeQueriesFromDirectory(FactBase store, String queryDir) throws IOException {
        List<QueryResult> results = new ArrayList<>();
        File dir = new File(queryDir);

        if (!dir.exists() || !dir.isDirectory()) {
            throw new IOException("Query directory not found: " + queryDir);
        }

        // Get all .queryset files
        File[] queryFiles = dir.listFiles((d, name) -> name.endsWith(".queryset"));

        if (queryFiles == null || queryFiles.length == 0) {
            throw new IOException("No .queryset files found in: " + queryDir);
        }

        Arrays.sort(queryFiles); // Sort for consistent ordering

        FOQueryEvaluator<FOFormula> evaluator = GenericFOQueryEvaluator.defaultInstance();

        // Process each query file
        for (File queryFile : queryFiles) {
            String templateName = extractTemplateName(queryFile.getName());
            System.out.println("Processing template: " + templateName);

            List<QueryResult> templateResults = executeQueriesFromFile(
                store, evaluator, queryFile, templateName
            );
            results.addAll(templateResults);

            System.out.println("  Queries in template: " + templateResults.size());
        }

        return results;
    }

    /**
     * Execute all queries from a single query file.
     */
    private static List<QueryResult> executeQueriesFromFile(
            FactBase store,
            FOQueryEvaluator<FOFormula> evaluator,
            File queryFile,
            String templateName) throws IOException {

        List<QueryResult> results = new ArrayList<>();

        try (StarQuerySparQLParser parser = new StarQuerySparQLParser(queryFile.getAbsolutePath())) {
            int queryIndex = 0;

            while (parser.hasNext()) {
                StarQuery starQuery = (StarQuery) parser.next();
                queryIndex++;

                String queryId = templateName + "_query_" + String.format("%03d", queryIndex);

                // Execute query and measure time
                long startTime = System.nanoTime();
                FOQuery<FOFormulaConjunction> foQuery = starQuery.asFOQuery();
                Iterator<Substitution> resultIterator = evaluator.evaluate(foQuery, store);

                // Count results
                int resultCount = 0;
                while (resultIterator.hasNext()) {
                    resultIterator.next();
                    resultCount++;
                }

                long endTime = System.nanoTime();
                double timeMs = (endTime - startTime) / 1_000_000.0;

                QueryResult result = new QueryResult(
                    templateName,
                    queryId,
                    resultCount,
                    timeMs,
                    starQuery.getRdfAtoms().size(), // Number of triple patterns
                    starQuery
                );

                results.add(result);
            }
        }

        return results;
    }

    /**
     * Extract template name from query file name.
     * Example: "Q_1_eligibleregion.queryset" -> "Q_1_eligibleregion"
     */
    private static String extractTemplateName(String filename) {
        return filename.replace(".queryset", "");
    }

    /**
     * Export query results to CSV file.
     */
    private static void exportToCSV(List<QueryResult> results, String datasetName, String outputFile) throws IOException {
        try (FileWriter writer = new FileWriter(outputFile)) {
            // Write CSV header
            writer.write("template,query_id,result_count,time_ms,dataset,triple_patterns\n");

            // Write data rows
            for (QueryResult result : results) {
                writer.write(String.format("%s,%s,%d,%.3f,%s,%d\n",
                    result.template,
                    result.queryId,
                    result.resultCount,
                    result.timeMs,
                    datasetName,
                    result.triplePatterns
                ));
            }
        }
    }

    /**
     * Data class to hold query execution results.
     */
    static class QueryResult {
        String template;
        String queryId;
        int resultCount;
        double timeMs;
        int triplePatterns;
        StarQuery query;

        QueryResult(String template, String queryId, int resultCount, double timeMs,
                    int triplePatterns, StarQuery query) {
            this.template = template;
            this.queryId = queryId;
            this.resultCount = resultCount;
            this.timeMs = timeMs;
            this.triplePatterns = triplePatterns;
            this.query = query;
        }
    }
}
