package qengine.storage.colleague;

import fr.boreal.model.logicalElements.api.*;
import fr.boreal.model.logicalElements.factory.api.TermFactory;
import fr.boreal.model.logicalElements.factory.impl.SameObjectTermFactory;
import fr.boreal.model.logicalElements.impl.SubstitutionImpl;
import qengine.model.RDFTriple;

import java.util.*;

public class RDFGiantTable implements qengine.storage.RDFStorage {

    private final Dictionary dict = new Dictionary();
    private final TermFactory termFactory = SameObjectTermFactory.instance();

    // Stockage des triplets encodés : [s, p, o]
    private final List<int[]> table = new ArrayList<>();

    @Override
    public boolean add(RDFTriple triple) {
        int s = dict.encode(triple.getTripleSubject().label());
        int p = dict.encode(triple.getTriplePredicate().label());
        int o = dict.encode(triple.getTripleObject().label());
        table.add(new int[]{s, p, o});
        return true;
    }

    @Override
    public long size() {
        return table.size();
    }

    @Override
    public Iterator<Substitution> match(RDFTriple triple) {
        List<Substitution> results = new ArrayList<>();
        for (int[] row : table) {
            Map<Variable, Term> binding = new HashMap<>();
            if (matches(triple.getTripleSubject(), row[0], binding) &&
                    matches(triple.getTriplePredicate(), row[1], binding) &&
                    matches(triple.getTripleObject(), row[2], binding)) {

                SubstitutionImpl s = new SubstitutionImpl();
                binding.forEach(s::add);
                results.add(s);
            }
        }
        return results.iterator();
    }

    @Override
    public long howMany(RDFTriple triple) {
        long count = 0;
        Iterator<Substitution> it = match(triple);
        while (it.hasNext()) {
            it.next();
            count++;
        }
        return count;
    }

    @Override
    public Collection<RDFTriple> getAtoms() {
        List<RDFTriple> atoms = new ArrayList<>();
        for (int[] row : table) {
            atoms.add(new RDFTriple(
                    termFactory.createOrGetLiteral(dict.decode(row[0])),
                    termFactory.createOrGetLiteral(dict.decode(row[1])),
                    termFactory.createOrGetLiteral(dict.decode(row[2]))
            ));
        }
        return atoms;
    }

    private boolean matches(Term term, int id, Map<Variable, Term> binding) {
        if (term instanceof Variable v) {
            Term existing = binding.get(v);
            Term actual = termFactory.createOrGetLiteral(dict.decode(id));
            if (existing != null) return existing.equals(actual);
            binding.put(v, actual);
            return true;
        } else {
            return term.label().equals(dict.decode(id));
        }
    }
}