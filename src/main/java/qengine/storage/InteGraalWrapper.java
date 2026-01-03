package qengine.storage;

import fr.boreal.model.formula.api.FOFormula;
import fr.boreal.model.formula.api.FOFormulaConjunction;
import fr.boreal.model.kb.api.FactBase;
import fr.boreal.model.logicalElements.api.Substitution;
import fr.boreal.model.query.api.FOQuery;
import fr.boreal.model.queryEvaluation.api.FOQueryEvaluator;
import fr.boreal.query_evaluation.generic.GenericFOQueryEvaluator;
import fr.boreal.storage.natives.SimpleInMemoryGraphStore;
import qengine.model.RDFTriple;
import qengine.model.StarQuery;

import java.util.*;

/**
 * Wrapper for InteGraal that implements RDFStorage interface.
 *
 * This allows InteGraal to be benchmarked using the same measurement protocol as
 * custom implementations, ensuring fair comparison.
 *
 * Note: InteGraal natively uses a different API (FactBase + FOQuery), so this wrapper
 * converts between the APIs to enable unified benchmarking.
 */
public class InteGraalWrapper implements RDFStorage {

    private final FactBase store;
    private final FOQueryEvaluator<FOFormula> evaluator;

    public InteGraalWrapper() {
        this.store = new SimpleInMemoryGraphStore();
        this.evaluator = GenericFOQueryEvaluator.defaultInstance();
    }

    @Override
    public boolean add(RDFTriple triple) {
        return store.add(triple);
    }

    @Override
    public Iterator<Substitution> match(RDFTriple triple) {
        try {
            // Create a simple query with one triple pattern
            List<RDFTriple> triples = Collections.singletonList(triple);

            // Extract all variables from the triple as answer variables
            Set<fr.boreal.model.logicalElements.api.Variable> answerVars = new HashSet<>();
            for (fr.boreal.model.logicalElements.api.Term term : triple.getTerms()) {
                if (term instanceof fr.boreal.model.logicalElements.api.Variable) {
                    answerVars.add((fr.boreal.model.logicalElements.api.Variable) term);
                }
            }

            // Convert to FOQuery
            fr.boreal.model.formula.api.FOFormulaConjunction conjunction =
                fr.boreal.model.formula.factory.FOFormulaFactory.instance().createOrGetConjunction(triples);
            FOQuery<fr.boreal.model.formula.api.FOFormulaConjunction> foQuery =
                fr.boreal.model.query.factory.FOQueryFactory.instance()
                    .createOrGetQuery("single_triple_query", conjunction, answerVars);

            // Evaluate using InteGraal's evaluator
            Iterator<Substitution> results = evaluator.evaluate(foQuery, store);
            return results;

        } catch (Exception e) {
            System.err.println("Error evaluating single triple with InteGraal: " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyIterator();
        }
    }

    @Override
    public Iterator<Substitution> match(StarQuery query) {
        try {
            // Convert StarQuery to InteGraal's FOQuery format
            FOQuery<FOFormulaConjunction> foQuery = query.asFOQuery();

            // Evaluate using InteGraal's evaluator
            Iterator<Substitution> results = evaluator.evaluate(foQuery, store);

            // Return results
            return results;

        } catch (Exception e) {
            System.err.println("Error evaluating query with InteGraal: " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyIterator();
        }
    }

    @Override
    public long howMany(RDFTriple triple) {
        // Count results by iterating
        long count = 0;
        Iterator<Substitution> iter = match(triple);
        while (iter.hasNext()) {
            iter.next();
            count++;
        }
        return count;
    }

    @Override
    public long size() {
        return store.size();
    }

    @Override
    public Collection<RDFTriple> getAtoms() {
        // InteGraal's FactBase doesn't provide direct iteration over facts
        return new ArrayList<>();
    }
}
