package com.project.cmpt371;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;

public class GameClient extends Application {
    public static String serverIP = "localhost";  // Default value; can be overwritten by launcher
    public static int serverPort = 12345;           // Default value; can be overwritten by launcher

    private static final int GRID_SIZE = 10;
    private Socket socket;
    private DataInputStream inputStream;
    private DataOutputStream outputStream;
    private Map<String, Rectangle> gridSquares;
    private String[][] boardState = new String[GRID_SIZE][GRID_SIZE];

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Connect to the server using provided serverIP and serverPort
        socket = new Socket(serverIP, serverPort);
        inputStream = new DataInputStream(socket.getInputStream());
        outputStream = new DataOutputStream(socket.getOutputStream());

        // Initialize the gridSquares map
        gridSquares = new HashMap<>();

        // Define GridPane to hold the 10x10 grid of squares
        GridPane gridPane = new GridPane();
        gridPane.setVgap(5);
        gridPane.setHgap(5);

        // Create the 10x10 grid
        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                final int finalRow = row;
                final int finalCol = col;

                Rectangle square = new Rectangle(50, 50, Color.LIGHTGRAY);
                square.setStroke(Color.BLACK);

                square.setOnMouseClicked(event -> handleSquareClick(finalRow, finalCol));

                gridSquares.put(finalRow + "," + finalCol, square);
                gridPane.add(square, col, row);
            }
        }

        Text gameInfo = new Text("Waiting for game to start...");
        VBox vBox = new VBox(10, gameInfo, gridPane);

        Scene scene = new Scene(vBox, 600, 600);
        primaryStage.setTitle("Team Box Conquest");
        primaryStage.setScene(scene);
        primaryStage.show();

        new Thread(this::listenForMessages).start();
    }

    private void listenForMessages() {
        try {
            while (true) {
                String message = inputStream.readUTF();

                if (message.startsWith("TEAM_ASSIGNMENT")) {
                    int team = Integer.parseInt(message.split(" ")[1]);
                    Platform.runLater(() -> System.out.println("Assigned to Team " + team));
                } else if (message.startsWith("GAME_STATE")) {
                    String[] state = message.split(" ");
                    for (int i = 1, row = 0; row < GRID_SIZE; row++) {
                        for (int col = 0; col < GRID_SIZE; col++, i++) {
                            final int finalRow = row;
                            final int finalCol = col;
                            boardState[row][col] = state[i];
                            Platform.runLater(() -> updateBoard(finalRow, finalCol));
                        }
                    }
                } else if (message.startsWith("GAME_OVER")) {
                    String winner = message.split(" ")[1];
                    Platform.runLater(() -> System.out.println(winner + " wins!"));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleSquareClick(int row, int col) {
        try {
            if (boardState[row][col].equals("UNCLAIMED")) {
                outputStream.writeUTF("CLAIM_REQUEST " + row + " " + col);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void updateBoard(int row, int col) {
        Rectangle square = gridSquares.get(row + "," + col);
        if ("TEAM_A".equals(boardState[row][col])) {
            square.setFill(Color.RED);
        } else if ("TEAM_B".equals(boardState[row][col])) {
            square.setFill(Color.BLUE);
        } else {
            square.setFill(Color.LIGHTGRAY);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
