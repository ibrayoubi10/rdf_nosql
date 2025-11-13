package qengine.parser;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.stream.Stream;

import org.eclipse.rdf4j.rio.RDFFormat;

import fr.boreal.io.api.Parser;
import fr.boreal.io.rdf.RDFParser;
import fr.boreal.io.rdf.RDFTranslationMode;
import fr.boreal.model.logicalElements.api.Atom;
import fr.boreal.model.logicalElements.api.Predicate;
import fr.boreal.model.logicalElements.factory.impl.SameObjectPredicateFactory;
import qengine.model.RDFTriple;

/**
 * Parser pour transformer des triplets RDF en RDFAtom.
 */
public class RDFTriplesParser implements Parser<RDFTriple> {

    private static final Predicate TRIPLE_PREDICATE = SameObjectPredicateFactory.instance()
            .createOrGetPredicate("triple", 3);

    private final RDFParser parser;

    public RDFTriplesParser(File file) throws IOException {
        this(new FileReader(file), getRDFFormat(file));
    }

    public RDFTriplesParser(Reader reader, RDFFormat format) {
        // Utilisation explicite du mode RawRDFTranslator
        this.parser = new RDFParser(reader, format, null, RDFTranslationMode.Raw);
    }

    @Override
    public boolean hasNext() {
        return parser.hasNext();
    }

    @Override
    public RDFTriple next() {
        Object obj = parser.next();

        if (obj instanceof Atom atom) {
            return convertToRDFAtom(atom);
        } else {
            throw new IllegalArgumentException("L'objet parsé n'est pas un triplet RDF.");
        }
    }

    @Override
    public void close() {
        parser.close();
    }

    /**
     * Retourne un flux de tous les triplets RDF parsés.
     *
     * @return un flux de RDFAtom
     */
    public Stream<RDFTriple> getRDFAtoms() {
        return this.streamParsedObjects(RDFTriple.class);
    }

    private static RDFFormat getRDFFormat(File file) {
        return org.eclipse.rdf4j.rio.Rio.getParserFormatForFileName(file.getName()).orElse(RDFFormat.TURTLE);
    }

    /**
     * Convertit un atome Integraal standard en RDFTriple.
     *
     * @param atom L'atome à convertir
     * @return L'instance correspondante de RDFTriple
     */
    private RDFTriple convertToRDFAtom(Atom atom) {
        if (atom.getTerms().length != 3) {
            throw new IllegalArgumentException("Un RDFAtom doit contenir exactement trois termes.");
        }

        if (!TRIPLE_PREDICATE.equals(atom.getPredicate())) {
            throw new IllegalArgumentException("Le prédicat de l'atome n'est pas 'triple'.");
        }

        return new RDFTriple(atom.getTerms()[0], atom.getTerms()[1], atom.getTerms()[2]);
    }
}
