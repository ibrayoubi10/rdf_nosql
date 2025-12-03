package qengine.storage;

import fr.boreal.model.logicalElements.api.*;
import fr.boreal.model.logicalElements.factory.impl.SameObjectTermFactory;
import fr.boreal.model.logicalElements.impl.SubstitutionImpl;
import qengine.model.RDFTriple;
import org.junit.jupiter.api.Test;
import qengine.model.StarQuery;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour la classe {@link RDFHexaStore}.
 */
public class RDFHexaStoreTest {
    private static final Literal<String> SUBJECT_1 = SameObjectTermFactory.instance().createOrGetLiteral("subject1");
    private static final Literal<String> PREDICATE_1 = SameObjectTermFactory.instance().createOrGetLiteral("predicate1");
    private static final Literal<String> OBJECT_1 = SameObjectTermFactory.instance().createOrGetLiteral("object1");
    private static final Literal<String> SUBJECT_2 = SameObjectTermFactory.instance().createOrGetLiteral("subject2");
    private static final Literal<String> PREDICATE_2 = SameObjectTermFactory.instance().createOrGetLiteral("predicate2");
    private static final Literal<String> OBJECT_2 = SameObjectTermFactory.instance().createOrGetLiteral("object2");
    private static final Literal<String> OBJECT_3 = SameObjectTermFactory.instance().createOrGetLiteral("object3");
    private static final Variable VAR_S = SameObjectTermFactory.instance().createOrGetVariable("?s");
    private static final Variable VAR_P = SameObjectTermFactory.instance().createOrGetVariable("?p");
    private static final Variable VAR_O = SameObjectTermFactory.instance().createOrGetVariable("?o");
    private static final Variable VAR_X = SameObjectTermFactory.instance().createOrGetVariable("?x");

    @Test
    public void testAddAllRDFAtoms() {
        RDFHexaStore store = new RDFHexaStore();

        // Version stream
        // Ajouter plusieurs RDFAtom
        RDFTriple rdfAtom1 = new RDFTriple(SUBJECT_1, PREDICATE_1, OBJECT_1);
        RDFTriple rdfAtom2 = new RDFTriple(SUBJECT_2, PREDICATE_2, OBJECT_2);

        Set<RDFTriple> rdfAtoms = Set.of(rdfAtom1, rdfAtom2);

        assertTrue(store.addAll(rdfAtoms.stream()), "Les RDFAtoms devraient être ajoutés avec succès.");

        // Vérifier que tous les atomes sont présents
        Collection<RDFTriple> atoms = store.getAtoms();
        assertTrue(atoms.contains(rdfAtom1), "La base devrait contenir le premier RDFAtom ajouté.");
        assertTrue(atoms.contains(rdfAtom2), "La base devrait contenir le second RDFAtom ajouté.");

        // Version collection
        store = new RDFHexaStore();
        assertTrue(store.addAll(rdfAtoms), "Les RDFAtoms devraient être ajoutés avec succès.");

        // Vérifier que tous les atomes sont présents
        atoms = store.getAtoms();
        assertTrue(atoms.contains(rdfAtom1), "La base devrait contenir le premier RDFAtom ajouté.");
        assertTrue(atoms.contains(rdfAtom2), "La base devrait contenir le second RDFAtom ajouté.");
    }

    @Test
    public void testAddRDFAtom() {
        RDFHexaStore store = new RDFHexaStore();

        // Adding a new triple should return true
        assertTrue(store.add(new RDFTriple(SUBJECT_1, PREDICATE_1, OBJECT_1)),
                "Adding a new triple should return true");

        // Verify the triple was actually stored
        assertEquals(1, store.size(), "Store should contain exactly one triple");
        assertTrue(store.getAtoms().contains(new RDFTriple(SUBJECT_1, PREDICATE_1, OBJECT_1)),
                "Store should contain the added triple");

        // Adding the same triple again should return false (duplicate)
        assertFalse(store.add(new RDFTriple(SUBJECT_1, PREDICATE_1, OBJECT_1)),
                "Adding a duplicate triple should return false");

        // Verify size hasn't changed
        assertEquals(1, store.size(), "Store should still contain exactly one triple after duplicate add");
    }

