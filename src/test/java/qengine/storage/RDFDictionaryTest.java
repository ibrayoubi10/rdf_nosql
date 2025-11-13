package qengine.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the encode method in RDFDictionary class.
 * <p>
 * The encode method is responsible for assigning unique integer IDs to strings
 * and ensuring that previously encoded strings return the same integer ID.
 */
class RDFDictionaryTest {

    @Test
    void testEncodeNewString() {
        // Arrange
        RDFDictionary dictionary = new RDFDictionary();
        String input = "example";

        // Act
        int encodedValue = dictionary.encode(input);

        // Assert
        assertEquals(0, encodedValue, "The first string should be encoded with the ID 0.");
    }

    @Test
    void testEncodeSameString() {
        // Arrange
        RDFDictionary dictionary = new RDFDictionary();
        String input = "example";

        // Act
        int firstEncoding = dictionary.encode(input);
        int secondEncoding = dictionary.encode(input);

        // Assert
        assertEquals(firstEncoding, secondEncoding, "The same string should always return the same encoded ID.");
    }

    @Test
    void testEncodeMultipleUniqueStrings() {
        // Arrange
        RDFDictionary dictionary = new RDFDictionary();
        String firstString = "first";
        String secondString = "second";

        // Act
        int firstEncoding = dictionary.encode(firstString);
        int secondEncoding = dictionary.encode(secondString);

        // Assert
        assertNotEquals(firstEncoding, secondEncoding, "Different strings should be assigned different IDs.");
    }

    @Test
    void testEncodeNullThrowsException() {
        // Arrange
        RDFDictionary dictionary = new RDFDictionary();

        // Act & Assert
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            dictionary.encode(null);
        });
        assertEquals("La chaîne ne peut pas être null", exception.getMessage(), "Null input should throw an exception with the correct message.");
    }

    @Test
    void testEncodeAfterReuse() {
        // Arrange
        RDFDictionary dictionary = new RDFDictionary();
        String firstString = "alpha";
        String reusableString = "beta";

        // Act
        int initialEncoding = dictionary.encode(firstString);
        int reusedEncoding = dictionary.encode(reusableString);
        int secondUsage = dictionary.encode(reusableString);

        // Assert
        assertEquals(reusedEncoding, secondUsage, "Reusing a string should return the same ID as the first encoding.");
        assertNotEquals(initialEncoding, reusedEncoding, "Two different strings should not share the same encoded ID.");
    }
}