package qengine.storage.colleague;

import java.util.HashMap;
import java.util.Map;

public class Dictionary {
    private final Map<String, Integer> encodeMap = new HashMap<>();
    private final Map<Integer, String> decodeMap = new HashMap<>();
    private int counter = 1;

    public int encode(String value) {
        if (!encodeMap.containsKey(value)) {
            encodeMap.put(value, counter);
            decodeMap.put(counter, value);
            counter++;
        }
        return encodeMap.get(value);
    }

    public String decode(int id) {
        return decodeMap.get(id);
    }

    public int size() {
        return encodeMap.size();
    }
}
