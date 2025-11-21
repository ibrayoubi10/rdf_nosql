package qengine.storage;

import fr.boreal.model.logicalElements.api.*;
import fr.boreal.model.logicalElements.factory.impl.SameObjectTermFactory;
import fr.boreal.model.logicalElements.impl.SubstitutionImpl;
import org.apache.commons.lang3.NotImplementedException;
import qengine.model.RDFTriple;
import qengine.model.StarQuery;

import java.util.*;

/**
 * Implémentation d'un HexaStore pour stocker des RDFAtom.
 * Cette classe utilise six index pour optimiser les recherches.
 * Les index sont basés sur les combinaisons (Sujet, Prédicat, Objet), (Sujet, Objet, Prédicat),
 * (Prédicat, Sujet, Objet), (Prédicat, Objet, Sujet), (Objet, Sujet, Prédicat) et (Objet, Prédicat, Sujet).
 */
public class RDFHexaStore implements RDFStorage {
    // Dictionary
    RDFDictionary dict = new RDFDictionary();
    // Indexes
    Map<Integer, Map<Integer, Set<Integer>>> indexSPO = new HashMap<>();
    Map<Integer, Map<Integer, Set<Integer>>> indexSOP = new HashMap<>();
    Map<Integer, Map<Integer, Set<Integer>>> indexPSO = new HashMap<>();
    Map<Integer, Map<Integer, Set<Integer>>> indexPOS = new HashMap<>();
    Map<Integer, Map<Integer, Set<Integer>>> indexOSP = new HashMap<>();
    Map<Integer, Map<Integer, Set<Integer>>> indexOPS = new HashMap<>();
    // Statistics
    Map<Integer, Integer> countS = new HashMap<>();
    Map<Integer, Integer> countP = new HashMap<>();
    Map<Integer, Integer> countO = new HashMap<>();
    Map<Integer, Map<Integer, Integer>> countSP = new HashMap<>();
    Map<Integer, Map<Integer, Integer>> countSO = new HashMap<>();
    Map<Integer, Map<Integer, Integer>> countPO = new HashMap<>();


    @Override
    public boolean add(RDFTriple triple) {
    // 1. Encode the triple terms using the dictionary
    int s = dict.encode(triple.getTripleSubject().toString());
    int p = dict.encode(triple.getTriplePredicate().toString());
    int o = dict.encode(triple.getTripleObject().toString());

    // 2. Check if triple already exists (check one index is enough)
    if (indexSPO.containsKey(s)) {
        Map<Integer, Set<Integer>> pMap = indexSPO.get(s);
        if (pMap.containsKey(p) && pMap.get(p).contains(o)) {
            return false; // Already exists
        }
    }

    // 3. Insert into all 6 indexes
    addToIndex(indexSPO, s, p, o);  // SPO
    addToIndex(indexSOP, s, o, p);  // SOP
    addToIndex(indexPSO, p, s, o);  // PSO
    addToIndex(indexPOS, p, o, s);  // POS
    addToIndex(indexOSP, o, s, p);  // OSP
    addToIndex(indexOPS, o, p, s);  // OPS

    // 4. Update statistics (for selectivity)
        countS.merge(s, 1, Integer::sum);
        countP.merge(p, 1, Integer::sum);
        countO.merge(o, 1, Integer::sum);
        countSP.computeIfAbsent(s, k -> new HashMap<>()).merge(p, 1, Integer::sum);
        countSO.computeIfAbsent(s, k -> new HashMap<>()).merge(o, 1, Integer::sum);
        countPO.computeIfAbsent(p, k -> new HashMap<>()).merge(o, 1, Integer::sum);

        return true;
}

    // Helper method to add to a specific index
    private void addToIndex(Map<Integer, Map<Integer, Set<Integer>>> index,
                            int key1, int key2, int key3) {
        index.computeIfAbsent(key1, k -> new HashMap<>())
             .computeIfAbsent(key2, k -> new HashSet<>())
             .add(key3);
    }

    @Override
    public long size() {
        long res = 0;
        for (Map<Integer, Set<Integer>> pMap : indexSPO.values()) {
            for (Set<Integer> oSet : pMap.values()) {
                res += oSet.size();
            }
        }
        return res;
    }

