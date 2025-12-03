package qengine.storage;

import java.util.*;
import java.util.stream.Stream;

import fr.boreal.model.logicalElements.api.Substitution;
import qengine.model.RDFTriple;
import qengine.model.StarQuery;

/**
 * Contrat pour un système de stockage de données RDF
 */
public interface RDFStorage {

    /**
     * Ajoute un RDFAtom dans le store.
     *
     * @param t le triplet à ajouter
     * @return true si le RDFAtom a été ajouté avec succès, false s'il est déjà présent
     */
    boolean add(RDFTriple t);

    /**
     * @param a atom
     * @return un itérateur de substitutions correspondant aux matchs des atomes
     *          (i.e., sur quels termes s'envoient les variables).
     */
    Iterator<Substitution> match(RDFTriple a);

    /**
     * @param q star query
     * @return an itérateur de substitutions décrivant les réponses à la requete
     */
    default Iterator<Substitution> match(StarQuery q) {
        List<RDFTriple> patterns = new ArrayList<>(q.getRdfAtoms());

        if (patterns.isEmpty()) {
            return Collections.emptyIterator();
        }

        // Sort patterns by selectivity
        patterns.sort(Comparator.comparingLong(this::howMany));

        // Initialize candidates with the match results of the MOST selective pattern
        List<Substitution> candidates = new ArrayList<>();
        match(patterns.getFirst()).forEachRemaining(candidates::add);

        // Early exit: if the most selective pattern had no matches
        if (candidates.isEmpty()) {
            return Collections.emptyIterator();
        }

        // Iterate over the remaining patterns, starting from the second pattern
        for (int i=1; i < patterns.size(); i++) {
            List<Substitution> newCandidates = new ArrayList<>();

            for (Substitution candidate : candidates) {
                Iterator<Substitution> patternResults = match(patterns.get(i));

                while (patternResults.hasNext()) {
                    Optional<Substitution> merged = candidate.merged(patternResults.next());
                    merged.ifPresent(newCandidates::add);
                }
            }

            candidates = newCandidates;

            // Early exit: if no candidates left, no need to continue
            if (candidates.isEmpty()) break;
        }

        return candidates.iterator();
    }

    /**
     * Retourne le nombre de triplets du store correspondant à l'atome donné
     *
     * @param a atom
     * @return nombre de triplets correspondants
     */
    long howMany(RDFTriple a);

    /**
     * Retourne le nombre d'atomes dans le Store.
     *
     * @return le nombre d'atomes
     */
    long size();

    /**
     * Retourne une collection contenant tous les atomes du store.
     * Utile pour les tests unitaires.
     *
     * @return une collection d'atomes
     */
    Collection<RDFTriple> getAtoms();

    /**
     * Ajoute des RDFAtom dans le store.
     *
     * @param atoms les RDFAtom à ajouter
     * @return true si au moins un RDFAtom a été ajouté, false s'ils sont tous déjà présents
     */
    default boolean addAll(Stream<RDFTriple> atoms) {
        return atoms.map(this::add).reduce(Boolean::logicalOr).orElse(false);
    }

    /**
     * Ajoute des RDFAtom dans le store.
     *
     * @param atoms les RDFAtom à ajouter
     * @return true si au moins un RDFAtom a été ajouté, false s'ils sont tous déjà présents
     */
    default boolean addAll(Collection<RDFTriple> atoms) {
        return this.addAll(atoms.stream());
    }
}
