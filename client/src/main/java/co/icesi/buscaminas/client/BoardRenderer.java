package co.icesi.buscaminas.client;

import co.icesi.buscaminas.client.dto.Cell;

/**
 * Dibuja el tablero en consola. Cada celda ocupa 5 caracteres: "[ x ]".
 */
public class BoardRenderer {

    private static final String RESET = "\u001B[0m";
    private static final String RED = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";

    private static final int CELL_WIDTH = 5;

    public static String render(Cell[][] board) {
        if (board == null || board.length == 0 || board[0].length == 0) {
            return "(tablero vacío)\n";
        }
        int rows = board.length;
        int cols = board[0].length;
        int rowLabelWidth = String.valueOf(rows - 1).length();

        StringBuilder sb = new StringBuilder();
        // cabecera de columnas, centrada sobre cada celda
        sb.append(" ".repeat(rowLabelWidth + 1));
        for (int j = 0; j < cols; j++) {
            sb.append(center(String.valueOf(j), CELL_WIDTH));
        }
        sb.append('\n');

        for (int i = 0; i < rows; i++) {
            sb.append(String.format("%" + rowLabelWidth + "d ", i));
            for (int j = 0; j < cols; j++) {
                sb.append(renderCell(board[i][j]));
            }
            sb.append('\n');
        }
        sb.append("Leyenda: [ . ] oculta   ").append(YELLOW).append("[ M ]").append(RESET).append(" bandera   ")
          .append(RED).append("[ * ]").append(RESET).append(" mina   [ n ] minas alrededor   [   ] vacía\n");
        return sb.toString();
    }

    private static String renderCell(Cell cell) {
        if (cell.isMarked()) {
            return YELLOW + "[ M ]" + RESET;
        }
        if (cell.isHide() && !cell.isShowAll()) {
            return "[ . ]";
        }
        if (cell.isLandMine()) {
            return RED + "[ * ]" + RESET;
        }
        return cell.getValue() == 0 ? "[   ]" : "[ " + cell.getValue() + " ]";
    }

    private static String center(String text, int width) {
        int left = (width - text.length()) / 2;
        int right = width - text.length() - left;
        return " ".repeat(Math.max(0, left)) + text + " ".repeat(Math.max(0, right));
    }
}
