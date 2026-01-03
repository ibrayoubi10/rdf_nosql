package qengine.storage.colleague;

import java.util.*;
import java.util.stream.Stream;

import fr.boreal.model.logicalElements.api.Substitution;
import fr.boreal.model.logicalElements.impl.SubstitutionImpl;
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
     * @return un itérateur de substitutions correspondant aux match des atomes
     *          (i.e., sur quels termes s'envoient les variables)
     */
    Iterator<Substitution> match(RDFTriple a);


    /**
     * @param query star query
     * @return an itérateur de subsitutions décrivrant les réponses à la requete
     */
    //une combinaison de plusieurs triplets qui partagent une même variable centrale.
    //SELECT ?x WHERE {
    //  ?x type artiste .
    //  ?x lives_in Paris .
    //  ?x birth_year 1904 .
    // ?x est la variable centrale
    //exécute les triplets un par un, puis on combine leurs résultats.
    //Iterator<Substitution> contenant toutes les solutions complètes qui respectent tous les triplets. C’est comme une jointure logique sur la variable ?x.

    default Iterator<Substitution> match(StarQuery query) {

        List<RDFTriple> atoms = new ArrayList<>(query.getRdfAtoms());

        if (atoms.isEmpty()) {
            List<Substitution> empty = new ArrayList<>();
            empty.add(new SubstitutionImpl());
            return empty.iterator();
        }

        // Trier les triplets selon leur sélectivité (le plus restrictif d'abord)
        atoms.sort(Comparator.comparingLong(this::howMany));

        // Match du premier triplet (le plus sélectif après le tri)
        List<Substitution> current = new ArrayList<>();
        match(atoms.get(0)).forEachRemaining(current::add);

        // Jointures sélectives dans l'ordre optimal pour les suivants
        for (int i = 1; i < atoms.size(); i++) {

            RDFTriple atom = atoms.get(i);

            List<Substitution> matches = new ArrayList<>();
            match(atom).forEachRemaining(matches::add);

            List<Substitution> next = new ArrayList<>();

            // Algorithme de jointure (Nested Loop)
            // On ne garde que les substitutions compatibles (merge)
            for (Substitution s1 : current) {
                for (Substitution s2 : matches) {
                    s1.merged(s2).ifPresent(next::add);
                }
            }

            current = next;

            if (current.isEmpty()) break;
        }

        return current.iterator();
    }

    /**
     * @param a atom
     * @return
     */
    long howMany(RDFTriple a);


    /**
     * Retourne le nombre d'atomes dans le Store.
     *
     * @return le nombre d'atomes
     */
    long size();

    /**
     * Retourne une collections contenant tous les atomes du store.
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