    @Test
    public void testAddDuplicateAtom() {
        RDFHexaStore store = new RDFHexaStore();

        // Add the first triple
        RDFTriple triple1 = new RDFTriple(SUBJECT_1, PREDICATE_1, OBJECT_1);
        assertTrue(store.add(triple1), "First addition should return true");
        assertEquals(1, store.size(), "Store should contain 1 triple");

        // Try to add the exact same triple - should return false
        RDFTriple duplicate1 = new RDFTriple(SUBJECT_1, PREDICATE_1, OBJECT_1);
        assertFalse(store.add(duplicate1), "Adding duplicate should return false");
        assertEquals(1, store.size(), "Store size should remain 1 after duplicate");

        // Add a second different triple
        RDFTriple triple2 = new RDFTriple(SUBJECT_2, PREDICATE_2, OBJECT_2);
        assertTrue(store.add(triple2), "Adding new triple should return true");
        assertEquals(2, store.size(), "Store should now contain 2 triples");

        // Try to add the first triple again
        assertFalse(store.add(triple1), "Re-adding first triple should return false");
        assertEquals(2, store.size(), "Store size should remain 2");

        // Try to add the second triple again
        assertFalse(store.add(triple2), "Re-adding second triple should return false");
        assertEquals(2, store.size(), "Store size should remain 2");

        // Add a third triple with some shared terms
        RDFTriple triple3 = new RDFTriple(SUBJECT_1, PREDICATE_1, OBJECT_3);
        assertTrue(store.add(triple3), "Adding triple with shared S and P should return true");
        assertEquals(3, store.size(), "Store should now contain 3 triples");

        // Verify all triples are present
        Collection<RDFTriple> atoms = store.getAtoms();
        assertTrue(atoms.contains(triple1), "Store should contain first triple");
        assertTrue(atoms.contains(triple2), "Store should contain second triple");
        assertTrue(atoms.contains(triple3), "Store should contain third triple");

        // Try multiple duplicates in sequence
        assertFalse(store.add(duplicate1), "Duplicate should still return false");
        assertFalse(store.add(triple2), "Duplicate should still return false");
        assertFalse(store.add(triple3), "Duplicate should still return false");
        assertEquals(3, store.size(), "Store size should remain 3 after multiple duplicate attempts");
    }

    @Test
    public void testSize() {
        RDFHexaStore store = new RDFHexaStore();

        // Test 1: Empty store should have size 0
        assertEquals(0, store.size(), "Empty store should have size 0");

        // Test 2: Add one triple, size should be 1
        store.add(new RDFTriple(SUBJECT_1, PREDICATE_1, OBJECT_1));
        assertEquals(1, store.size(), "Store should have size 1 after adding one triple");

        // Test 3: Add duplicate, size should remain 1
        store.add(new RDFTriple(SUBJECT_1, PREDICATE_1, OBJECT_1));
        assertEquals(1, store.size(), "Store size should remain 1 after adding duplicate");

        // Test 4: Add a second unique triple, size should be 2
        store.add(new RDFTriple(SUBJECT_2, PREDICATE_2, OBJECT_2));
        assertEquals(2, store.size(), "Store should have size 2 after adding second unique triple");

        // Test 5: Add a third unique triple, size should be 3
        store.add(new RDFTriple(SUBJECT_1, PREDICATE_1, OBJECT_3));
        assertEquals(3, store.size(), "Store should have size 3 after adding third unique triple");

        // Test 6: Add multiple duplicates, size should remain 3
        store.add(new RDFTriple(SUBJECT_1, PREDICATE_1, OBJECT_1));
        store.add(new RDFTriple(SUBJECT_2, PREDICATE_2, OBJECT_2));
        store.add(new RDFTriple(SUBJECT_1, PREDICATE_1, OBJECT_3));
        assertEquals(3, store.size(), "Store size should remain 3 after adding multiple duplicates");

        // Test 7: Add more unique triples
        store.add(new RDFTriple(SUBJECT_1, PREDICATE_2, OBJECT_1));
        assertEquals(4, store.size(), "Store should have size 4");

        store.add(new RDFTriple(SUBJECT_2, PREDICATE_1, OBJECT_1));
        assertEquals(5, store.size(), "Store should have size 5");

        store.add(new RDFTriple(SUBJECT_2, PREDICATE_1, OBJECT_3));
        assertEquals(6, store.size(), "Store should have size 6");

        // Test 8: Verify size matches getAtoms().size()
        Collection<RDFTriple> atoms = store.getAtoms();
        assertEquals(store.size(), atoms.size(),
                "size() should match the number of atoms returned by getAtoms()");

        // Test 9: Test with addAll (using Stream)
        RDFHexaStore store2 = new RDFHexaStore();
        Set<RDFTriple> triples = Set.of(
                new RDFTriple(SUBJECT_1, PREDICATE_1, OBJECT_1),
                new RDFTriple(SUBJECT_2, PREDICATE_2, OBJECT_2),
                new RDFTriple(SUBJECT_1, PREDICATE_1, OBJECT_3)
        );
        store2.addAll(triples.stream());
        assertEquals(3, store2.size(), "Store should have size 3 after addAll with 3 unique triples");

        // Test 10: Add duplicates via addAll
        store2.addAll(triples.stream());
        assertEquals(3, store2.size(), "Store size should remain 3 after addAll with duplicates");

        // Test 11: Test with addAll (using Collection)
        RDFHexaStore store3 = new RDFHexaStore();
        store3.addAll(triples);
        assertEquals(3, store3.size(), "Store should have size 3 after addAll(Collection)");
    }

