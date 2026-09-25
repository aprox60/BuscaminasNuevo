package co.icesi.buscaminas.client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import co.icesi.buscaminas.client.dto.Request;
import co.icesi.buscaminas.client.dto.Response;

/**
 * Emisor TCP: abre una conexión por petición, envía una línea JSON y lee una línea JSON de respuesta.
 */
public class BuscaminasTCPClient {

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 15000;

    private final String host;
    private final int port;
    // sin setPrettyPrinting: el protocolo delimita los mensajes con '\n'
    private final Gson gson = new Gson();

    public BuscaminasTCPClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public Gson getGson() {
        return gson;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public Response sendRequest(String host, int port, Request request) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);
            try (BufferedWriter writer = new BufferedWriter(
                         new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

                writer.write(gson.toJson(request));
                writer.newLine();
                writer.flush();

                String line = reader.readLine();
                if (line == null) {
                    throw new IOException("El servidor cerró la conexión sin enviar respuesta");
                }
                try {
                    return gson.fromJson(line, Response.class);
                } catch (JsonParseException e) {
                    throw new IOException("Respuesta del servidor no es un JSON válido: " + line, e);
                }
            }
        }
    }

    public Response initGame(int n, int m, int minas) throws IOException {
        return sendRequest(host, port, new Request("INIT_GAME",
                "n", String.valueOf(n), "m", String.valueOf(m), "minas", String.valueOf(minas)));
    }

    public Response selectCell(int i, int j) throws IOException {
        return sendRequest(host, port, new Request("SELECT_CELL", "i", String.valueOf(i), "j", String.valueOf(j)));
    }

    public Response markCell(int i, int j) throws IOException {
        return sendRequest(host, port, new Request("MARK_CELL", "i", String.valueOf(i), "j", String.valueOf(j)));
    }

    public Response getBoard() throws IOException {
        return sendRequest(host, port, new Request("GET_BOARD"));
    }

    public Response showAll() throws IOException {
        return sendRequest(host, port, new Request("SOW_ALL"));
    }
}
