package co.icesi.buscaminas.client;

import java.io.IOException;
import java.util.Scanner;

import co.icesi.buscaminas.client.dto.Cell;
import co.icesi.buscaminas.client.dto.Response;

/**
 * Cliente de consola del Buscaminas distribuido.
 * Uso: MainClient [host] [puerto]   (por defecto localhost 12345)
 */
public class MainClient {

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 12345;

    private final BuscaminasTCPClient client;
    private final Scanner scanner;
    // dimensiones del último tablero recibido, para validar coordenadas antes de enviarlas
    private int rows = -1;
    private int cols = -1;

    public MainClient(BuscaminasTCPClient client, Scanner scanner) {
        this.client = client;
        this.scanner = scanner;
    }

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : DEFAULT_HOST;
        int port = DEFAULT_PORT;
        if (args.length > 1) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                port = -1;
            }
            if (port < 1 || port > 65535) {
                System.err.println("Puerto inválido: '" + args[1] + "'. Uso: MainClient [host] [puerto]");
                System.exit(1);
            }
        }

        System.out.println("Buscaminas distribuido - servidor " + host + ":" + port);
        new MainClient(new BuscaminasTCPClient(host, port), new Scanner(System.in)).loop();
    }

    private void loop() {
        // muestra el estado actual de la partida compartida (si el servidor responde)
        execute(this::requestBoard);

        while (true) {
            printMenu();
            Integer option = readInt("Seleccione una opción: ", 1, 6);
            if (option == null) {
                return; // fin de la entrada estándar
            }
            switch (option) {
                case 1 -> execute(this::initGame);
                case 2 -> execute(this::selectCell);
                case 3 -> execute(this::markCell);
                case 4 -> execute(this::requestBoard);
                case 5 -> execute(this::surrender);
                case 6 -> {
                    System.out.println("¡Hasta luego!");
                    return;
                }
                default -> { }
            }
        }
    }

    private void printMenu() {
        System.out.println();
        System.out.println("===== BUSCAMINAS =====");
        System.out.println("1. Iniciar nuevo juego");
        System.out.println("2. Seleccionar celda");
        System.out.println("3. Marcar/desmarcar celda");
        System.out.println("4. Ver tablero");
        System.out.println("5. Rendirse (mostrar todo)");
        System.out.println("6. Salir");
    }

    private void initGame() throws IOException {
        Integer n = readInt("Número de filas (n): ", 1, 100);
        if (n == null) return;
        Integer m = readInt("Número de columnas (m): ", 1, 100);
        if (m == null) return;
        if (n * m < 2) {
            System.out.println("El tablero debe tener al menos 2 celdas.");
            return;
        }
        Integer minas = readInt("Número de minas (1 a " + (n * m - 1) + "): ", 1, n * m - 1);
        if (minas == null) return;
        Response response = client.initGame(n, m, minas);
        if (showIfError(response)) return;
        System.out.println("Nueva partida de " + n + "x" + m + " con " + minas + " minas.");
        printBoard(response);
    }

    private void selectCell() throws IOException {
        int[] cell = readCoordinates();
        if (cell == null) return;
        Response response = client.selectCell(cell[0], cell[1]);
        if (showIfError(response)) return;

        if (response.isGameEnd() && response.isWin()) {
            printBoard(response);
            System.out.println("\u001B[32m¡FELICITACIONES! Ganaste la partida.\u001B[0m");
        } else if (response.isGameEnd()) {
            System.out.println("\u001B[31m¡BOOM! Pisaste una mina. " + orEmpty(response.getMessage()) + "\u001B[0m");
            Response all = client.showAll();
            printBoard(showIfError(all) ? response : all);
        } else {
            printBoard(response);
        }
    }

    private void markCell() throws IOException {
        int[] cell = readCoordinates();
        if (cell == null) return;
        Response response = client.markCell(cell[0], cell[1]);
        if (showIfError(response)) return;
        printBoard(response);
    }

    private void requestBoard() throws IOException {
        Response response = client.getBoard();
        if (showIfError(response)) return;
        printBoard(response);
    }

    private void surrender() throws IOException {
        Response response = client.showAll();
        if (showIfError(response)) return;
        System.out.println("Te rendiste. Este era el tablero completo:");
        printBoard(response);
        System.out.println("Usa la opción 1 para iniciar una nueva partida.");
    }

    // ---------- utilidades ----------

    private interface Action {
        void run() throws IOException;
    }

    /** Ejecuta una acción de red sin dejar caer el cliente si el servidor no está disponible. */
    private void execute(Action action) {
        try {
            action.run();
        } catch (IOException e) {
            System.out.println("\u001B[31mNo se pudo comunicar con el servidor " + client.getHost() + ":"
                    + client.getPort() + " (" + e.getMessage() + "). ¿Está en ejecución?\u001B[0m");
        }
    }

    private boolean showIfError(Response response) {
        if (response == null) {
            System.out.println("Respuesta vacía del servidor.");
            return true;
        }
        if (!response.isOk()) {
            System.out.println("\u001B[31mError: " + orEmpty(response.getMessage()) + "\u001B[0m");
            return true;
        }
        return false;
    }

    private void printBoard(Response response) {
        Cell[][] board = response.getBoard(client.getGson());
        if (board == null) {
            System.out.println("(el servidor no envió tablero)");
            return;
        }
        rows = board.length;
        cols = board.length > 0 ? board[0].length : 0;
        System.out.println();
        System.out.print(BoardRenderer.render(board));
    }

    private int[] readCoordinates() {
        Integer i = readInt("Fila (i): ", 0, rows > 0 ? rows - 1 : Integer.MAX_VALUE);
        if (i == null) return null;
        Integer j = readInt("Columna (j): ", 0, cols > 0 ? cols - 1 : Integer.MAX_VALUE);
        if (j == null) return null;
        return new int[]{i, j};
    }

    /** Lee un entero en [min, max]; repite mientras la entrada sea inválida. Retorna null si se acaba la entrada. */
    private Integer readInt(String prompt, int min, int max) {
        while (true) {
            System.out.print(prompt);
            if (!scanner.hasNextLine()) {
                return null;
            }
            String line = scanner.nextLine().trim();
            try {
                int value = Integer.parseInt(line);
                if (value >= min && value <= max) {
                    return value;
                }
                System.out.println("Valor fuera de rango. Debe estar entre " + min + " y "
                        + (max == Integer.MAX_VALUE ? "el tamaño del tablero" : max) + ".");
            } catch (NumberFormatException e) {
                System.out.println("Entrada inválida: '" + line + "' no es un número entero.");
            }
        }
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }
}