    @Test
    public void testMatchAtom() {
        RDFHexaStore store = new RDFHexaStore();
        store.add(new RDFTriple(SUBJECT_1, PREDICATE_1, OBJECT_1));
        store.add(new RDFTriple(SUBJECT_2, PREDICATE_1, OBJECT_2));
        store.add(new RDFTriple(SUBJECT_1, PREDICATE_1, OBJECT_3));

        // Case 0: no variables (exact match)
        RDFTriple exactAtom = new RDFTriple(SUBJECT_2, PREDICATE_1, OBJECT_2);
        Iterator<Substitution> exactMatches = store.match(exactAtom);
        List<Substitution> exactList = new ArrayList<>();
        exactMatches.forEachRemaining(exactList::add);
        assertEquals(1, exactList.size(), "Exact triple should yield one empty substitution");
        assertTrue(exactList.getFirst().isEmpty(), "Substitution must be empty for an exact match");

        // Case 1: one variable (object variable)
        RDFTriple matchingAtom = new RDFTriple(SUBJECT_1, PREDICATE_1, VAR_O);
        Iterator<Substitution> matchedAtoms = store.match(matchingAtom);
        List<Substitution> matchedList = new ArrayList<>();
        matchedAtoms.forEachRemaining(matchedList::add);

        Substitution firstResult = new SubstitutionImpl();
        firstResult.add(VAR_O, OBJECT_1);
        Substitution secondResult = new SubstitutionImpl();
        secondResult.add(VAR_O, OBJECT_3);

        assertEquals(2, matchedList.size(), "There should be two matched RDFAtoms");
        assertTrue(matchedList.contains(firstResult), "Missing substitution: " + firstResult);
        assertTrue(matchedList.contains(secondResult), "Missing substitution: " + secondResult);

        // Case 2: two variables (subject and object)
        RDFTriple twoVarAtom = new RDFTriple(VAR_S, PREDICATE_1, VAR_O);
        Iterator<Substitution> twoVarMatches = store.match(twoVarAtom);
        List<Substitution> twoVarList = new ArrayList<>();
        twoVarMatches.forEachRemaining(twoVarList::add);
        assertEquals(3, twoVarList.size(), "Should match all triples with predicate1");

        // Case 3: three variables
        RDFTriple allVarAtom = new RDFTriple(VAR_S, VAR_P, VAR_O);
        Iterator<Substitution> allVarMatches = store.match(allVarAtom);
        List<Substitution> allVarList = new ArrayList<>();
        allVarMatches.forEachRemaining(allVarList::add);
        assertEquals(3, allVarList.size(), "Should return all triples in the store");

        // Case 4: no match
        RDFTriple noMatch = new RDFTriple(SUBJECT_1, PREDICATE_2, VAR_O);
        Iterator<Substitution> noMatchIt = store.match(noMatch);
        assertFalse(noMatchIt.hasNext(), "Nonexistent predicate should yield no matches");
    }

