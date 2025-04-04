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
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.paint.CycleMethod;
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
    private Socket socket;
    private DataInputStream inputStream;
    private DataOutputStream outputStream;
    private Map<String, Rectangle> gridSquares;
    private String[][] boardState = new String[GRID_SIZE][GRID_SIZE];
    private List<String>[][] heldState = new ArrayList[GRID_SIZE][GRID_SIZE];
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

    @Override
    public void start(Stage primaryStage) throws Exception {
        this.primaryStage = primaryStage;
        socket = new Socket(serverIP, serverPort);
        primaryStage.getIcons().add(new Image(String.valueOf(getClass().getResource("/Images/icon.png"))));
        System.out.println("Client " + playerName + " connected to " + serverIP + ":" + serverPort);
        inputStream = new DataInputStream(socket.getInputStream());
        outputStream = new DataOutputStream(socket.getOutputStream());
        primaryStage.setWidth(1100);
        primaryStage.setHeight(1040);
        primaryStage.setFullScreen(true);

        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                heldState[row][col] = new ArrayList<>();
            }
        }

        setupUI();

        outputStream.writeUTF("PLAYER_INFO " + playerName + " " + teamColor);
        outputStream.flush();

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

        gameInfo = new Text("Connecting to server, please wait...");
        gameInfo.setId("gameInfo");

        StackPane gridContainer = new StackPane(gridPane);
        gridContainer.getStyleClass().add("grid-container");
        gridContainer.setMaxWidth(600);
        gridContainer.setMaxHeight(600);

        VBox centerBox = new VBox(15, gameInfo, gridContainer);
        centerBox.setAlignment(Pos.CENTER);

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
                if (!socket.isClosed()) socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        primaryStage.show();
    }

    private void setupInteractions() {
        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                final int finalRow = row;
                final int finalCol = col;
                String key = finalRow + "," + finalCol;
                Rectangle square = gridSquares.get(key);

                square.setOnMousePressed(event -> {
                    // Allow interaction with any unclaimed block
                    if (event.isPrimaryButtonDown() && "UNCLAIMED".equals(boardState[finalRow][finalCol])) {
                        try {
                            outputStream.writeUTF("HOLD_START " + finalRow + " " + finalCol);
                            outputStream.flush();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });

                square.setOnMouseReleased(event -> {
                    try {
                        outputStream.writeUTF("HOLD_END " + finalRow + " " + finalCol);
                        outputStream.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });

                square.setOnMouseEntered(event -> {
                    if ("UNCLAIMED".equals(boardState[finalRow][finalCol])) {
                        if ("TEAM_A".equals(assignedTeam)) {
                            square.getStyleClass().add("team-a-cursor");
                        } else {
                            square.getStyleClass().add("team-b-cursor");
                        }
                    }
                });

                square.setOnMouseExited(event -> {
                    square.getStyleClass().removeAll("team-a-cursor", "team-b-cursor");
                });
            }
        }

        if ("TEAM_A".equals(assignedTeam)) {
            gridPane.getStyleClass().add("team-a-grid");
        } else {
            gridPane.getStyleClass().add("team-b-grid");
        }

        primaryStage.setTitle("Team Box Conquest - " + playerName + " (" +
                ("TEAM_A".equals(assignedTeam) ? "Red" : "Blue") + " Team)");
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
                    Platform.runLater(() -> {
                        gameInfo.setText("Playing as " + assignedName + " on " + (assignedTeam.equals("TEAM_A") ? "Red" : "Blue") + " Team");
                        setupInteractions();
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
                } else if (message.startsWith("INITIAL_HELD_STATE")) {
                    String[] state = message.split(" ");
                    for (int i = 1, row = 0; row < GRID_SIZE; row++) {
                        for (int col = 0; col < GRID_SIZE; col++, i++) {
                            String holding = state[i];
                            heldState[row][col].clear();
                            if (!"NONE".equals(holding)) {
                                heldState[row][col].addAll(Arrays.asList(holding.split(",")));
                            }
                            final int finalRow = row;
                            final int finalCol = col;
                            Platform.runLater(() -> updateBoard(finalRow, finalCol));
                        }
                    }
                } else if (message.startsWith("HOLD_START")) {
                    String[] parts = message.split(" ");
                    int row = Integer.parseInt(parts[1]);
                    int col = Integer.parseInt(parts[2]);
                    String team = parts[3];
                    if (!heldState[row][col].contains(team)) {
                        heldState[row][col].add(team);
                    }
                    Platform.runLater(() -> updateBoard(row, col));
                } else if (message.startsWith("HOLD_END")) {
                    String[] parts = message.split(" ");
                    int row = Integer.parseInt(parts[1]);
                    int col = Integer.parseInt(parts[2]);
                    String team = parts[3];
                    heldState[row][col].remove(team);
                    Platform.runLater(() -> updateBoard(row, col));
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
                        teamAList.setText(formatTeamList(teamA, "Red"));
                        teamBList.setText(formatTeamList(teamB, "Blue"));
                    });
                } else if (message.startsWith("CHAT")) {
                    String chatMsg = message.substring(5);
                    Platform.runLater(() -> chatArea.appendText(formatChatMessage(chatMsg) + "\n"));
                }
            }
        } catch (IOException e) {
            if (isRunning) {
                System.out.println("Client " + playerName + " disconnected: " + e.getMessage());
                Platform.runLater(() -> gameInfo.setText("Disconnected from server."));
            }
        }
    }

    private String formatTeamList(String teamList, String teamName) {
        if (teamList.isEmpty()) {
            return "";
        }
        String[] players = teamList.split(",");
        StringBuilder sb = new StringBuilder();
        for (String player : players) {
            if (player.equals(playerName)) {
                sb.append(player).append(" (You)\n");
            } else {
                sb.append(player).append("\n");
            }
        }
        return sb.toString();
    }

    private String formatChatMessage(String message) {
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

        final String currentName = playerName;
        final String currentTeam = teamColor;

        Button rejoinButton = new Button("Rejoin Game");
        rejoinButton.setOnAction(e -> {
            try {
                socket.close();
                primaryStage.close();
                Platform.runLater(() -> {
                    try {
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

    private void updateBoard(int row, int col) {
        Rectangle square = gridSquares.get(row + "," + col);
        square.getStyleClass().removeAll("team-a-held", "team-b-held", "both-held", "team-a-claimed", "team-b-claimed");

        if ("TEAM_A".equals(boardState[row][col])) {
            square.getStyleClass().add("team-a-claimed");
            square.setFill(Color.rgb(255, 0, 0));
        } else if ("TEAM_B".equals(boardState[row][col])) {
            square.getStyleClass().add("team-b-claimed");
            square.setFill(Color.rgb(0, 0, 255));
        } else {
            List<String> holdingTeams = heldState[row][col];
            if (holdingTeams.contains("TEAM_A") && holdingTeams.contains("TEAM_B")) {
                // Both teams holding - create red/blue gradient
                square.getStyleClass().add("both-held");
                
                // Create a red/blue linear gradient
                Stop[] stops = new Stop[] {
                    new Stop(0, Color.rgb(255, 0, 0, 0.5)),  // Light red
                    new Stop(1, Color.rgb(0, 0, 255, 0.5))   // Light blue
                };
                LinearGradient gradient = new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE, stops);
                square.setFill(gradient);
            } else if (holdingTeams.contains("TEAM_A")) {
                // Only Team A holding
                square.getStyleClass().add("team-a-held");
                square.setFill(Color.rgb(255, 0, 0, 0.5)); // Light red
            } else if (holdingTeams.contains("TEAM_B")) {
                // Only Team B holding
                square.getStyleClass().add("team-b-held");
                square.setFill(Color.rgb(0, 0, 255, 0.5)); // Light blue
            } else {
                // No one holding
                square.setFill(Color.LIGHTGRAY);
                square.setStroke(Color.BLACK);
            }
        }
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