package qengine.storage;

import fr.boreal.model.logicalElements.api.Substitution;
import qengine.model.RDFTriple;
import qengine.model.StarQuery;

import java.util.Collection;
import java.util.Iterator;

/**
 * Wrapper for Colleague's RDFHexaStore implementation that uses a different interface.
 *
 * This allows the colleague implementation to be benchmarked using the same
 * measurement protocol as other systems, ensuring fair comparison.
 */
public class ColleagueWrapper implements RDFStorage {

    private final qengine.storage.colleague.RDFStorage store;

    public ColleagueWrapper() {
        this.store = new qengine.storage.colleague.RDFHexaStore();
    }

    @Override
    public boolean add(RDFTriple triple) {
        return store.add(triple);
    }

    @Override
    public Iterator<Substitution> match(RDFTriple triple) {
        return store.match(triple);
    }

    @Override
    public Iterator<Substitution> match(StarQuery query) {
        return store.match(query);
    }

    @Override
    public long howMany(RDFTriple triple) {
        return store.howMany(triple);
    }

    @Override
    public long size() {
        return store.size();
    }

    @Override
    public Collection<RDFTriple> getAtoms() {
        return store.getAtoms();
    }
}
