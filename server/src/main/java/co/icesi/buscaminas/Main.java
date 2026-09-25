package co.icesi.buscaminas;

import java.util.Scanner;

import co.icesi.buscaminas.controllers.TCPController;
import co.icesi.buscaminas.model.BoardGame;
import co.icesi.buscaminas.services.ServicesImpl;

public class Main {

    private static final int DEFAULT_PORT = 12345;

    /**
     * Uso: Main [puerto] [--local]
     * --local lanza además el juego por consola sobre el mismo tablero (solo para depuración).
     */
    public static void main(String[] args)
    {
        int port = DEFAULT_PORT;
        boolean local = false;
        for (String arg : args) {
            if (arg.equals("--local")) {
                local = true;
                continue;
            }
            try {
                port = Integer.parseInt(arg);
            } catch (NumberFormatException e) {
                System.err.println("Puerto inválido: '" + arg + "'. Uso: Main [puerto] [--local]");
                System.exit(1);
            }
            if (port < 1 || port > 65535) {
                System.err.println("El puerto debe estar entre 1 y 65535 (recibido " + port + ")");
                System.exit(1);
            }
        }

        ServicesImpl serv = new ServicesImpl();
        if (local) {
            new Thread(() -> apply(serv.getGame()), "local-console").start();
        }

        TCPController controller;
        try {
            controller = new TCPController(serv, port);
        } catch (IllegalStateException e) {
            System.err.println("Error al iniciar el servidor: " + e.getMessage());
            System.exit(1);
            return;
        }
        controller.startService();
    }
    public static void apply(BoardGame bg) {

        int n = bg.getBoard().length;
        int m = bg.getBoard()[0].length;
        System.out.println("LandMines on the table: "+ bg.getMines());
//        bg.showAll(true);
//        bg.printBoard();
//        bg.showAll(false);
        bg.printBoard();
        Scanner scanner = new Scanner(System.in);
        System.out.println("select a cell (i,j) between 0 and "+(n-1)+","+(m-1)+" to play, or (-1,-1) to exit");
        System.out.println("you have "+bg.getMines()+" mines to avoid");
        System.out.println("use the format: <operation> <i> <j>");
        System.out.println("operation 1: select cell, operation 2: mark/unmark cell");
        int operation = scanner.nextInt();
        int i = scanner.nextInt();
        int j = scanner.nextInt();
        do{
            try {
                if(operation==2){
                    bg.markCell(i,j);
                }else if(operation ==1){
                    boolean r = bg.selectCell(i, j);
                    if (r) {
                        System.out.println("you win, Congratulations");
                        break;
                    }
                }
                bg.printBoard();
            }catch (RuntimeException e){
                System.out.println(e.getMessage());
                break;
            }
            operation = scanner.nextInt();
            i = scanner.nextInt();
            j = scanner.nextInt();
        }while (i>=0 && j>=0);
        bg.showAll(true);
        bg.printBoard();
        System.out.println("exit");
        scanner.close();

    }
}