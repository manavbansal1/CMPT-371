package com.project.cmpt371;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import java.io.*;
import java.net.*;
import java.util.*;

public class GameClient extends Application {
    public static String serverIP = "localhost";
    public static int serverPort = 12345;
    public static String playerName = "";
    public static String teamColor = "TEAM_A";

    private static final int GRID_SIZE = 10;
    private static final long HOLD_DURATION = 2000;
    private Socket socket;
    private DataInputStream inputStream;
    private DataOutputStream outputStream;
    private Map<String, Rectangle> gridSquares;
    private String[][] boardState = new String[GRID_SIZE][GRID_SIZE];
    private String[][] heldState = new String[GRID_SIZE][GRID_SIZE];
    private Map<String, Long> pressStartTimes = new HashMap<>();
    private Map<String, Thread> pressTimers = new HashMap<>();
    private String assignedTeam;
    private Text gameInfo;
    private Text redScoreText;
    private Text blueScoreText;
    private TextArea teamAList;
    private TextArea teamBList;
    private TextArea chatArea;
    private TextField chatInput;
    private boolean isRunning = true;
    private Stage primaryStage;
    private GridPane gridPane;
    private boolean serverConfirmed = false;

    @Override
    public void start(Stage primaryStage) throws Exception {
        this.primaryStage = primaryStage;
        socket = new Socket(serverIP, serverPort);
        primaryStage.getIcons().add(new Image(String.valueOf(getClass().getResource("/Images/icon.png"))));
        System.out.println("Client " + playerName + " connected to " + serverIP + ":" + serverPort + " from local port " + socket.getLocalPort());
        inputStream = new DataInputStream(socket.getInputStream());
        outputStream = new DataOutputStream(socket.getOutputStream());
        primaryStage.setWidth(1100);
        primaryStage.setHeight(1040);
        primaryStage.setFullScreen(true);

        // Set up preliminary UI - waiting for server confirmation
        setupUI();

        // Request team assignment
        outputStream.writeUTF("PLAYER_INFO " + playerName + " " + teamColor);
        outputStream.flush();

        // Start listening for server responses
        new Thread(this::listenForMessages).start();
    }

