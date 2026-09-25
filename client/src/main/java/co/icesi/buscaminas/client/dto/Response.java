package co.icesi.buscaminas.client.dto;

import java.util.Map;

import com.google.gson.Gson;

public class Response {
    public String status;
    public Map<String, Object> data;

    public boolean isOk() {
        return "OK".equals(status);
    }

    /**
     * Gson deserializa data.board como listas de LinkedTreeMap (data es Map<String,Object>),
     * así que se vuelve a convertir a Cell[][] pasando por un JsonTree.
     */
    public Cell[][] getBoard(Gson gson) {
        if (data == null || data.get("board") == null) {
            return null;
        }
        return gson.fromJson(gson.toJsonTree(data.get("board")), Cell[][].class);
    }

    public boolean isWin() {
        return getBoolean("win");
    }

    public boolean isGameEnd() {
        return getBoolean("gameEnd");
    }

    public String getMessage() {
        if (data == null || data.get("message") == null) {
            return null;
        }
        return data.get("message").toString();
    }

    private boolean getBoolean(String key) {
        if (data == null) {
            return false;
        }
        Object value = data.get(key);
        return value instanceof Boolean && (Boolean) value;
    }
}
