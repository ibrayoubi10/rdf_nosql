package qengine.storage;

import fr.boreal.model.logicalElements.api.*;
import fr.boreal.model.logicalElements.factory.impl.SameObjectTermFactory;
import fr.boreal.model.logicalElements.impl.SubstitutionImpl;
import qengine.model.RDFTriple;
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
        List<Substitution> results = new ArrayList<>();

        Term s = triple.getTripleSubject();
        Term p = triple.getTriplePredicate();
        Term o = triple.getTripleObject();

        boolean sVar = s.isVariable();
        boolean pVar = p.isVariable();
        boolean oVar = o.isVariable();

        // Encode constants only (check if they exist in the dictionary)
        Integer sId = sVar ? null : dict.getIdOrNull(s.toString());
        Integer pId = pVar ? null : dict.getIdOrNull(p.toString());
        Integer oId = oVar ? null : dict.getIdOrNull(o.toString());

        // If any constant is not in dictionary, no results possible
        if ((!sVar && sId == null) || (!pVar && pId == null) || (!oVar && oId == null)) {
            return Collections.emptyIterator();
        }

        // Case 0: No variables (s, p, o) - exact match check
        if (!sVar && !pVar && !oVar) {
            boolean exists = indexSPO.containsKey(sId)
                    && indexSPO.get(sId).containsKey(pId)
                    && indexSPO.get(sId).get(pId).contains(oId);
            if (exists) {
                results.add(new SubstitutionImpl(new HashMap<>()));
            }
            return results.iterator();
        }

        // Case 1: Only an object is variable (s, p, ?o) - use SPO
        if (!sVar && !pVar && oVar) {
            if (indexSPO.containsKey(sId) && indexSPO.get(sId).containsKey(pId)) {
                for (Integer foundO : indexSPO.get(sId).get(pId)) {
                    results.add(createSubstitution((Variable) o, foundO));
                }
            }
            return results.iterator();
        }

        // Case 2: Only predicate is variable (s, ?p, o) - use SOP
        if (!sVar && pVar && !oVar) {
            if (indexSOP.containsKey(sId) && indexSOP.get(sId).containsKey(oId)) {
                for (Integer foundP : indexSOP.get(sId).get(oId)) {
                    results.add(createSubstitution((Variable) p, foundP));
                }
            }
            return results.iterator();
        }

        // Case 3: Only subject is variable (?s, p, o) - use POS
        if (sVar && !pVar && !oVar) {
            if (indexPOS.containsKey(pId) && indexPOS.get(pId).containsKey(oId)) {
                for (Integer foundS : indexPOS.get(pId).get(oId)) {
                    results.add(createSubstitution((Variable) s, foundS));
                }
            }
            return results.iterator();
        }

        // Case 4: Subject and predicate are variables (?s, ?p, o) - use OPS
        if (sVar && pVar && !oVar) {
            if (indexOPS.containsKey(oId)) {
                for (Map.Entry<Integer, Set<Integer>> pEntry : indexOPS.get(oId).entrySet()) {
                    Integer foundP = pEntry.getKey();
                    for (Integer foundS : pEntry.getValue()) {
                        results.add(createSubstitution((Variable) s, foundS, (Variable) p, foundP));
                    }
                }
            }
            return results.iterator();
        }

        // Case 5: Subject and object are variables (?s, p, ?o) - use PSO
        if (sVar && !pVar && oVar) {
            if (indexPSO.containsKey(pId)) {
                for (Map.Entry<Integer, Set<Integer>> sEntry : indexPSO.get(pId).entrySet()) {
                    Integer foundS = sEntry.getKey();
                    for (Integer foundO : sEntry.getValue()) {
                        results.add(createSubstitution((Variable) s, foundS, (Variable) o, foundO));
                    }
                }
            }
            return results.iterator();
        }

        // Case 6: Predicate and object are variables (s, ?p, ?o) - use SPO
        if (!sVar && pVar && oVar) {
            if (indexSPO.containsKey(sId)) {
                for (Map.Entry<Integer, Set<Integer>> pEntry : indexSPO.get(sId).entrySet()) {
                    Integer foundP = pEntry.getKey();
                    for (Integer foundO : pEntry.getValue()) {
                        results.add(createSubstitution((Variable) p, foundP, (Variable) o, foundO));
                    }
                }
            }
            return results.iterator();
        }

        // Case 7: All variables (?s, ?p, ?o) - full scan using SPO
        if (sVar && pVar && oVar) {
            for (Map.Entry<Integer, Map<Integer, Set<Integer>>> sEntry : indexSPO.entrySet()) {
                Integer foundS = sEntry.getKey();
                for (Map.Entry<Integer, Set<Integer>> pEntry : sEntry.getValue().entrySet()) {
                    Integer foundP = pEntry.getKey();
                    for (Integer foundO : pEntry.getValue()) {
                        results.add(createSubstitution(
                                (Variable) s, foundS,
                                (Variable) p, foundP,
                                (Variable) o, foundO
                        ));
                    }
                }
            }
            return results.iterator();
        }

        return results.iterator();
    }

    // Helper: create a substitution with 1 binding
    private Substitution createSubstitution(Variable var, Integer encodedValue) {
        Map<Variable, Term> map = new HashMap<>();
        Term term = SameObjectTermFactory.instance().createOrGetLiteral(dict.decode(encodedValue));
        map.put(var, term);
        return new SubstitutionImpl(map);
    }

    // Helper: create a substitution with 2 bindings
    private Substitution createSubstitution(Variable var1, Integer val1, Variable var2, Integer val2) {
        Map<Variable, Term> map = new HashMap<>();
        map.put(var1, SameObjectTermFactory.instance().createOrGetLiteral(dict.decode(val1)));
        map.put(var2, SameObjectTermFactory.instance().createOrGetLiteral(dict.decode(val2)));
        return new SubstitutionImpl(map);
    }

    // Helper: create a substitution with 3 bindings
    private Substitution createSubstitution(Variable var1, Integer val1,
                                            Variable var2, Integer val2,
                                            Variable var3, Integer val3) {
        Map<Variable, Term> map = new HashMap<>();
        map.put(var1, SameObjectTermFactory.instance().createOrGetLiteral(dict.decode(val1)));
        map.put(var2, SameObjectTermFactory.instance().createOrGetLiteral(dict.decode(val2)));
        map.put(var3, SameObjectTermFactory.instance().createOrGetLiteral(dict.decode(val3)));
        return new SubstitutionImpl(map);
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
