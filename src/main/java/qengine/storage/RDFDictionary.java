package qengine.storage;

import java.util.HashMap;
import java.util.Map;

/**
 * Classe représentant un dictionnaire RDF qui permet de mapper des chaînes de
 * caractères (représentant des éléments RDF) à des identifiants uniques (entiers)
 * et vice versa.
 */
public class RDFDictionary {
    private Map<String, Integer> stringToInt = new HashMap<>();
    private Map<Integer, String> intToString = new HashMap<>();
    private int nextId = 0;

    /**
     * Encode une chaîne en un identifiant unique.
     * Si la chaîne existe déjà, retourne l'identifiant existant.
     *
     * @param s la chaîne à encoder
     * @return l'identifiant unique associé
     * @throws IllegalArgumentException si s est null
     */
    public int encode(String s) {
        if (s == null) {
            throw new IllegalArgumentException("La chaîne ne peut pas être null");
        }
        
        Integer id = stringToInt.get(s);
        if (id != null) {
            return id;
        }
        
        // Nouvelle chaîne : assigner un ID
        int newId = nextId++;
        stringToInt.put(s, newId);
        intToString.put(newId, s);
        return newId;
    }

    /**
     * Décode un identifiant pour retrouver la chaîne associée.
     *
     * @param i l'identifiant à décoder
     * @return la chaîne associée
     * @throws IllegalArgumentException si l'identifiant n'existe pas
     */
    public String decode(int i) {
        String value = intToString.get(i);
        if (value == null) {
            throw new IllegalArgumentException("Identifiant inexistant: " + i);
        }
        return value;
    }
    
    /**
     * Retourne le nombre d'éléments dans le dictionnaire.
     *
     * @return le nombre d'éléments
     */
    public int size() {
        return stringToInt.size();
    }
}