    @Override
    public Iterator<Substitution> match(RDFTriple triple) {
        Map<String, Integer> constants = new HashMap<>();
        Iterator<Substitution> subs;
        Term s = triple.getTripleSubject();
        Term p = triple.getTriplePredicate();
        Term o = triple.getTripleObject();

        // Count variables
        int varCount = 0;
        if (s.isVariable()) varCount++;
        if (p.isVariable()) varCount++;
        if (o.isVariable()) varCount++;

        // Case 0: No variables - exact match
        if (varCount == 0) {
            // Encode the terms
            int sId = dict.encode(s.toString());
            int pId = dict.encode(p.toString());
            int oId = dict.encode(o.toString());

            // Check if the exact triple exists in the store
            boolean exists = indexSPO.containsKey(sId)
                    && indexSPO.get(sId).containsKey(pId)
                    && indexSPO.get(sId).get(pId).contains(oId);

            if (exists) {
                // Return an iterator with ONE empty substitution because there are no variables to bind
                Substitution emptySub = new SubstitutionImpl(new HashMap<>());
                subs = Collections.singletonList(emptySub).iterator();
            } else {
                // Return an empty iterator (no matches)
                subs = Collections.emptyIterator();
            }
        }

        // Case 1: One variable
        else if (varCount == 1) {
            if (s.isVariable()) {
                constants.put("p", dict.encode(p.toString()));
                constants.put("o", dict.encode(o.toString()));
                subs = useIndex(indexPOS, constants);
            } else if (p.isVariable()) {
                constants.put("s", dict.encode(s.toString()));
                constants.put("o", dict.encode(o.toString()));
                subs = useIndex(indexSOP, constants);
            } else {
                constants.put("s", dict.encode(s.toString()));
                constants.put("p", dict.encode(p.toString()));
                subs = useIndex(indexSPO, constants);
            }
        }

        // Case 2: Two variables
        else if (varCount == 2) {
            if (s.isVariable() && p.isVariable()) {
                constants.put("o", dict.encode(o.toString()));
                subs = useIndex(indexOSP, constants);
            } else if (s.isVariable() && o.isVariable()) {
                constants.put("p", dict.encode(p.toString()));
                subs = useIndex(indexPOS, constants);
            } else {
                constants.put("s", dict.encode(s.toString()));
                subs = useIndex(indexSPO, constants);
            }
        }

        // Case 3: Three variables
        else {
            subs = useIndex(indexSPO, constants);
        }

        return subs;
    }

    // Helper method to match a triple against the indexes
    public Iterator<Substitution> useIndex(Map<Integer, Map<Integer, Set<Integer>>> index, Map<String, Integer> constants) {
        List<Substitution> results = new ArrayList<>();
        final Variable VAR_S = SameObjectTermFactory.instance().createOrGetVariable("?s");
        final Variable VAR_P = SameObjectTermFactory.instance().createOrGetVariable("?p");
        final Variable VAR_O = SameObjectTermFactory.instance().createOrGetVariable("?o");

        if (constants.size() > 3) {
            throw new IllegalArgumentException("Invalid number of constants");
        }

        // CASE 0: No constants provided
        if (constants.isEmpty()) {
            for (Map.Entry<Integer, Map<Integer, Set<Integer>>> sEntry : index.entrySet()) {
                int sId = sEntry.getKey();
                for (Map.Entry<Integer, Set<Integer>> pEntry : sEntry.getValue().entrySet()) {
                    int pId = pEntry.getKey();
                    for (int oId : pEntry.getValue()) {
                        Map<Variable, Term> map = new HashMap<>();

                        Term sTerm = SameObjectTermFactory.instance().createOrGetLiteral(dict.decode(sId));
                        Term pTerm = SameObjectTermFactory.instance().createOrGetLiteral(dict.decode(pId));
                        Term oTerm = SameObjectTermFactory.instance().createOrGetLiteral(dict.decode(oId));

                        map.put(VAR_S, sTerm);
                        map.put(VAR_P, pTerm);
                        map.put(VAR_O, oTerm);

                        results.add(new SubstitutionImpl(map));
                    }
                }
            }
        }

        // CASE 1: One constant provided
        if (constants.size() == 1) {
            if (constants.containsKey("s")) {
                int sId = constants.get("s");
                if (index.containsKey(sId)) {
                    for (Map.Entry<Integer, Set<Integer>> pEntry : index.get(sId).entrySet()) {
                        int pId = pEntry.getKey();
                        for (int oId : pEntry.getValue()) {
                            Map<Variable, Term> map = new HashMap<>();

                            Term pTerm = SameObjectTermFactory.instance().createOrGetLiteral(dict.decode(pId));
                            Term oTerm = SameObjectTermFactory.instance().createOrGetLiteral(dict.decode(oId));

                            map.put(VAR_P, pTerm);
                            map.put(VAR_O, oTerm);

                            results.add(new SubstitutionImpl(map));
                        }
                    }
                }
            } else if (constants.containsKey("p")) {
                int pId = constants.get("p");
                if (index.containsKey(pId)) {
                    for (Map.Entry<Integer, Set<Integer>> oEntry : index.get(pId).entrySet()) {
                        int oId = oEntry.getKey();
                        for (int sId : oEntry.getValue()) {
                            Map<Variable, Term> map = new HashMap<>();

                            Term oTerm = SameObjectTermFactory.instance().createOrGetLiteral(dict.decode(oId));
                            Term sTerm = SameObjectTermFactory.instance().createOrGetLiteral(dict.decode(sId));

                            map.put(VAR_O, oTerm);
                            map.put(VAR_S, sTerm);

                            results.add(new SubstitutionImpl(map));
                        }
                    }
                }
            } else if (constants.containsKey("o")) {
                int oId = constants.get("o");
                for (Map.Entry<Integer, Map<Integer, Set<Integer>>> sEntry : index.entrySet()) {
                    int sId = sEntry.getKey();
                    for (Map.Entry<Integer, Set<Integer>> pEntry : sEntry.getValue().entrySet()) {
                        int pId = pEntry.getKey();
                        if (pEntry.getValue().contains(oId)) {
                            Map<Variable, Term> map = new HashMap<>();

                            Term sTerm = SameObjectTermFactory.instance().createOrGetLiteral(dict.decode(sId));
                            Term pTerm = SameObjectTermFactory.instance().createOrGetLiteral(dict.decode(pId));

                            map.put(VAR_S, sTerm);
                            map.put(VAR_P, pTerm);

                            results.add(new SubstitutionImpl(map));
                        }
                    }
                }
            }
        }

        // CASE 2: Two constants provided
        else if (constants.size() == 2) {
            Integer sId = constants.get("s");
            Integer pId = constants.get("p");
            Integer oId = constants.get("o");

            // Known s, p
            if (sId != null && pId != null) {
                if (index.containsKey(sId)
                        && index.get(sId).containsKey(pId)) {
                    for (int o : index.get(sId).get(pId)) {
                        Map<Variable, Term> map = new HashMap<>();

                        Term oTerm = SameObjectTermFactory.instance().createOrGetLiteral(dict.decode(o));

                        map.put(VAR_O, oTerm);

                        results.add(new SubstitutionImpl(map));
                    }
                }
            }

            // Known s, o
            else if (sId != null && oId != null) {
                if (index.containsKey(sId)) {
                    for (Map.Entry<Integer, Set<Integer>> entry : index.get(sId).entrySet()) {
                        int p = entry.getKey();
                        if (entry.getValue().contains(oId)) {
                            Map<Variable, Term> map = new HashMap<>();

                            Term pTerm = SameObjectTermFactory.instance().createOrGetLiteral(dict.decode(p));

                            map.put(VAR_P, pTerm);

                            results.add(new SubstitutionImpl(map));
                        }
                    }
                }
            }

            // Known p, o
            else if (pId != null && oId != null) {
                for (Map.Entry<Integer, Map<Integer, Set<Integer>>> sEntry : index.entrySet()) {
                    int s = sEntry.getKey();
                    if (sEntry.getValue().containsKey(pId)
                            && sEntry.getValue().get(pId).contains(oId)) {
                        Map<Variable, Term> map = new HashMap<>();

                        Term sTerm = SameObjectTermFactory.instance().createOrGetLiteral(dict.decode(s));

                        map.put(VAR_S, sTerm);

                        results.add(new SubstitutionImpl(map));
                    }
                }
            }
        }

        return results.iterator();
    }

