package co.icesi.buscaminas.client.dto;

import java.util.HashMap;
import java.util.Map;

public class Request {
    public String action;
    public Map<String, String> data;

    public Request() {
        this.data = new HashMap<>();
    }

    /**
     * Construye una petición a partir de pares clave/valor.
     * Ejemplo: new Request("SELECT_CELL", "i", "2", "j", "3")
     */
    public Request(String action, String... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("keyValues debe tener un número par de elementos");
        }
        this.action = action;
        this.data = new HashMap<>();
        for (int k = 0; k < keyValues.length; k += 2) {
            data.put(keyValues[k], keyValues[k + 1]);
        }
    }
}
