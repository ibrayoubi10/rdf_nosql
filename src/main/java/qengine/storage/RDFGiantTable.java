package qengine.storage;

import fr.boreal.model.logicalElements.api.Literal;
import fr.boreal.model.logicalElements.api.Substitution;
import fr.boreal.model.logicalElements.api.Term;
import fr.boreal.model.logicalElements.api.Variable;
import fr.boreal.model.logicalElements.factory.impl.SameObjectTermFactory;
import fr.boreal.model.logicalElements.impl.SubstitutionImpl;
import qengine.model.RDFTriple;

import java.util.*;

/**
 * Implémentation simple de RDFStorage :
 * un stockage "Giant Table" sans aucun index, basé uniquement sur une liste
 * des triplets encodés.
 */
public class RDFGiantTable implements RDFStorage {

    private final RDFDictionary dict = new RDFDictionary();

    private final List<int[]> table = new ArrayList<>();

    @Override
    public boolean add(RDFTriple triple) {

        int s = dict.encode(triple.getTripleSubject().toString());
        int p = dict.encode(triple.getTriplePredicate().toString());
        int o = dict.encode(triple.getTripleObject().toString());

        int[] encoded = new int[]{s, p, o};

        // éviter les doublons
        for (int[] t : table) {
            if (t[0] == s && t[1] == p && t[2] == o)
                return false;
        }

        table.add(encoded);
        return true;
    }

    @Override
    public long size() {
        return table.size();
    }

    @Override
    public Iterator<Substitution> match(RDFTriple triple) {

        Term s = triple.getTripleSubject();
        Term p = triple.getTriplePredicate();
        Term o = triple.getTripleObject();

        boolean vs = s.isVariable();
        boolean vp = p.isVariable();
        boolean vo = o.isVariable();

        Integer sId = vs ? null : dict.encode(s.toString());
        Integer pId = vp ? null : dict.encode(p.toString());
        Integer oId = vo ? null : dict.encode(o.toString());

        List<Substitution> results = new ArrayList<>();

        Variable VS = vs ? SameObjectTermFactory.instance().createOrGetVariable(s.toString()) : null;
        Variable VP = vp ? SameObjectTermFactory.instance().createOrGetVariable(p.toString()) : null;
        Variable VO = vo ? SameObjectTermFactory.instance().createOrGetVariable(o.toString()) : null;

        for (int[] t : table) {
            int ts = t[0], tp = t[1], to = t[2];

            // vérifier les constantes
            if (!vs && ts != sId) continue;
            if (!vp && tp != pId) continue;
            if (!vo && to != oId) continue;

            Map<Variable, Term> map = new HashMap<>();

            if (vs) map.put(VS, literal(ts));
            if (vp) map.put(VP, literal(tp));
            if (vo) map.put(VO, literal(to));

            results.add(new SubstitutionImpl(map));
        }

        return results.iterator();
    }

    /** Helper pour décoder un entier en Literal */
    private Literal<String> literal(int id) {
        return SameObjectTermFactory.instance().createOrGetLiteral(dict.decode(id));
    }

    @Override
    public long howMany(RDFTriple triple) {

        Term s = triple.getTripleSubject();
        Term p = triple.getTriplePredicate();
        Term o = triple.getTripleObject();

        boolean vs = s.isVariable();
        boolean vp = p.isVariable();
        boolean vo = o.isVariable();

        Integer sId = vs ? null : dict.encode(s.toString());
        Integer pId = vp ? null : dict.encode(p.toString());
        Integer oId = vo ? null : dict.encode(o.toString());

        long count = 0;

        for (int[] t : table) {
            int ts = t[0], tp = t[1], to = t[2];

            if (!vs && ts != sId) continue;
            if (!vp && tp != pId) continue;
            if (!vo && to != oId) continue;

            count++;
        }

        return count;
    }

    @Override
    public Collection<RDFTriple> getAtoms() {
        Collection<RDFTriple> res = new ArrayList<>();

        for (int[] t : table) {
            int s = t[0], p = t[1], o = t[2];

            Literal<String> subject = literal(s);
            Literal<String> predicate = literal(p);
            Literal<String> object = literal(o);

            res.add(new RDFTriple(subject, predicate, object));
        }
        return res;
    }
}
