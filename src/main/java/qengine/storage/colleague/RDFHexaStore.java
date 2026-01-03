package qengine.storage.colleague;

import fr.boreal.model.logicalElements.api.*;
import fr.boreal.model.logicalElements.factory.api.TermFactory;
import fr.boreal.model.logicalElements.factory.impl.SameObjectTermFactory;
import fr.boreal.model.logicalElements.impl.SubstitutionImpl;
import qengine.model.RDFTriple;

import java.util.*;

public class RDFHexaStore implements RDFStorage {

    private final Dictionary dict = new Dictionary();
    private final TermFactory factory = SameObjectTermFactory.instance();

    // Six index Hexastore
    private final Map<Integer, Map<Integer, Set<Integer>>> SPO = new HashMap<>();
    private final Map<Integer, Map<Integer, Set<Integer>>> SOP = new HashMap<>();
    private final Map<Integer, Map<Integer, Set<Integer>>> PSO = new HashMap<>();
    private final Map<Integer, Map<Integer, Set<Integer>>> POS = new HashMap<>();
    private final Map<Integer, Map<Integer, Set<Integer>>> OSP = new HashMap<>();
    private final Map<Integer, Map<Integer, Set<Integer>>> OPS = new HashMap<>();

    // Statistiques
    private final Map<String, Long> stats = new HashMap<>();
    private long totalTriples = 0L;

    @Override
    public boolean add(RDFTriple t) {

        int s = dict.encode(t.getTripleSubject().label());
        int p = dict.encode(t.getTriplePredicate().label());
        int o = dict.encode(t.getTripleObject().label());

        // Déjà présent ?
        if (SPO.containsKey(s)
                && SPO.get(s).containsKey(p)
                && SPO.get(s).get(p).contains(o)) {
            return false;
        }

        addTo(SPO, s, p, o);
        addTo(SOP, s, o, p);
        addTo(PSO, p, s, o);
        addTo(POS, p, o, s);
        addTo(OSP, o, s, p);
        addTo(OPS, o, p, s);

        totalTriples++;
        increment("TOTAL");

        // statistiques SP, SO, PO, S, P, O
        increment("SP:" + s + ":" + p);
        increment("SO:" + s + ":" + o);
        increment("PO:" + p + ":" + o);

        increment("S:" + s);
        increment("P:" + p);
        increment("O:" + o);

        return true;
    }

    private void addTo(Map<Integer, Map<Integer, Set<Integer>>> idx,
                       int k1, int k2, int k3) {
        idx.computeIfAbsent(k1, x -> new HashMap<>())
                .computeIfAbsent(k2, x -> new HashSet<>())
                .add(k3);
    }

    private void increment(String key) {
        stats.merge(key, 1L, Long::sum);
    }

    @Override
    public long howMany(RDFTriple t) {

        Term sT = t.getTripleSubject();
        Term pT = t.getTriplePredicate();
        Term oT = t.getTripleObject();

        boolean sVar = sT instanceof Variable;
        boolean pVar = pT instanceof Variable;
        boolean oVar = oT instanceof Variable;

        Integer s = sVar ? null : dict.encode(sT.label());
        Integer p = pVar ? null : dict.encode(pT.label());
        Integer o = oVar ? null : dict.encode(oT.label());

        // 0 variable
        if (!sVar && !pVar && !oVar) {
            return (SPO.containsKey(s)
                    && SPO.get(s).containsKey(p)
                    && SPO.get(s).get(p).contains(o)) ? 1 : 0;
        }

        // 1 variable
        if (sVar && !pVar && !oVar) return stats.getOrDefault("PO:" + p + ":" + o, 0L);
        if (!sVar && pVar && !oVar) return stats.getOrDefault("SO:" + s + ":" + o, 0L);
        if (!sVar && !pVar && oVar) return stats.getOrDefault("SP:" + s + ":" + p, 0L);

        // 2 variables
        if (sVar && pVar && !oVar) return stats.getOrDefault("O:" + o, 0L);
        if (sVar && !pVar && oVar) return stats.getOrDefault("P:" + p, 0L);
        if (!sVar && pVar && oVar) return stats.getOrDefault("S:" + s, 0L);

        // 3 variables
        return totalTriples;
    }

