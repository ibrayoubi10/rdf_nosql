package qengine.program;

import fr.boreal.model.formula.api.FOFormula;
import fr.boreal.model.formula.api.FOFormulaConjunction;
import fr.boreal.model.query.api.Query;
import fr.boreal.model.kb.api.FactBase;
import fr.boreal.model.query.api.FOQuery;
import fr.boreal.model.logicalElements.api.Substitution;
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
import java.util.stream.Collectors;

public final class MainBenchmark {

    private static final String WATDIV_DIR = "watdiv-mini-projet-partie-2/";
    private static final String QUERIES_BASE_DIR = WATDIV_DIR + "testsuite/queries/";
    private static final String RESULTS_DIR = "results/";

    // Datasets à tester
    private static final String[] DATASETS = {
            WATDIV_DIR + "data_500K.nt",
            WATDIV_DIR + "data_2M.nt"
    };

    private static final String[] DATASET_NAMES = {"500K", "2M"};

    // Query sets à tester (répertoires)
    private static final String[] QUERY_SETS = {"100", "1000", "10000"};

    public static void main(String[] args) {
        try {
            // Créer le répertoire des résultats
            Files.createDirectories(Paths.get(RESULTS_DIR));

            System.out.println("=================================================");
            System.out.println("   RDF Query Engine Benchmark - WatDiv");
            System.out.println("=================================================\n");

            // Tester chaque combinaison dataset x queryset
            List<BenchmarkExperiment> allExperiments = new ArrayList<>();

            for (String datasetName : DATASET_NAMES) {
                for (String querySet : QUERY_SETS) {
                    System.out.println("=================================================");
                    System.out.printf("   EXPERIMENT: %s triples / %s queries%n", datasetName, querySet);
                    System.out.println("=================================================\n");

                    String datasetPath = WATDIV_DIR + "data_" + datasetName + ".nt";
                    String queriesDir = QUERIES_BASE_DIR + querySet + "/";

                    BenchmarkExperiment experiment = runExperiment(
                            datasetPath, datasetName, queriesDir, querySet);
                    allExperiments.add(experiment);

                    System.out.println();
                }
            }

            // Générer le rapport final comparatif
            generateFinalReport(allExperiments);

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Exécute une expérience complète (1 dataset + 1 query set).
     */
    private static BenchmarkExperiment runExperiment(String datasetPath, String datasetName,
                                                     String queriesDir, String querySetName) throws IOException {
        // Charger les requêtes
        System.out.println("=== Loading Queries ===");
        List<QueryFile> queryFiles = loadQueriesFromDirectory(queriesDir);
        System.out.printf("Found %d query files in %s%n", queryFiles.size(), querySetName);

        int starQueryCount = countStarQueries(queryFiles);
        System.out.printf("Star queries: %d/%d (%.1f%%)%n%n",
                starQueryCount, queryFiles.size(),
                queryFiles.isEmpty() ? 0 : 100.0 * starQueryCount / queryFiles.size());

        // Chargement des données
        System.out.println("=== Phase 1: Loading RDF Data ===");
        long startLoad = System.currentTimeMillis();
        List<RDFTriple> rdfTriples = parseRDFData(datasetPath);
        long endLoad = System.currentTimeMillis();
        long loadTime = endLoad - startLoad;
        System.out.printf("Loaded %d triples in %d ms%n%n", rdfTriples.size(), loadTime);

        // Initialisation des stores
        System.out.println("=== Phase 2: Initializing Stores ===");
        long startStoreInit = System.currentTimeMillis();
        FactBase myStore = new SimpleInMemoryGraphStore();
        FactBase oracleStore = new SimpleInMemoryGraphStore();

        for (RDFTriple triple : rdfTriples) {
            myStore.add(triple);
            oracleStore.add(triple);
        }
        long endStoreInit = System.currentTimeMillis();
        long storeInitTime = endStoreInit - startStoreInit;
        System.out.printf("Stores initialized in %d ms%n%n", storeInitTime);

        // Évaluation des requêtes
        System.out.println("=== Phase 3: Query Evaluation ===");
        List<QueryResult> results = evaluateQueries(queryFiles, myStore, oracleStore,
                datasetName, querySetName);

        // Créer l'expérience
        BenchmarkExperiment experiment = new BenchmarkExperiment(
                datasetName, querySetName, rdfTriples.size(),
                loadTime, storeInitTime, results);

        // Générer le rapport pour cette expérience
        generateExperimentReport(experiment);

        return experiment;
    }

    /**
     * Charge tous les fichiers .queryset d'un répertoire spécifique.
     */
    private static List<QueryFile> loadQueriesFromDirectory(String queriesDir) throws IOException {
        List<QueryFile> queryFiles = new ArrayList<>();
        File dir = new File(queriesDir);

        if (!dir.exists() || !dir.isDirectory()) {
            throw new IOException("Queries directory not found: " + queriesDir);
        }

        // Chercher les fichiers _100.queryset, _1000.queryset, _10000.queryset
        File[] files = dir.listFiles((d, name) -> name.endsWith(".queryset"));
        if (files == null || files.length == 0) {
            throw new IOException("No .queryset files found in: " + queriesDir);
        }

        Arrays.sort(files, Comparator.comparing(File::getName));

        for (File file : files) {
            try (StarQuerySparQLParser parser = new StarQuerySparQLParser(file.getAbsolutePath())) {
                while (parser.hasNext()) {
                    Query query = parser.next();
                    if (query instanceof StarQuery starQuery) {
                        queryFiles.add(new QueryFile(file.getName(), starQuery, true));
                    } else {
                        queryFiles.add(new QueryFile(file.getName(), null, false));
                        System.out.println("Warning: Query in " + file.getName() + " is not a star query");
                    }
                }
            } catch (Exception e) {
                System.err.println("Error parsing " + file.getName() + ": " + e.getMessage());
            }
        }

        return queryFiles;
    }

    /**
     * Compte le nombre de requêtes en étoile.
     */
    private static int countStarQueries(List<QueryFile> queryFiles) {
        return (int) queryFiles.stream().filter(qf -> qf.isStarQuery).count();
    }

    /**
     * Parse le fichier RDF.
     */
    private static List<RDFTriple> parseRDFData(String rdfFilePath) throws IOException {
        FileReader rdfFile = new FileReader(rdfFilePath);
        List<RDFTriple> rdfTriples = new ArrayList<>();

        // Détection du format
        RDFFormat format = RDFFormat.NTRIPLES;
        if (rdfFilePath.endsWith(".n3")) {
            format = RDFFormat.N3;
        } else if (rdfFilePath.endsWith(".ttl")) {
            format = RDFFormat.TURTLE;
        }

        try (RDFTriplesParser parser = new RDFTriplesParser(rdfFile, format)) {
            while (parser.hasNext()) {
                rdfTriples.add(parser.next());
            }
        }
        return rdfTriples;
    }

    /**
     * Évalue toutes les requêtes.
     */
    private static List<QueryResult> evaluateQueries(List<QueryFile> queryFiles,
                                                     FactBase myStore, FactBase oracleStore,
                                                     String datasetName, String querySetName) {
        FOQueryEvaluator<FOFormula> evaluator = GenericFOQueryEvaluator.defaultInstance();
        List<QueryResult> results = new ArrayList<>();

        int queryNum = 0;
        for (QueryFile qf : queryFiles) {
            queryNum++;

            // Ignorer les requêtes non-étoile
            if (!qf.isStarQuery || qf.starQuery == null) {
                System.out.printf("[%d/%d] Skipping (not a star query)%n",
                        queryNum, queryFiles.size());
                continue;
            }

            System.out.printf("[%d/%d] Evaluating query...", queryNum, queryFiles.size());

            try {
                FOQuery<FOFormulaConjunction> foQuery = qf.starQuery.asFOQuery();

                // Évaluation avec mon système
                long startMy = System.nanoTime();
                Set<Substitution> myResults = iteratorToSet(evaluator.evaluate(foQuery, myStore));
                long endMy = System.nanoTime();
                long myTime = (endMy - startMy) / 1_000_000;

                // Évaluation avec l'oracle
                long startOracle = System.nanoTime();
                Set<Substitution> oracleResults = iteratorToSet(evaluator.evaluate(foQuery, oracleStore));
                long endOracle = System.nanoTime();
                long oracleTime = (endOracle - startOracle) / 1_000_000;

                // Vérification
                boolean correct = oracleResults.containsAll(myResults);
                boolean complete = myResults.containsAll(oracleResults);

                QueryResult result = new QueryResult(queryNum, qf.filename, qf.starQuery,
                        myResults.size(), oracleResults.size(), myTime, oracleTime, correct, complete);
                results.add(result);

                // Export détaillé
                exportQueryResults(result, myResults, oracleResults, datasetName, querySetName);

                System.out.printf(" %d results, %d ms %s%n",
                        oracleResults.size(), myTime,
                        (correct && complete) ? "✓" : "✗");

            } catch (Exception e) {
                System.out.printf(" ERROR: %s%n", e.getMessage());
            }
        }

        return results;
    }

    /**
     * Convertit Iterator en Set.
     */
    private static Set<Substitution> iteratorToSet(Iterator<Substitution> iter) {
        Set<Substitution> set = new HashSet<>();
        iter.forEachRemaining(set::add);
        return set;
    }

    /**
     * Exporte les résultats d'une requête.
     */
    private static void exportQueryResults(QueryResult result, Set<Substitution> myResults,
                                           Set<Substitution> oracleResults,
                                           String datasetName, String querySetName) throws IOException {
        String filename = RESULTS_DIR + datasetName + "_" + querySetName + "_query" + result.queryNumber + ".txt";

        try (FileWriter fw = new FileWriter(filename)) {
            fw.write("Dataset: " + datasetName + "\n");
            fw.write("Query Set: " + querySetName + "\n");
            fw.write("Query #: " + result.queryNumber + "\n");
            fw.write("=" .repeat(60) + "\n\n");
            fw.write("Query: " + result.starQuery + "\n\n");

            fw.write("=== Performance ===\n");
            fw.write(String.format("My system: %d ms%n", result.myTime));
            fw.write(String.format("Oracle: %d ms%n", result.oracleTime));
            fw.write(String.format("Speedup: %.2fx%n%n",
                    result.myTime > 0 ? (double) result.oracleTime / result.myTime : 0));

            fw.write("=== Results ===\n");
            fw.write(String.format("My results: %d%n", result.myResultCount));
            fw.write(String.format("Oracle results: %d%n%n", result.oracleResultCount));

            fw.write("=== Correctness ===\n");
            fw.write("Correct: " + result.correct + "\n");
            fw.write("Complete: " + result.complete + "\n\n");

            if (!result.correct || !result.complete) {
                fw.write("=== My Results ===\n");
                for (Substitution s : myResults) {
                    fw.write(s + "\n");
                }

                fw.write("\n=== Oracle Results ===\n");
                for (Substitution s : oracleResults) {
                    fw.write(s + "\n");
                }
            }
        }
    }

    /**
     * Génère le rapport pour une expérience.
     */
    private static void generateExperimentReport(BenchmarkExperiment experiment) throws IOException {
        String csvFile = RESULTS_DIR + experiment.datasetName + "_" + experiment.querySetName + "_stats.csv";

        try (FileWriter fw = new FileWriter(csvFile)) {
            fw.write("query_number,query_file,my_results,oracle_results,my_time_ms,oracle_time_ms,correct,complete\n");

            for (QueryResult r : experiment.results) {
                fw.write(String.format("%d,%s,%d,%d,%d,%d,%b,%b%n",
                        r.queryNumber, r.queryFilename, r.myResultCount, r.oracleResultCount,
                        r.myTime, r.oracleTime, r.correct, r.complete));
            }
        }

        // Calcul des statistiques
        int totalQueries = experiment.results.size();
        int correctCount = (int) experiment.results.stream().filter(r -> r.correct).count();
        int completeCount = (int) experiment.results.stream().filter(r -> r.complete).count();
        int zeroAnswers = (int) experiment.results.stream().filter(r -> r.oracleResultCount == 0).count();
        long totalMyTime = experiment.results.stream().mapToLong(r -> r.myTime).sum();
        long totalOracleTime = experiment.results.stream().mapToLong(r -> r.oracleTime).sum();

        System.out.println("\n--- Summary for " + experiment.datasetName + " / " + experiment.querySetName + " ---");
        System.out.printf("Total queries: %d%n", totalQueries);
        System.out.printf("Queries with 0 answers: %d (%.1f%%)%n",
                zeroAnswers, totalQueries > 0 ? 100.0 * zeroAnswers / totalQueries : 0);
        System.out.printf("Correct: %d/%d (%.1f%%)%n",
                correctCount, totalQueries, totalQueries > 0 ? 100.0 * correctCount / totalQueries : 0);
        System.out.printf("Complete: %d/%d (%.1f%%)%n",
                completeCount, totalQueries, totalQueries > 0 ? 100.0 * completeCount / totalQueries : 0);
        System.out.printf("Total time (my): %d ms%n", totalMyTime);
        System.out.printf("Total time (oracle): %d ms%n", totalOracleTime);
        System.out.printf("Avg speedup: %.2fx%n", totalMyTime > 0 ? (double) totalOracleTime / totalMyTime : 0);
    }

    /**
     * Génère le rapport final comparatif.
     */
    private static void generateFinalReport(List<BenchmarkExperiment> experiments) throws IOException {
        String reportFile = RESULTS_DIR + "final_report.txt";

        try (FileWriter fw = new FileWriter(reportFile)) {
            fw.write("=================================================\n");
            fw.write("   WATDIV BENCHMARK - FINAL REPORT\n");
            fw.write("=================================================\n\n");

            for (BenchmarkExperiment exp : experiments) {
                int totalQueries = exp.results.size();
                int zeroAnswers = (int) exp.results.stream().filter(r -> r.oracleResultCount == 0).count();
                int correctCount = (int) exp.results.stream().filter(r -> r.correct).count();
                int completeCount = (int) exp.results.stream().filter(r -> r.complete).count();
                long totalMyTime = exp.results.stream().mapToLong(r -> r.myTime).sum();
                long totalOracleTime = exp.results.stream().mapToLong(r -> r.oracleTime).sum();

                fw.write(String.format("Experiment: %s triples / %s queries%n",
                        exp.datasetName, exp.querySetName));
                fw.write(String.format("  Dataset size: %d triples%n", exp.tripleCount));
                fw.write(String.format("  Load time: %d ms%n", exp.loadTime));
                fw.write(String.format("  Store init time: %d ms%n", exp.storeInitTime));
                fw.write(String.format("  Queries evaluated: %d%n", totalQueries));
                fw.write(String.format("  Queries with 0 answers: %d (%.1f%%)%n",
                        zeroAnswers, totalQueries > 0 ? 100.0 * zeroAnswers / totalQueries : 0));
                fw.write(String.format("  Correct queries: %d/%d (%.1f%%)%n",
                        correctCount, totalQueries, totalQueries > 0 ? 100.0 * correctCount / totalQueries : 0));
                fw.write(String.format("  Complete queries: %d/%d (%.1f%%)%n",
                        completeCount, totalQueries, totalQueries > 0 ? 100.0 * completeCount / totalQueries : 0));
                fw.write(String.format("  Total execution time: %d ms (my) vs %d ms (oracle)%n",
                        totalMyTime, totalOracleTime));
                fw.write(String.format("  Average speedup: %.2fx%n",
                        totalMyTime > 0 ? (double) totalOracleTime / totalMyTime : 0));
                fw.write(String.format("  Avg time per query: %.2f ms (my) vs %.2f ms (oracle)%n%n",
                        totalQueries > 0 ? (double) totalMyTime / totalQueries : 0,
                        totalQueries > 0 ? (double) totalOracleTime / totalQueries : 0));
            }

            // Histogramme des résultats par expérience
            fw.write("\n=== Answer Distribution ===\n");
            for (BenchmarkExperiment exp : experiments) {
                fw.write(String.format("%nExperiment: %s / %s:%n", exp.datasetName, exp.querySetName));
                Map<Integer, Long> distribution = exp.results.stream()
                        .collect(Collectors.groupingBy(
                                r -> Math.min(r.oracleResultCount / 10, 10),
                                Collectors.counting()));

                for (int i = 0; i <= 10; i++) {
                    int count = distribution.getOrDefault(i, 0L).intValue();
                    String range = (i == 10) ? "100+" : (i*10) + "-" + ((i+1)*10-1);
                    fw.write(String.format("  %s answers: %d queries%n", range, count));
                }
            }
        }

        System.out.println("\n=================================================");
        System.out.println("   BENCHMARK COMPLETE");
        System.out.println("=================================================");
        System.out.println("Results saved in: " + RESULTS_DIR);
        System.out.println("Final report: " + reportFile);
    }

    // Classes internes
    private record QueryFile(String filename, StarQuery starQuery, boolean isStarQuery) { }

    private record QueryResult(int queryNumber, String queryFilename, StarQuery starQuery,
                               int myResultCount, int oracleResultCount,
                               long myTime, long oracleTime, boolean correct, boolean complete) { }

    private record BenchmarkExperiment(String datasetName, String querySetName, int tripleCount,
                                       long loadTime, long storeInitTime,
                                       List<QueryResult> results) { }
}