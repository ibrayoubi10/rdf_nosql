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

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public final class Example {

	private static final String WORKING_DIR = "data/";
	private static final String SAMPLE_DATA_FILE = WORKING_DIR + "sample_data.nt";
	private static final String SAMPLE_QUERY_FILE = WORKING_DIR + "sample_query.queryset";
    private static final String RESULTS_DIR = "results/";

	public static void main(String[] args) throws IOException {
		/*
		 * Exemple d'utilisation des deux parsers
		 */
		System.out.println("=== Parsing RDF Data ===");
		List<RDFTriple> rdfAtoms = parseRDFData(SAMPLE_DATA_FILE);

		System.out.println("\n=== Parsing Sample Queries ===");
		List<StarQuery> starQueries = parseSparQLQueries(SAMPLE_QUERY_FILE);

        // FactBase pour votre moteur et pour l'oracle InteGraal
        FactBase myStore = new SimpleInMemoryGraphStore();
        FactBase oracleStore = new SimpleInMemoryGraphStore();
        for (RDFTriple triple : rdfAtoms) {
            myStore.add(triple);
            oracleStore.add(triple);
        }

        FOQueryEvaluator<FOFormula> evaluator = GenericFOQueryEvaluator.defaultInstance();

		// Exécuter les requêtes sur le store
        for (int i = 0; i < starQueries.size(); i++) {
            StarQuery starQuery = starQueries.get(i);
            FOQuery<FOFormulaConjunction> foQuery = starQuery.asFOQuery();

            // Évaluation
            Set<Substitution> myResults = iteratorToSet(evaluator.evaluate(foQuery, myStore));
            Set<Substitution> oracleResults = iteratorToSet(evaluator.evaluate(foQuery, oracleStore));

            // Vérification correction et complétude
            boolean correct = oracleResults.containsAll(myResults);
            boolean complete = myResults.containsAll(oracleResults);

            // Export
            exportResults(i + 1, starQuery, myResults, oracleResults, correct, complete);
        }
	}

	/**
	 * Parse et affiche le contenu d'un fichier RDF.
	 *
	 * @param rdfFilePath Chemin vers le fichier RDF à parser
	 * @return Liste des RDFAtoms parsés
	 */
	private static List<RDFTriple> parseRDFData(String rdfFilePath) throws IOException {
		FileReader rdfFile = new FileReader(rdfFilePath);
		List<RDFTriple> rdfAtoms = new ArrayList<>();

		try (RDFTriplesParser rdfAtomParser = new RDFTriplesParser(rdfFile, RDFFormat.NTRIPLES)) {
			int count = 0;
			while (rdfAtomParser.hasNext()) {
				RDFTriple triple = rdfAtomParser.next();
				rdfAtoms.add(triple);  // Stocker le triplet dans la collection
				System.out.println("RDF Triple #" + (++count) + ": " + triple);
			}
			System.out.println("Total RDF Triples parsed: " + count);
		}
		return rdfAtoms;
	}

	/**
	 * Parse et affiche le contenu d'un fichier de requêtes SparQL.
	 *
	 * @param queryFilePath Chemin vers le fichier de requêtes SparQL
	 * @return Liste des StarQueries parsées
	 */
	private static List<StarQuery> parseSparQLQueries(String queryFilePath) throws IOException {
		List<StarQuery> starQueries = new ArrayList<>();

		try (StarQuerySparQLParser queryParser = new StarQuerySparQLParser(queryFilePath)) {
			int queryCount = 0;

			while (queryParser.hasNext()) {
				Query query = queryParser.next();
				if (query instanceof StarQuery starQuery) {
					starQueries.add(starQuery);  // Stocker la requête dans la collection
					System.out.println("Star Query #" + (++queryCount) + ":");
					System.out.println("  Central Variable: " + starQuery.getCentralVariable().label());
					System.out.println("  RDF Atoms:");
					starQuery.getRdfAtoms().forEach(triple -> System.out.println("    " + triple));
				} else {
					System.err.println("Requête inconnue ignorée.");
				}
			}
			System.out.println("Total Queries parsed: " + starQueries.size());
		}
		return starQueries;
	}

    /**
     * Convertit un Iterator de Substitution en un Set pour faciliter la comparaison.
     *
     * @param iter Iterator de Substitution à convertir
     * @return Set contenant tous les éléments de l'iterator
     */
    private static Set<Substitution> iteratorToSet(Iterator<Substitution> iter) {
        Set<Substitution> set = new HashSet<>();
        iter.forEachRemaining(set::add);
        return set;
    }

    /**
     * Exporte les résultats d'une requête en étoile dans un fichier texte minimal.
     *
     * @param queryIndex Index de la requête
     * @param starQuery La requête en étoile évaluée
     * @param myResults Ensemble de résultats obtenus par le système
     * @param oracleResults Ensemble de résultats de l'oracle
     * @param correct Indique si les résultats sont corrects
     * @param complete Indique si les résultats sont complets
     * @throws IOException En cas d'erreur lors de l'écriture du fichier
     */
    private static void exportResults(int queryIndex, StarQuery starQuery,
                                      Set<Substitution> myResults, Set<Substitution> oracleResults,
                                      boolean correct, boolean complete) throws IOException {
        try (FileWriter fw = new FileWriter(RESULTS_DIR + "query" + queryIndex + "_results.txt")) {
            fw.write("StarQuery: " + starQuery + "\n\n");
            fw.write("=== My Results ===\n");
            for (Substitution s : myResults) fw.write(s + "\n");

            fw.write("\n=== Oracle Results ===\n");
            for (Substitution s : oracleResults) fw.write(s + "\n");

            fw.write("\n=== Comparison ===\n");
            fw.write("Correct: " + correct + "\n");
            fw.write("Complete: " + complete + "\n");
        }
        System.out.printf("Query %d exported. Correct: %b, Complete: %b%n", queryIndex, correct, complete);
    }
}