    @Override
    public Iterator<Substitution> match(RDFTriple t) {

        Term sT = t.getTripleSubject();
        Term pT = t.getTriplePredicate();
        Term oT = t.getTripleObject();

        boolean sVar = sT instanceof Variable;
        boolean pVar = pT instanceof Variable;
        boolean oVar = oT instanceof Variable;

        Integer s = sVar ? null : dict.encode(sT.label());
        Integer p = pVar ? null : dict.encode(pT.label());
        Integer o = oVar ? null : dict.encode(oT.label());

        // Cas optimisé : triple constant
        if (!sVar && !pVar && !oVar) {
            if (SPO.containsKey(s)
                    && SPO.get(s).containsKey(p)
                    && SPO.get(s).get(p).contains(o)) {
                return Collections.singletonList(
                        buildSub(s, p, o, sVar, pVar, oVar, sT, pT, oT)
                ).iterator();
            }
            return Collections.emptyIterator();
        }

        // Construire les 6 permutations du pattern
        RDFTriple spo = new RDFTriple(sT, pT, oT);
        RDFTriple sop = new RDFTriple(sT, oT, pT);
        RDFTriple pso = new RDFTriple(pT, sT, oT);
        RDFTriple pos = new RDFTriple(pT, oT, sT);
        RDFTriple osp = new RDFTriple(oT, sT, pT);
        RDFTriple ops = new RDFTriple(oT, pT, sT);

        Map<String, RDFTriple> patterns = Map.of(
                "SPO", spo, "SOP", sop, "PSO", pso,
                "POS", pos, "OSP", osp, "OPS", ops
        );

        // Calculer la sélectivité
        double bestSel = Double.MAX_VALUE;
        String bestOrder = "SPO";

        for (String ord : patterns.keySet()) {
            long count = howMany(patterns.get(ord));
            double sel = (totalTriples == 0) ? 1.0 : (double) count / totalTriples;
            if (sel < bestSel) {
                bestSel = sel;
                bestOrder = ord;
            }
        }

        // Parcourir l’index choisi
        List<Substitution> out = new ArrayList<>();

        switch (bestOrder) {
            case "SOP": iterate(SOP, s, o, p, out, sVar, pVar, oVar, sT, pT, oT, "SOP"); break;
            case "PSO": iterate(PSO, p, s, o, out, sVar, pVar, oVar, sT, pT, oT, "PSO"); break;
            case "POS": iterate(POS, p, o, s, out, sVar, pVar, oVar, sT, pT, oT, "POS"); break;
            case "OSP": iterate(OSP, o, s, p, out, sVar, pVar, oVar, sT, pT, oT, "OSP"); break;
            case "OPS": iterate(OPS, o, p, s, out, sVar, pVar, oVar, sT, pT, oT, "OPS"); break;
            default:    iterate(SPO, s, p, o, out, sVar, pVar, oVar, sT, pT, oT, "SPO");
        }

        return out.iterator();
    }

    private void iterate(Map<Integer, Map<Integer, Set<Integer>>> idx,
                         Integer k1, Integer k2, Integer k3,
                         List<Substitution> out,
                         boolean sVar, boolean pVar, boolean oVar,
                         Term sT, Term pT, Term oT,
                         String order) {

        if (k1 != null && !idx.containsKey(k1)) return;

        for (var e1 : idx.entrySet()) {
            int a = e1.getKey();
            if (k1 != null && a != k1) continue;

            for (var e2 : e1.getValue().entrySet()) {
                int b = e2.getKey();
                if (k2 != null && b != k2) continue;

                for (int c : e2.getValue()) {
                    if (k3 != null && c != k3) continue;

                    int s, p, o;
                    // Reconstruction de s, p, o selon l'ordre de l'index
                    switch (order) {
                        case "SOP": s=a; o=b; p=c; break;
                        case "PSO": p=a; s=b; o=c; break;
                        case "POS": p=a; o=b; s=c; break;
                        case "OSP": o=a; s=b; p=c; break;
                        case "OPS": o=a; p=b; s=c; break;
                        default:    s=a; p=b; o=c; // SPO
                    }

                    // Cas : ?x p ?x (Sujet == Objet)
                    if (sVar && oVar && sT.equals(oT) && s != o) continue;

                    // Cas : ?x ?x o (Sujet == Prédicat)
                    if (sVar && pVar && sT.equals(pT) && s != p) continue;

                    // Cas : s ?x ?x (Prédicat == Objet)
                    if (pVar && oVar && pT.equals(oT) && p != o) continue;

                    out.add(buildSub(s, p, o, sVar, pVar, oVar, sT, pT, oT));
                }
            }
        }
    }

    private Substitution buildSub(int s, int p, int o,
                                  boolean sVar, boolean pVar, boolean oVar,
                                  Term sT, Term pT, Term oT) {

        SubstitutionImpl sub = new SubstitutionImpl();
        if (sVar) sub.add((Variable) sT, factory.createOrGetLiteral(dict.decode(s)));
        if (pVar) sub.add((Variable) pT, factory.createOrGetLiteral(dict.decode(p)));
        if (oVar) sub.add((Variable) oT, factory.createOrGetLiteral(dict.decode(o)));
        return sub;
    }

    @Override public long size() { return totalTriples; }

    @Override
    public Collection<RDFTriple> getAtoms() {
        List<RDFTriple> a = new ArrayList<>();
        for (var e1 : SPO.entrySet())
            for (var e2 : e1.getValue().entrySet())
                for (int o : e2.getValue())
                    a.add(new RDFTriple(
                            factory.createOrGetLiteral(dict.decode(e1.getKey())),
                            factory.createOrGetLiteral(dict.decode(e2.getKey())),
                            factory.createOrGetLiteral(dict.decode(o))
                    ));
        return a;
    }
}