    @Override
    public Iterator<Substitution> match(StarQuery q) {
        throw new NotImplementedException();
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

        // Pattern 0 variables = exact triple
        if (!vs && !vp && !vo) {
            return (indexSPO.containsKey(sId)
                    && indexSPO.get(sId).containsKey(pId)
                    && indexSPO.get(sId).get(pId).contains(oId))
                    ? 1 : 0;
        }

        // One variable
        if (vs && !vp && !vo) { // (?s, p, o)
            return countPO.getOrDefault(pId, Collections.emptyMap())
                    .getOrDefault(oId, 0);
        }
        if (!vs && vp && !vo) { // (s, ?p, o)
            return countSO.getOrDefault(sId, Collections.emptyMap())
                    .getOrDefault(oId, 0);
        }
        if (!vs && !vp && vo) { // (s, p, ?o)
            return countSP.getOrDefault(sId, Collections.emptyMap())
                    .getOrDefault(pId, 0);
        }

        // Two variables
        if (vs && vp && !vo) { // (?s, ?p, o)
            return countO.getOrDefault(oId, 0);
        }
        if (vs && !vp && vo) { // (?s, p, ?o)
            return countP.getOrDefault(pId, 0);
        }
        if (!vs && vp && vo) { // (s, ?p, ?o)
            return countS.getOrDefault(sId, 0);
        }

        // Three variables
        return size();
    }

    @Override
    public Collection<RDFTriple> getAtoms() {
        Collection<RDFTriple> res = new ArrayList<>();
        for (Integer s : indexSPO.keySet()) {
            for (Integer p : indexSPO.get(s).keySet()) {
                for (Integer o : indexSPO.get(s).get(p)) {
                    Literal<String> subject = SameObjectTermFactory.instance().createOrGetLiteral(dict.decode(s));
                    Literal<String> predicate = SameObjectTermFactory.instance().createOrGetLiteral(dict.decode(p));
                    Literal<String> object = SameObjectTermFactory.instance().createOrGetLiteral(dict.decode(o));
                    RDFTriple atom = new RDFTriple(subject, predicate, object);
                    res.add(atom);
                }
            }
        }
        return res;
    }
}