    @Test
    public void testHowMany() {
        RDFHexaStore store = new RDFHexaStore();

        store.add(new RDFTriple(SUBJECT_1, PREDICATE_1, OBJECT_1));
        store.add(new RDFTriple(SUBJECT_1, PREDICATE_1, OBJECT_3));
        store.add(new RDFTriple(SUBJECT_2, PREDICATE_1, OBJECT_1));
        store.add(new RDFTriple(SUBJECT_2, PREDICATE_2, OBJECT_2));

        // PATTERN 0 VARIABLES

        // Exact match exists
        assertEquals(1,
                store.howMany(new RDFTriple(SUBJECT_1, PREDICATE_1, OBJECT_1)),
                "Exact triple should return 1");

        // The exact match does NOT exist
        assertEquals(0,
                store.howMany(new RDFTriple(SUBJECT_1, PREDICATE_2, OBJECT_1)),
                "Nonexistent exact triple should return 0");


        // PATTERN 1 VARIABLE

        // (?s, p1, o1) → matches (s1,p1,o1) and (s2,p1,o1)
        assertEquals(2,
                store.howMany(new RDFTriple(VAR_S, PREDICATE_1, OBJECT_1)),
                "(?s, p1, o1) should match 2 triples");

        // (s1, ?p, o3) → matches only (s1,p1,o3)
        assertEquals(1,
                store.howMany(new RDFTriple(SUBJECT_1, VAR_P, OBJECT_3)),
                "(s1, ?p, o3) should match 1 triple");

        // (s1, p1, ?o) → matches (s1,p1,o1) and (s1,p1,o3)
        assertEquals(2,
                store.howMany(new RDFTriple(SUBJECT_1, PREDICATE_1, VAR_O)),
                "(s1, p1, ?o) should match 2 triples");


        // PATTERN 2 VARIABLES

        // (?s, ?p, o1) → matches (s1,p1,o1) and (s2,p1,o1)
        assertEquals(2,
                store.howMany(new RDFTriple(VAR_S, VAR_P, OBJECT_1)),
                "(?s, ?p, o1) should match 2 triples");

        // (?s, p1, ?o) → matches 3 triples with predicate p1
        assertEquals(3,
                store.howMany(new RDFTriple(VAR_S, PREDICATE_1, VAR_O)),
                "(?s, p1, ?o) should match all 3 triples under p1");

        // (s2, ?p, ?o) → matches (s2,p1,o1) and (s2,p2,o2)
        assertEquals(2,
                store.howMany(new RDFTriple(SUBJECT_2, VAR_P, VAR_O)),
                "(s2, ?p, ?o) should match 2 triples");


        // PATTERN 3 VARIABLES

        assertEquals(4,
                store.howMany(new RDFTriple(VAR_S, VAR_P, VAR_O)),
                "(?s, ?p, ?o) should return total store size");
    }