    private void setupUI() {
        gridSquares = new HashMap<>();
        gridPane = new GridPane();
        gridPane.setVgap(5);
        gridPane.setHgap(5);
        gridPane.getStyleClass().add("grid-pane");

        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                final int finalRow = row;
                final int finalCol = col;
                String key = finalRow + "," + finalCol;

                Rectangle square = new Rectangle(50, 50, Color.LIGHTGRAY);
                square.setStroke(Color.BLACK);
                square.getStyleClass().add("grid-square");

                gridSquares.put(key, square);
                gridPane.add(square, col, row);
            }
        }

        // Top: Team Scores
        redScoreText = new Text("0");
        HBox redScoreBox = new HBox(redScoreText);
        redScoreBox.setId("redScoreBox");
        redScoreBox.setAlignment(Pos.CENTER);

        blueScoreText = new Text("0");
        HBox blueScoreBox = new HBox(blueScoreText);
        blueScoreBox.setId("blueScoreBox");
        blueScoreBox.setAlignment(Pos.CENTER);

        HBox topBox = new HBox(20, redScoreBox, blueScoreBox);
        topBox.setAlignment(Pos.CENTER);
        topBox.setPadding(new Insets(15));

        // Center: Game Info and Grid
        gameInfo = new Text("Connecting to server, please wait...");
        gameInfo.setId("gameInfo");

        // Create a container to center the grid and control its size
        StackPane gridContainer = new StackPane(gridPane);
        gridContainer.getStyleClass().add("grid-container");
        gridContainer.setMaxWidth(600);
        gridContainer.setMaxHeight(600);
        gridContainer.setPrefWidth(Region.USE_COMPUTED_SIZE);
        gridContainer.setPrefHeight(Region.USE_COMPUTED_SIZE);

        VBox centerBox = new VBox(15, gameInfo, gridContainer);
        centerBox.setAlignment(Pos.CENTER);
        centerBox.setPrefWidth(Region.USE_COMPUTED_SIZE);

        // Right: Team Lists and Leave Button
        // Add team list headers
        Label teamAHeader = new Label("Red Team");
        teamAHeader.getStyleClass().add("team-header");

        Label teamBHeader = new Label("Blue Team");
        teamBHeader.getStyleClass().add("team-header");

        teamAList = new TextArea();
        teamAList.setEditable(false);
        teamAList.getStyleClass().add("team-a-list");

        teamBList = new TextArea();
        teamBList.setEditable(false);
        teamBList.getStyleClass().add("team-b-list");

        Button leaveButton = new Button("Leave Game");
        leaveButton.setOnAction(e -> {
            isRunning = false;
            try {
                socket.close();
                primaryStage.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });

        VBox teamASection = new VBox(5, teamAHeader, teamAList);
        VBox teamBSection = new VBox(5, teamBHeader, teamBList);

        VBox rightBox = new VBox(15, teamASection, teamBSection, leaveButton);
        rightBox.setAlignment(Pos.TOP_CENTER);
        rightBox.setPadding(new Insets(15));
        rightBox.setPrefWidth(200);
        rightBox.setMaxWidth(200);

        // Bottom: Chat
        Label chatLabel = new Label("Chat");
        chatLabel.getStyleClass().add("chat-header");

        chatArea = new TextArea();
        chatArea.setId("chatArea");
        chatArea.setEditable(false);

        chatInput = new TextField();
        chatInput.setPromptText("Type a message...");
        chatInput.setOnAction(e -> {
            String message = chatInput.getText().trim();
            if (!message.isEmpty()) {
                try {
                    outputStream.writeUTF("CHAT " + message);
                    outputStream.flush();
                    chatInput.clear();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });

        VBox chatBox = new VBox(10, chatLabel, chatArea, chatInput);
        chatBox.setPadding(new Insets(15));
        chatBox.setMaxHeight(200);

        // Main Layout
        BorderPane root = new BorderPane();
        root.setTop(topBox);
        root.setCenter(centerBox);
        root.setRight(rightBox);
        root.setBottom(chatBox);

        Scene scene = new Scene(root, 800, 600);
        scene.getStylesheets().add(getClass().getResource("/css/client-style.css").toExternalForm());
        primaryStage.setTitle("Team Box Conquest - " + playerName);
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(event -> {
            isRunning = false;
            try {
                if (!socket.isClosed()) {
                    socket.close();
                }
                System.out.println("Client " + playerName + " closed.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        primaryStage.show();
    }

    private void setupInteractions() {
        // Only set up interactions after server confirmation
        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                final int finalRow = row;
                final int finalCol = col;
                String key = finalRow + "," + finalCol;
                Rectangle square = gridSquares.get(key);

                square.setOnMousePressed(event -> {
                    if (event.isPrimaryButtonDown() && "UNCLAIMED".equals(boardState[finalRow][finalCol]) && heldState[finalRow][finalCol] == null) {
                        try {
                            outputStream.writeUTF("HOLD_START " + finalRow + " " + finalCol);
                            outputStream.flush();
                            pressStartTimes.put(key, System.currentTimeMillis());

                            // Apply the correct hold style immediately for visual feedback
                            square.getStyleClass().removeAll("team-a-held", "team-b-held");
                            if ("TEAM_A".equals(assignedTeam)) {
                                square.getStyleClass().add("team-a-held");
                                System.out.println("Applied red hold style on mouse press");
                            } else {
                                square.getStyleClass().add("team-b-held");
                                System.out.println("Applied blue hold style on mouse press");
                            }

                            Thread timer = new Thread(() -> {
                                try {
                                    Thread.sleep(HOLD_DURATION);
                                    if (pressStartTimes.containsKey(key)) {
                                        Platform.runLater(() -> handleSquareHold(finalRow, finalCol));
                                    }
                                } catch (InterruptedException e) {
                                    // Thread interrupted
                                }
                            });
                            pressTimers.put(key, timer);
                            timer.start();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });

                square.setOnMouseReleased(event -> {
                    if (pressStartTimes.containsKey(key)) {
                        long pressEndTime = System.currentTimeMillis();
                        long duration = pressEndTime - pressStartTimes.get(key);
                        pressStartTimes.remove(key);
                        Thread timer = pressTimers.remove(key);
                        if (timer != null) {
                            timer.interrupt();
                        }
                        try {
                            outputStream.writeUTF("HOLD_END " + finalRow + " " + finalCol);
                            outputStream.flush();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        if (duration < HOLD_DURATION) {
                            System.out.println("Hold too short: " + duration + " ms");
                        }
                    }
                });

                square.setOnMouseEntered(event -> {
                    if ("UNCLAIMED".equals(boardState[finalRow][finalCol]) && heldState[finalRow][finalCol] == null) {
                        if (assignedTeam != null) {
                            if (assignedTeam.equals("TEAM_A")) {
                                square.getStyleClass().add("team-a-cursor");
                            } else {
                                square.getStyleClass().add("team-b-cursor");
                            }
                        }
                    }
                });

                square.setOnMouseExited(event -> {
                    square.getStyleClass().removeAll("team-a-cursor", "team-b-cursor");
                });
            }
        }

        // Set up grid styling based on confirmed team
        if ("TEAM_A".equals(assignedTeam)) {
            gridPane.getStyleClass().add("team-a-grid");
            System.out.println("Applied team-a-grid style");
        } else {
            gridPane.getStyleClass().add("team-b-grid");
            System.out.println("Applied team-b-grid style");
        }

        // Update title with confirmed team
        primaryStage.setTitle("Team Box Conquest - " + playerName + " (" +
                (assignedTeam.equals("TEAM_A") ? "Red" : "Blue") + " Team)");
    }

    private void listenForMessages() {
        try {
            while (isRunning) {
                String message = inputStream.readUTF();
                System.out.println("Client " + playerName + " received: " + message);

                if (message.startsWith("TEAM_ASSIGNMENT")) {
                    String[] parts = message.split(" ");
                    assignedTeam = parts[1];
                    String assignedName = parts[2];
                    System.out.println("Server assigned team: " + assignedTeam);

                    Platform.runLater(() -> {
                        gameInfo.setText("Playing as " + assignedName + " on " + (assignedTeam.equals("TEAM_A") ? "Red" : "Blue") + " Team");

                        // Now that team is confirmed, setup interactions
                        if (!serverConfirmed) {
                            serverConfirmed = true;
                            setupInteractions();
                        }
                    });
                } else if (message.startsWith("GAME_STATE")) {
                    String[] state = message.split(" ");
                    for (int i = 1, row = 0; row < GRID_SIZE; row++) {
                        for (int col = 0; col < GRID_SIZE; col++, i++) {
                            boardState[row][col] = state[i];
                            final int finalRow = row;
                            final int finalCol = col;
                            Platform.runLater(() -> updateBoard(finalRow, finalCol));
                        }
                    }
                } else if (message.startsWith("HELD_STATE")) {
                    String[] state = message.split(" ");
                    for (int i = 1, row = 0; row < GRID_SIZE; row++) {
                        for (int col = 0; col < GRID_SIZE; col++, i++) {
                            heldState[row][col] = "NONE".equals(state[i]) ? null : state[i];
                            final int finalRow = row;
                            final int finalCol = col;
                            Platform.runLater(() -> updateBoard(finalRow, finalCol));
                        }
                    }
                } else if (message.startsWith("GAME_OVER")) {
                    String winner = message.split(" ")[1];
                    Platform.runLater(() -> showWinScreen(winner));
                } else if (message.equals("TEAM_FULL")) {
                    Platform.runLater(() -> {
                        gameInfo.setText("Selected team is full! Please restart and choose another team.");
                        try {
                            socket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                } else if (message.startsWith("TEAM_SCORES")) {
                    String[] parts = message.split(" ");
                    int maxA = Integer.parseInt(parts[1]);
                    int maxB = Integer.parseInt(parts[2]);
                    Platform.runLater(() -> {
                        redScoreText.setText(String.valueOf(maxA));
                        blueScoreText.setText(String.valueOf(maxB));
                    });
                } else if (message.startsWith("TEAM_LISTS")) {
                    String[] parts = message.split(" ", 3);
                    String teamA = parts[1];
                    String teamB = parts[2];
                    Platform.runLater(() -> {
                        // Format player names with bold styling
                        String formattedTeamA = formatTeamList(teamA, "Red");
                        String formattedTeamB = formatTeamList(teamB, "Blue");

                        teamAList.setText(formattedTeamA);
                        teamBList.setText(formattedTeamB);
                    });
                } else if (message.startsWith("CHAT")) {
                    String chatMsg = message.substring(5);
                    Platform.runLater(() -> {
                        // Format chat messages with appropriate styling
                        String formattedMsg = formatChatMessage(chatMsg);
                        chatArea.appendText(formattedMsg + "\n");
                    });
                } else if (message.startsWith("HOLD_INFO")) {
                    String[] parts = message.split(" ");
                    int row = Integer.parseInt(parts[1]);
                    int col = Integer.parseInt(parts[2]);
                    String holdingTeam = parts[3];
                    Platform.runLater(() -> {
                        Rectangle square = gridSquares.get(row + "," + col);
                        square.getStyleClass().removeAll("team-a-held", "team-b-held");
                        if ("TEAM_A".equals(holdingTeam)) {
                            square.getStyleClass().add("team-a-held");
                        } else {
                            square.getStyleClass().add("team-b-held");
                        }
                    });
                } else if (message.startsWith("RELEASE_INFO")) {
                    String[] parts = message.split(" ");
                    int row = Integer.parseInt(parts[1]);
                    int col = Integer.parseInt(parts[2]);
                    Platform.runLater(() -> {
                        Rectangle square = gridSquares.get(row + "," + col);
                        square.getStyleClass().removeAll("team-a-held", "team-b-held");
                        if ("UNCLAIMED".equals(boardState[row][col])) {
                            square.setFill(Color.LIGHTGRAY);
                        }
                    });
                }
            }
        } catch (IOException e) {
            if (isRunning) {
                System.out.println("Client " + playerName + " disconnected from server: " + e.getMessage());
                Platform.runLater(() -> gameInfo.setText("Disconnected from server."));
            }
        }
    }

    // Helper method to format team player lists
    private String formatTeamList(String teamList, String teamName) {
        if (teamList.isEmpty()) {
            return "";
        }

        String[] players = teamList.split(",");
        StringBuilder sb = new StringBuilder();

        for (String player : players) {
            // Add special formatting for the current player
            if (player.equals(playerName)) {
                sb.append(player).append(" (You)\n");
            } else {
                sb.append(player).append("\n");
            }
        }

        return sb.toString();
    }

    // Helper method to format chat messages
    private String formatChatMessage(String message) {
        // You could add styling based on who sent the message
        if (message.startsWith(playerName)) {
            return message + " (You)";
        }
        return message;
    }

    private void showWinScreen(String winner) {
        Color backgroundColor = winner.equals("TEAM_A") ? Color.rgb(255, 85, 85, 0.9) :
                winner.equals("TEAM_B") ? Color.rgb(85, 85, 255, 0.9) :
                        Color.rgb(128, 128, 128, 0.9);
        String headingText = winner.equals("TIE") ? "Game Over: Tie!" : "Team " + (winner.equals("TEAM_A") ? "A" : "B") + " Won";

        VBox winBox = new VBox(25);
        winBox.setAlignment(Pos.CENTER);
        winBox.getStyleClass().add("win-screen");
        winBox.setBackground(new Background(new BackgroundFill(backgroundColor, null, null)));

        Text heading = new Text(headingText);
        heading.setFont(Font.font("Segoe UI", 40));
        heading.setFill(Color.WHITE);

        Button leaveButton = new Button("Leave");
        leaveButton.setOnAction(e -> {
            isRunning = false;
            try {
                socket.close();
                primaryStage.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });

        // Store the current player's name and team before rejoining
        final String currentName = playerName;
        final String currentTeam = teamColor;

        Button rejoinButton = new Button("Rejoin Game");
        rejoinButton.setOnAction(e -> {
            try {
                socket.close();
                primaryStage.close();
                Platform.runLater(() -> {
                    try {
                        // Preserve the player's name and team color when rejoining
                        GameClient.playerName = currentName;
                        GameClient.teamColor = currentTeam;

                        new GameClient().start(new Stage());
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        showAlert("Error", "Failed to rejoin: " + ex.getMessage());
                    }
                });
            } catch (IOException ex) {
                ex.printStackTrace();
                Platform.runLater(() -> showAlert("Error", "Failed to rejoin: " + ex.getMessage()));
            }
        });

        HBox buttonBox = new HBox(20, leaveButton, rejoinButton);
        buttonBox.setAlignment(Pos.CENTER);

        winBox.getChildren().addAll(heading, buttonBox);

        Scene winScene = new Scene(winBox, 800, 600);
        winScene.getStylesheets().add(getClass().getResource("/css/client-style.css").toExternalForm());
        primaryStage.setScene(winScene);
    }

    private void handleSquareHold(int row, int col) {
        try {
            outputStream.writeUTF("CLAIM_REQUEST " + row + " " + col);
            outputStream.flush();

            // Access the square directly by its coordinates
            Rectangle square = gridSquares.get(row + "," + col);

            // Remove any existing team styles
            square.getStyleClass().removeAll("team-a-held", "team-b-held");

            // Apply the correct style based on this client's assigned team
            if ("TEAM_A".equals(assignedTeam)) {
                square.getStyleClass().add("team-a-held");
                System.out.println("Applied red hold style on claim request");
            } else {
                square.getStyleClass().add("team-b-held");
                System.out.println("Applied blue hold style on claim request");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void updateBoard(int row, int col) {
        Rectangle square = gridSquares.get(row + "," + col);

        // Clear all team-related style classes first
        square.getStyleClass().removeAll("team-a-held", "team-b-held", "team-a-claimed", "team-b-claimed");

        // Check if this is a square we are holding
        String key = row + "," + col;
        boolean isHeldByMe = pressStartTimes.containsKey(key);

        // Handle claimed squares
        if ("TEAM_A".equals(boardState[row][col])) {
            square.getStyleClass().add("team-a-claimed");
        } else if ("TEAM_B".equals(boardState[row][col])) {
            square.getStyleClass().add("team-b-claimed");
        }
        // Handle held squares - prioritize local holds over server holds
        else if (isHeldByMe) {
            // For squares we are holding, use our team color
            if ("TEAM_A".equals(assignedTeam)) {
                square.getStyleClass().add("team-a-held");
            } else {
                square.getStyleClass().add("team-b-held");
            }
        }
        else if (heldState[row][col] != null) {
            // For squares held by others, use server team info
            String holdingTeam = determineHoldingTeam(heldState[row][col]);
            if ("TEAM_A".equals(holdingTeam)) {
                square.getStyleClass().add("team-a-held");
            } else {
                square.getStyleClass().add("team-b-held");
            }
        }
        // Default unclaimed state
        else {
            square.setFill(Color.LIGHTGRAY);
            square.setStroke(Color.BLACK);
        }
    }

    private String determineHoldingTeam(String holdingId) {
        // Try to determine team from the holdingId
        if (holdingId != null) {
            if (holdingId.contains("TEAM_A")) {
                return "TEAM_A";
            } else if (holdingId.contains("TEAM_B")) {
                return "TEAM_B";
            } else {
                // Fallback method
                return holdingId.hashCode() % 2 == 0 ? "TEAM_A" : "TEAM_B";
            }
        }
        return null;
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}