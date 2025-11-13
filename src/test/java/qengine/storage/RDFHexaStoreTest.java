package qengine.storage;

import fr.boreal.model.logicalElements.api.*;
import fr.boreal.model.logicalElements.factory.impl.SameObjectTermFactory;
import fr.boreal.model.logicalElements.impl.SubstitutionImpl;
import org.apache.commons.lang3.NotImplementedException;
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
    private static final Literal<String> OBJECT_4 = SameObjectTermFactory.instance().createOrGetLiteral("object4");
    private static final Variable VAR_S = SameObjectTermFactory.instance().createOrGetVariable("?s");
    private static final Variable VAR_P = SameObjectTermFactory.instance().createOrGetVariable("?p");
    private static final Variable VAR_O = SameObjectTermFactory.instance().createOrGetVariable("?o");
    private static final Variable VAR_X = SameObjectTermFactory.instance().createOrGetVariable("?x");
    private static final Variable VAR_Y = SameObjectTermFactory.instance().createOrGetVariable("?y");
    private static final Variable VAR_Z = SameObjectTermFactory.instance().createOrGetVariable("?z");

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

        // Case 2: two variables (subject + object)
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
    public void testMatchStarQuery() {throw new NotImplementedException();}
}