    @Test
    public void testMatchStarQuery() {
        RDFHexaStore store = new RDFHexaStore();

        // Setup test data
        // Bob: knows Alice, livesIn Paris, worksAt Google
        store.add(new RDFTriple(
                SameObjectTermFactory.instance().createOrGetLiteral("Bob"),
                SameObjectTermFactory.instance().createOrGetLiteral("knows"),
                SameObjectTermFactory.instance().createOrGetLiteral("Alice")));
        store.add(new RDFTriple(
                SameObjectTermFactory.instance().createOrGetLiteral("Bob"),
                SameObjectTermFactory.instance().createOrGetLiteral("livesIn"),
                SameObjectTermFactory.instance().createOrGetLiteral("Paris")));
        store.add(new RDFTriple(
                SameObjectTermFactory.instance().createOrGetLiteral("Bob"),
                SameObjectTermFactory.instance().createOrGetLiteral("worksAt"),
                SameObjectTermFactory.instance().createOrGetLiteral("Google")));

        // Charlie: knows Alice, livesIn Paris, worksAt Meta
        store.add(new RDFTriple(
                SameObjectTermFactory.instance().createOrGetLiteral("Charlie"),
                SameObjectTermFactory.instance().createOrGetLiteral("knows"),
                SameObjectTermFactory.instance().createOrGetLiteral("Alice")));
        store.add(new RDFTriple(
                SameObjectTermFactory.instance().createOrGetLiteral("Charlie"),
                SameObjectTermFactory.instance().createOrGetLiteral("livesIn"),
                SameObjectTermFactory.instance().createOrGetLiteral("Paris")));
        store.add(new RDFTriple(
                SameObjectTermFactory.instance().createOrGetLiteral("Charlie"),
                SameObjectTermFactory.instance().createOrGetLiteral("worksAt"),
                SameObjectTermFactory.instance().createOrGetLiteral("Meta")));

        // David: knows Alice, livesIn London, worksAt Google
        store.add(new RDFTriple(
                SameObjectTermFactory.instance().createOrGetLiteral("David"),
                SameObjectTermFactory.instance().createOrGetLiteral("knows"),
                SameObjectTermFactory.instance().createOrGetLiteral("Alice")));
        store.add(new RDFTriple(
                SameObjectTermFactory.instance().createOrGetLiteral("David"),
                SameObjectTermFactory.instance().createOrGetLiteral("livesIn"),
                SameObjectTermFactory.instance().createOrGetLiteral("London")));
        store.add(new RDFTriple(
                SameObjectTermFactory.instance().createOrGetLiteral("David"),
                SameObjectTermFactory.instance().createOrGetLiteral("worksAt"),
                SameObjectTermFactory.instance().createOrGetLiteral("Google")));

        // === TEST 1: Single pattern ===
        // SELECT ?x WHERE { ?x knows Alice }
        List<RDFTriple> patterns1 = List.of(
                new RDFTriple(VAR_X,
                        SameObjectTermFactory.instance().createOrGetLiteral("knows"),
                        SameObjectTermFactory.instance().createOrGetLiteral("Alice"))
        );
        StarQuery query1 = new StarQuery("q1", patterns1, List.of(VAR_X));

        List<Substitution> results1 = new ArrayList<>();
        store.match(query1).forEachRemaining(results1::add);

        assertEquals(3, results1.size(), "Should find 3 people who know Alice");
        Set<String> names1 = extractVariableValues(results1, VAR_X);
        assertTrue(names1.contains("Bob"));
        assertTrue(names1.contains("Charlie"));
        assertTrue(names1.contains("David"));

        // === TEST 2: Two patterns ===
        // SELECT ?x WHERE { ?x knows Alice. ?x livesIn Paris }
        List<RDFTriple> patterns2 = List.of(
                new RDFTriple(VAR_X,
                        SameObjectTermFactory.instance().createOrGetLiteral("knows"),
                        SameObjectTermFactory.instance().createOrGetLiteral("Alice")),
                new RDFTriple(VAR_X,
                        SameObjectTermFactory.instance().createOrGetLiteral("livesIn"),
                        SameObjectTermFactory.instance().createOrGetLiteral("Paris"))
        );
        StarQuery query2 = new StarQuery("q2", patterns2, List.of(VAR_X));

        List<Substitution> results2 = new ArrayList<>();
        store.match(query2).forEachRemaining(results2::add);

        assertEquals(2, results2.size(), "Should find 2 people who know Alice AND live in Paris");
        Set<String> names2 = extractVariableValues(results2, VAR_X);
        assertTrue(names2.contains("Bob"));
        assertTrue(names2.contains("Charlie"));
        assertFalse(names2.contains("David")); // David lives in London

        // === TEST 3: Three patterns ===
        // SELECT ?x WHERE { ?x knows Alice. ?x livesIn Paris. ?x worksAt Google }
        List<RDFTriple> patterns3 = List.of(
                new RDFTriple(VAR_X,
                        SameObjectTermFactory.instance().createOrGetLiteral("knows"),
                        SameObjectTermFactory.instance().createOrGetLiteral("Alice")),
                new RDFTriple(VAR_X,
                        SameObjectTermFactory.instance().createOrGetLiteral("livesIn"),
                        SameObjectTermFactory.instance().createOrGetLiteral("Paris")),
                new RDFTriple(VAR_X,
                        SameObjectTermFactory.instance().createOrGetLiteral("worksAt"),
                        SameObjectTermFactory.instance().createOrGetLiteral("Google"))
        );
        StarQuery query3 = new StarQuery("q3", patterns3, List.of(VAR_X));

        List<Substitution> results3 = new ArrayList<>();
        store.match(query3).forEachRemaining(results3::add);

        assertEquals(1, results3.size(), "Should find only Bob");
        Set<String> names3 = extractVariableValues(results3, VAR_X);
        assertTrue(names3.contains("Bob"));

        // === TEST 4: No results ===
        // SELECT ?x WHERE { ?x knows Alice. ?x livesIn Tokyo }
        List<RDFTriple> patterns4 = List.of(
                new RDFTriple(VAR_X,
                        SameObjectTermFactory.instance().createOrGetLiteral("knows"),
                        SameObjectTermFactory.instance().createOrGetLiteral("Alice")),
                new RDFTriple(VAR_X,
                        SameObjectTermFactory.instance().createOrGetLiteral("livesIn"),
                        SameObjectTermFactory.instance().createOrGetLiteral("Tokyo"))
        );
        StarQuery query4 = new StarQuery("q4", patterns4, List.of(VAR_X));

        List<Substitution> results4 = new ArrayList<>();
        store.match(query4).forEachRemaining(results4::add);

        assertTrue(results4.isEmpty(), "Nobody knows Alice AND lives in Tokyo");

        // === TEST 5: The first pattern has no matches ===
        // SELECT ?x WHERE { ?x knows Nobody. ?x livesIn Paris }
        List<RDFTriple> patterns5 = List.of(
                new RDFTriple(VAR_X,
                        SameObjectTermFactory.instance().createOrGetLiteral("knows"),
                        SameObjectTermFactory.instance().createOrGetLiteral("Nobody")),
                new RDFTriple(VAR_X,
                        SameObjectTermFactory.instance().createOrGetLiteral("livesIn"),
                        SameObjectTermFactory.instance().createOrGetLiteral("Paris"))
        );
        StarQuery query5 = new StarQuery("q5", patterns5, List.of(VAR_X));

        List<Substitution> results5 = new ArrayList<>();
        store.match(query5).forEachRemaining(results5::add);

        assertTrue(results5.isEmpty(), "First pattern matches nothing");

        // === TEST 6: Pattern order shouldn't affect results ===
        // Same as TEST 2 but patterns in reverse order
        List<RDFTriple> patterns6 = List.of(
                new RDFTriple(VAR_X,
                        SameObjectTermFactory.instance().createOrGetLiteral("livesIn"),
                        SameObjectTermFactory.instance().createOrGetLiteral("Paris")),
                new RDFTriple(VAR_X,
                        SameObjectTermFactory.instance().createOrGetLiteral("knows"),
                        SameObjectTermFactory.instance().createOrGetLiteral("Alice"))
        );
        StarQuery query6 = new StarQuery("q6", patterns6, List.of(VAR_X));

        List<Substitution> results6 = new ArrayList<>();
        store.match(query6).forEachRemaining(results6::add);

        assertEquals(2, results6.size(), "Order of patterns should not affect result count");
        Set<String> names6 = extractVariableValues(results6, VAR_X);
        assertTrue(names6.contains("Bob"));
        assertTrue(names6.contains("Charlie"));
    }

    // Helper method to extract values for a variable from substitutions
    private Set<String> extractVariableValues(List<Substitution> substitutions, Variable var) {
        Set<String> values = new HashSet<>();
        for (Substitution sub : substitutions) {
            Term term = sub.createImageOf(var);
            if (term != null && !term.equals(var)) {
                values.add(term.toString());
            }
        }
        return values;
    }
}
