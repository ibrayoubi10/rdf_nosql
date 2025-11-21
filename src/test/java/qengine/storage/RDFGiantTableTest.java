package qengine.storage;

import fr.boreal.model.logicalElements.api.*;
import fr.boreal.model.logicalElements.factory.impl.SameObjectTermFactory;
import fr.boreal.model.logicalElements.impl.SubstitutionImpl;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.jupiter.api.Test;
import qengine.model.RDFTriple;

import qengine.model.StarQuery;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour la classe {@link RDFGiantTable}.
 */
public class RDFGiantTableTest {

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


    /** =============== TEST addAll (Stream et Collection) ================== */
    @Test
    public void testAddAllRDFAtoms() {
        RDFGiantTable store = new RDFGiantTable();

        RDFTriple rdf1 = new RDFTriple(SUBJECT_1, PREDICATE_1, OBJECT_1);
        RDFTriple rdf2 = new RDFTriple(SUBJECT_2, PREDICATE_2, OBJECT_2);

        Set<RDFTriple> set = Set.of(rdf1, rdf2);

        assertTrue(store.addAll(set.stream()));
        assertTrue(store.getAtoms().contains(rdf1));
        assertTrue(store.getAtoms().contains(rdf2));

        store = new RDFGiantTable();
        assertTrue(store.addAll(set));
        assertTrue(store.getAtoms().contains(rdf1));
        assertTrue(store.getAtoms().contains(rdf2));
    }


    /** =============== TEST add() ================== */
    @Test
    public void testAdd() {
        RDFGiantTable store = new RDFGiantTable();

        assertTrue(store.add(new RDFTriple(SUBJECT_1, PREDICATE_1, OBJECT_1)));
        assertEquals(1, store.size());

        assertFalse(store.add(new RDFTriple(SUBJECT_1, PREDICATE_1, OBJECT_1)));
        assertEquals(1, store.size());

        assertTrue(store.add(new RDFTriple(SUBJECT_2, PREDICATE_2, OBJECT_2)));
        assertEquals(2, store.size());
    }


    /** =============== TEST duplicate ================== */
    @Test
    public void testDuplicateInsertion() {
        RDFGiantTable store = new RDFGiantTable();

        RDFTriple t1 = new RDFTriple(SUBJECT_1, PREDICATE_1, OBJECT_1);

        assertTrue(store.add(t1));
        assertFalse(store.add(t1));
        assertEquals(1, store.size());
    }


    /** =============== TEST size() ================== */
    @Test
    public void testSize() {
        RDFGiantTable store = new RDFGiantTable();

        assertEquals(0, store.size());
        store.add(new RDFTriple(SUBJECT_1, PREDICATE_1, OBJECT_1));
        assertEquals(1, store.size());

        store.add(new RDFTriple(SUBJECT_1, PREDICATE_1, OBJECT_3));
        store.add(new RDFTriple(SUBJECT_2, PREDICATE_2, OBJECT_2));
        assertEquals(3, store.size());

        store.add(new RDFTriple(SUBJECT_1, PREDICATE_1, OBJECT_1)); // duplicate
        assertEquals(3, store.size());

        assertEquals(3, store.getAtoms().size());
    }


    /** =============== TEST match() ================== */
    @Test
    public void testMatch() {
        RDFGiantTable store = new RDFGiantTable();

        store.add(new RDFTriple(SUBJECT_1, PREDICATE_1, OBJECT_1));
        store.add(new RDFTriple(SUBJECT_1, PREDICATE_1, OBJECT_3));
        store.add(new RDFTriple(SUBJECT_2, PREDICATE_1, OBJECT_2));


        // ---- Case 0: Exact match ----
        RDFTriple exact = new RDFTriple(SUBJECT_2, PREDICATE_1, OBJECT_2);
        Iterator<Substitution> it = store.match(exact);
        List<Substitution> list = new ArrayList<>();
        it.forEachRemaining(list::add);

        assertEquals(1, list.size());
        assertTrue(list.get(0).isEmpty());


        // ---- Case 1: One variable ----
        RDFTriple oneVar = new RDFTriple(SUBJECT_1, PREDICATE_1, VAR_O);

        List<Substitution> res = new ArrayList<>();
        store.match(oneVar).forEachRemaining(res::add);

        assertEquals(2, res.size());

        Substitution s1 = new SubstitutionImpl();
        s1.add(VAR_O, OBJECT_1);

        Substitution s2 = new SubstitutionImpl();
        s2.add(VAR_O, OBJECT_3);

        assertTrue(res.contains(s1));
        assertTrue(res.contains(s2));


        // ---- Case 2: Two variables ----
        RDFTriple twoVars = new RDFTriple(VAR_S, PREDICATE_1, VAR_O);

        List<Substitution> res2 = new ArrayList<>();
        store.match(twoVars).forEachRemaining(res2::add);

        assertEquals(3, res2.size());


        // ---- Case 3: Three variables ----
        RDFTriple threeVars = new RDFTriple(VAR_S, VAR_P, VAR_O);

        List<Substitution> res3 = new ArrayList<>();
        store.match(threeVars).forEachRemaining(res3::add);

        assertEquals(3, res3.size());


        // ---- No match ----
        RDFTriple noMatch = new RDFTriple(SUBJECT_1, PREDICATE_2, VAR_O);
        assertFalse(store.match(noMatch).hasNext());
    }


    /** =============== TEST howMany() ================== */
    @Test
    public void testHowMany() {
        RDFGiantTable store = new RDFGiantTable();

        store.add(new RDFTriple(SUBJECT_1, PREDICATE_1, OBJECT_1));
        store.add(new RDFTriple(SUBJECT_1, PREDICATE_1, OBJECT_3));
        store.add(new RDFTriple(SUBJECT_2, PREDICATE_1, OBJECT_1));
        store.add(new RDFTriple(SUBJECT_2, PREDICATE_2, OBJECT_2));


        // ---- 0 variables ----
        assertEquals(1, store.howMany(new RDFTriple(SUBJECT_1, PREDICATE_1, OBJECT_1)));
        assertEquals(0, store.howMany(new RDFTriple(SUBJECT_1, PREDICATE_2, OBJECT_1)));


        // ---- 1 variable ----
        assertEquals(2, store.howMany(new RDFTriple(VAR_S, PREDICATE_1, OBJECT_1)));
        assertEquals(1, store.howMany(new RDFTriple(SUBJECT_1, VAR_P, OBJECT_3)));
        assertEquals(2, store.howMany(new RDFTriple(SUBJECT_1, PREDICATE_1, VAR_O)));


        // ---- 2 variables ----
        assertEquals(2, store.howMany(new RDFTriple(VAR_S, VAR_P, OBJECT_1)));
        assertEquals(3, store.howMany(new RDFTriple(VAR_S, PREDICATE_1, VAR_O)));
        assertEquals(2, store.howMany(new RDFTriple(SUBJECT_2, VAR_P, VAR_O)));


        // ---- 3 variables ----
        assertEquals(4, store.howMany(new RDFTriple(VAR_S, VAR_P, VAR_O)));
    }


    @Test
    public void testMatchStarQuery() {
        assertThrows(NotImplementedException.class, () -> {
            new RDFGiantTable().match((StarQuery) null);
        });
    }
}
