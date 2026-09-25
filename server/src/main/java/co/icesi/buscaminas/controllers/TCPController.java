package co.icesi.buscaminas.controllers;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import co.icesi.buscaminas.controllers.dtos.Request;
import co.icesi.buscaminas.controllers.dtos.Response;
import co.icesi.buscaminas.model.Cell;
import co.icesi.buscaminas.services.ServicesImpl;

public class TCPController {

    private static final int MAX_SIZE = 100;

    private ServicesImpl services;

    private ServerSocket serverSocket;

    private boolean running;

    private ExecutorService executor;

    private Gson gson;

    public TCPController(ServicesImpl services) {
        this(services, 12345);
    }

    public TCPController(ServicesImpl services, int port) {
        this.services = services;
        try {
            // 0.0.0.0: escucha en todas las interfaces de red de la máquina
            serverSocket = new ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"));
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo abrir el puerto " + port
                    + " (¿está en uso o no hay permisos?): " + e.getMessage(), e);
        }
        executor = Executors.newFixedThreadPool(5);
        gson = new GsonBuilder().create();
        running = true;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public boolean isRunning() {
        return running;
    }

    public void startService() {
        log("Servidor TCP escuchando en " + serverSocket.getInetAddress().getHostAddress()
                + ":" + serverSocket.getLocalPort() + " (pool de 5 hilos)");
        while (running) {
            try {
                executor.execute(new TCPClientHandler(serverSocket.accept(), services));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        try {
            serverSocket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void log(String msg) {
        System.out.println("[" + Thread.currentThread().getName() + "] " + msg);
    }

    class TCPClientHandler implements Runnable {
        //TODO: 
        private Socket clientSocket;
        private ServicesImpl services;

        public TCPClientHandler(Socket clientSocket, ServicesImpl services) {
            this.clientSocket = clientSocket;
            this.services = services;
        }

        @Override
        public void run() {
            String client = clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort();
            try {
                log("Cliente conectado: " + client);
                BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));

                String line = reader.readLine();
                Response response = handle(line, client);

                // gson sin pretty printing: la respuesta debe ser una sola línea
                String json = gson.toJson(response);
                writer.write(json);
                writer.newLine();
                writer.flush();
                writer.close();
                reader.close();

                clientSocket.close();
                log("Respuesta enviada a " + client + ": status=" + response.status + ". Cliente desconectado");
            } catch (Exception e) {
                log("Error atendiendo a " + client + ": " + e);
            }
        }

        private Response handle(String line, String client) {
            if (line == null || line.isBlank()) {
                return error("Petición vacía: se esperaba un JSON terminado en salto de línea");
            }
            Request rq;
            try {
                rq = gson.fromJson(line, Request.class);
            } catch (JsonParseException e) {
                log("JSON inválido de " + client + ": " + line);
                return error("JSON inválido: " + e.getMessage());
            }
            if (rq == null || rq.action == null) {
                return error("Falta el campo 'action'");
            }
            log("Acción recibida de " + client + ": " + rq.action + " " + rq.data);
            Map<String, String> data = rq.data == null ? new HashMap<>() : rq.data;

            try {
                Response response = ok();
                Cell[][] board;
                switch (rq.action) {
                    case "SELECT_CELL":
                        int i = intParam(data, "i");
                        int j = intParam(data, "j");
                        try {
                            boolean win = services.selectCell(i, j);
                            response.data.put("win", win);
                            response.data.put("gameEnd", win);
                            if (win) {
                                response.data.put("message", "¡Ganaste! Todas las celdas seguras fueron destapadas");
                            }
                        } catch (IllegalArgumentException e) {
                            // coordenada inválida: error, pero la partida continúa
                            return error(e.getMessage());
                        } catch (RuntimeException e) {
                            response.data.put("gameEnd", true);
                            response.data.put("win", false);
                            response.data.put("message", "Game over");
                        }
                        board = services.printBoard();
                        response.data.put("board", board);
                        break;
                    case "MARK_CELL":
                        i = intParam(data, "i");
                        j = intParam(data, "j");
                        services.markCell(i, j);
                        board = services.printBoard();
                        response.data.put("board", board);
                        break;
                    case "SOW_ALL":
                        services.showAll(true);
                        board = services.printBoard();
                        response.data.put("board", board);
                        break;
                    case "GET_BOARD":
                        board = services.printBoard();
                        response.data.put("board", board);
                        break;
                    case "INIT_GAME":
                        int n = intParam(data, "n");
                        int m = intParam(data, "m");
                        int minas = intParam(data, "minas");
                        if (n <= 0 || m <= 0 || n > MAX_SIZE || m > MAX_SIZE) {
                            return error("n y m deben estar entre 1 y " + MAX_SIZE);
                        }
                        if (minas <= 0 || minas >= n * m) {
                            return error("minas debe ser mayor que 0 y menor que n*m (" + (n * m) + ")");
                        }
                        services.initGame(n, m, minas);
                        board = services.printBoard();
                        response.data.put("board", board);
                        break;

                    default:
                        return error("Acción desconocida: " + rq.action);
                }
                return response;
            } catch (IllegalArgumentException e) {
                return error(e.getMessage());
            } catch (RuntimeException e) {
                log("Error procesando " + rq.action + ": " + e);
                return error("Error interno del servidor: " + e.getMessage());
            }
        }

        private int intParam(Map<String, String> data, String key) {
            String value = data.get(key);
            if (value == null) {
                throw new IllegalArgumentException("Falta el parámetro '" + key + "'");
            }
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("El parámetro '" + key + "' debe ser un entero (recibido '" + value + "')");
            }
        }

        private Response ok() {
            Response response = new Response();
            response.status = "OK";
            response.data = new HashMap<>();
            return response;
        }

        private Response error(String message) {
            Response response = new Response();
            response.status = "ERROR";
            response.data = new HashMap<>();
            response.data.put("message", message);
            return response;
        }

    }

}
