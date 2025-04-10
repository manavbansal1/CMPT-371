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

/**
 * The GameClient class represents the client-side application for the Team Box Conquest game.
 * It handles the connection to the server, displays the game board, and manages user interactions.
 * Players can claim squares on the grid by pressing and holding them, chat with other players,
 * and view team information and scores.
 */
public class GameClient extends Application {
    /** Default server IP address */
    public static String serverIP = "localhost";
    
    /** Default server port */
    public static int serverPort = 12345;
    
    /** Player's name, set from the launcher */
    public static String playerName = "";
    
    /** Player's selected team color, set from the launcher */
    public static String teamColor = "TEAM_A";

    /** Size of the game grid (10x10) */
    private static final int GRID_SIZE = 10;
    
    /** Socket for connection to the server */
    private Socket socket;
    
    /** Input stream for receiving messages from the server */
    private DataInputStream inputStream;
    
    /** Output stream for sending messages to the server */
    private DataOutputStream outputStream;
    
    /** Map of grid coordinates to Rectangle UI elements */
    private Map<String, Rectangle> gridSquares;
    
    /** Current ownership state of each square on the board */
    private String[][] boardState = new String[GRID_SIZE][GRID_SIZE];
    
    /** Tracks which teams are currently holding each square */
    private List<String>[][] heldState = new ArrayList[GRID_SIZE][GRID_SIZE];
    
    /** The team assigned to this client by the server */
    private String assignedTeam;
    
    /** Text element for displaying game status messages */
    private Text gameInfo;
    
    /** Text element for displaying the red team score */
    private Text redScoreText;
    
    /** Text element for displaying the blue team score */
    private Text blueScoreText;
    
    /** TextArea listing players on Team A (Red) */
    private TextArea teamAList;
    
    /** TextArea listing players on Team B (Blue) */
    private TextArea teamBList;
    
    /** TextArea for displaying chat messages */
    private TextArea chatArea;
    
    /** TextField for entering chat messages */
    private TextField chatInput;
    
    /** Flag to control the message listening thread */
    private boolean isRunning = true;
    
    /** The main application window */
    private Stage primaryStage;
    
    /** The grid container for the game board */
    private GridPane gridPane;

    /**
     * Initializes the client application, connects to the server, and sets up the UI.
     *
     * @param primaryStage The primary stage provided by the JavaFX framework
     * @throws Exception If connection to the server fails
     */
    @Override
    public void start(Stage primaryStage) throws Exception {
        this.primaryStage = primaryStage;
        
        // Connect to the server
        socket = new Socket(serverIP, serverPort);
        primaryStage.getIcons().add(new Image(String.valueOf(getClass().getResource("/Images/icon.png"))));
        System.out.println("Client " + playerName + " connected to " + serverIP + ":" + serverPort);
        
        // Set up data streams
        inputStream = new DataInputStream(socket.getInputStream());
        outputStream = new DataOutputStream(socket.getOutputStream());
        
        // Configure window size
        primaryStage.setWidth(1100);
        primaryStage.setHeight(1040);
        primaryStage.setFullScreen(true);

        // Initialize heldState with empty lists for each grid cell
        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                heldState[row][col] = new ArrayList<>();
            }
        }

        // Set up the UI components
        setupUI();

        // Send player information to the server
        outputStream.writeUTF("PLAYER_INFO " + playerName + " " + teamColor);
        outputStream.flush();

        // Start a separate thread for listening to server messages
        new Thread(this::listenForMessages).start();
    }

    /**
     * Sets up the user interface, including the game grid, team lists, and chat components.
     */
    private void setupUI() {
        // Initialize grid components
        gridSquares = new HashMap<>();
        gridPane = new GridPane();
        gridPane.setVgap(5);
        gridPane.setHgap(5);
        gridPane.getStyleClass().add("grid-pane");

        // Create grid squares
        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                final int finalRow = row;
                final int finalCol = col;
                String key = finalRow + "," + finalCol;

                // Create a square for this grid position
                Rectangle square = new Rectangle(50, 50, Color.LIGHTGRAY);
                square.setStroke(Color.BLACK);
                square.getStyleClass().add("grid-square");

                // Store square reference and add to grid
                gridSquares.put(key, square);
                gridPane.add(square, col, row);
            }
        }

        // Set up score displays
        redScoreText = new Text("0");
        HBox redScoreBox = new HBox(redScoreText);
        redScoreBox.setId("redScoreBox");
        redScoreBox.setAlignment(Pos.CENTER);

        blueScoreText = new Text("0");
        HBox blueScoreBox = new HBox(blueScoreText);
        blueScoreBox.setId("blueScoreBox");
        blueScoreBox.setAlignment(Pos.CENTER);

        // Create top section with scores
        HBox topBox = new HBox(20, redScoreBox, blueScoreBox);
        topBox.setAlignment(Pos.CENTER);
        topBox.setPadding(new Insets(15));

        // Create game info text
        gameInfo = new Text("Connecting to server, please wait...");
        gameInfo.setId("gameInfo");

        // Wrap grid in a container for styling
        StackPane gridContainer = new StackPane(gridPane);
        gridContainer.getStyleClass().add("grid-container");
        gridContainer.setMaxWidth(600);
        gridContainer.setMaxHeight(600);

        // Create center section with game info and grid
        VBox centerBox = new VBox(15, gameInfo, gridContainer);
        centerBox.setAlignment(Pos.CENTER);

        // Set up team lists
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

        // Create leave game button
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

        // Organize team sections
        VBox teamASection = new VBox(5, teamAHeader, teamAList);
        VBox teamBSection = new VBox(5, teamBHeader, teamBList);

        // Create right sidebar with team info and leave button
        VBox rightBox = new VBox(15, teamASection, teamBSection, leaveButton);
        rightBox.setAlignment(Pos.TOP_CENTER);
        rightBox.setPadding(new Insets(15));
        rightBox.setPrefWidth(200);
        rightBox.setMaxWidth(200);

        // Set up chat components
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

        // Create chat section
        VBox chatBox = new VBox(10, chatLabel, chatArea, chatInput);
        chatBox.setPadding(new Insets(15));
        chatBox.setMaxHeight(200);

        // Assemble main layout
        BorderPane root = new BorderPane();
        root.setTop(topBox);
        root.setCenter(centerBox);
        root.setRight(rightBox);
        root.setBottom(chatBox);

        // Create scene and add styling
        Scene scene = new Scene(root, 800, 600);
        scene.getStylesheets().add(getClass().getResource("/css/client-style.css").toExternalForm());
        primaryStage.setTitle("Team Box Conquest - " + playerName);
        primaryStage.setScene(scene);
        
        // Handle window close event
        primaryStage.setOnCloseRequest(event -> {
            isRunning = false;
            try {
                if (!socket.isClosed()) socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        
        // Show the window
        primaryStage.show();
    }

    /**
     * Sets up interaction handlers for grid squares based on the player's assigned team.
     * This is called after receiving team assignment from the server.
     */
    private void setupInteractions() {
        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                final int finalRow = row;
                final int finalCol = col;
                String key = finalRow + "," + finalCol;
                Rectangle square = gridSquares.get(key);

                // Mouse press handler - start holding a square
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

                // Mouse release handler - stop holding a square
                square.setOnMouseReleased(event -> {
                    try {
                        outputStream.writeUTF("HOLD_END " + finalRow + " " + finalCol);
                        outputStream.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });

                // Mouse enter handler - show team-specific cursor on hover
                square.setOnMouseEntered(event -> {
                    if ("UNCLAIMED".equals(boardState[finalRow][finalCol])) {
                        if ("TEAM_A".equals(assignedTeam)) {
                            square.getStyleClass().add("team-a-cursor");
                        } else {
                            square.getStyleClass().add("team-b-cursor");
                        }
                    }
                });

                // Mouse exit handler - remove cursor styling
                square.setOnMouseExited(event -> {
                    square.getStyleClass().removeAll("team-a-cursor", "team-b-cursor");
                });
            }
        }

        // Apply team-specific styling to the grid
        if ("TEAM_A".equals(assignedTeam)) {
            gridPane.getStyleClass().add("team-a-grid");
        } else {
            gridPane.getStyleClass().add("team-b-grid");
        }

        // Update window title with player name and team
        primaryStage.setTitle("Team Box Conquest - " + playerName + " (" +
                ("TEAM_A".equals(assignedTeam) ? "Red" : "Blue") + " Team)");
    }

    /**
     * Listens for messages from the server in a loop and processes them accordingly.
     * This method runs in a separate thread.
     */
    private void listenForMessages() {
        try {
            while (isRunning) {
                String message = inputStream.readUTF();
                System.out.println("Client " + playerName + " received: " + message);

                // Process message based on its type
                if (message.startsWith("TEAM_ASSIGNMENT")) {
                    // Handle team assignment message
                    String[] parts = message.split(" ");
                    assignedTeam = parts[1];
                    String assignedName = parts[2];
                    Platform.runLater(() -> {
                        gameInfo.setText("Playing as " + assignedName + " on " + 
                                (assignedTeam.equals("TEAM_A") ? "Red" : "Blue") + " Team");
                        setupInteractions();
                    });
                } else if (message.startsWith("GAME_STATE")) {
                    // Handle game state update message
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
                    // Handle initial held state message
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
                    // Handle hold start message
                    String[] parts = message.split(" ");
                    int row = Integer.parseInt(parts[1]);
                    int col = Integer.parseInt(parts[2]);
                    String team = parts[3];
                    if (!heldState[row][col].contains(team)) {
                        heldState[row][col].add(team);
                    }
                    Platform.runLater(() -> updateBoard(row, col));
                } else if (message.startsWith("HOLD_END")) {
                    // Handle hold end message
                    String[] parts = message.split(" ");
                    int row = Integer.parseInt(parts[1]);
                    int col = Integer.parseInt(parts[2]);
                    String team = parts[3];
                    heldState[row][col].remove(team);
                    Platform.runLater(() -> updateBoard(row, col));
                } else if (message.startsWith("GAME_OVER")) {
                    // Handle game over message
                    String winner = message.split(" ")[1];
                    Platform.runLater(() -> showWinScreen(winner));
                } else if (message.equals("TEAM_FULL")) {
                    // Handle team full message
                    Platform.runLater(() -> {
                        gameInfo.setText("Selected team is full! Please restart and choose another team.");
                        try {
                            socket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                } else if (message.startsWith("TEAM_SCORES")) {
                    // Handle team scores message
                    String[] parts = message.split(" ");
                    int maxA = Integer.parseInt(parts[1]);
                    int maxB = Integer.parseInt(parts[2]);
                    Platform.runLater(() -> {
                        redScoreText.setText(String.valueOf(maxA));
                        blueScoreText.setText(String.valueOf(maxB));
                    });
                } else if (message.startsWith("TEAM_LISTS")) {
                    // Handle team lists message
                    String[] parts = message.split(" ", 3);
                    String teamA = parts[1];
                    String teamB = parts[2];
                    Platform.runLater(() -> {
                        teamAList.setText(formatTeamList(teamA, "Red"));
                        teamBList.setText(formatTeamList(teamB, "Blue"));
                    });
                } else if (message.startsWith("CHAT")) {
                    // Handle chat message
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

    /**
     * Formats a comma-separated list of team members for display.
     * Highlights the current player with "(You)" suffix.
     *
     * @param teamList Comma-separated string of player names
     * @param teamName Name of the team ("Red" or "Blue")
     * @return Formatted string for display in the team list
     */
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

    /**
     * Formats a chat message, adding "(You)" suffix if the message is from the current player.
     *
     * @param message The chat message to format
     * @return Formatted chat message
     */
    private String formatChatMessage(String message) {
        if (message.startsWith(playerName)) {
            return message + " (You)";
        }
        return message;
    }

    /**
     * Displays the game over screen with appropriate styling based on the winner.
     *
     * @param winner The winning team ("TEAM_A", "TEAM_B", or "TIE")
     */
    private void showWinScreen(String winner) {
        // Set background color based on winner
        Color backgroundColor = winner.equals("TEAM_A") ? Color.rgb(255, 85, 85, 0.9) :
                winner.equals("TEAM_B") ? Color.rgb(85, 85, 255, 0.9) :
                        Color.rgb(128, 128, 128, 0.9);
        String headingText = winner.equals("TIE") ? "Game Over: Tie!" : 
                "Team " + (winner.equals("TEAM_A") ? "A" : "B") + " Won";

        // Create win screen layout
        VBox winBox = new VBox(25);
        winBox.setAlignment(Pos.CENTER);
        winBox.getStyleClass().add("win-screen");
        winBox.setBackground(new Background(new BackgroundFill(backgroundColor, null, null)));

        // Create heading text
        Text heading = new Text(headingText);
        heading.setFont(Font.font("Segoe UI", 40));
        heading.setFill(Color.WHITE);

        // Create leave button
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

        // Store current name and team for potential rejoin
        final String currentName = playerName;
        final String currentTeam = teamColor;

        // Create rejoin button
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

        // Create button container
        HBox buttonBox = new HBox(20, leaveButton, rejoinButton);
        buttonBox.setAlignment(Pos.CENTER);

        // Assemble win screen
        winBox.getChildren().addAll(heading, buttonBox);

        // Create and set new scene
        Scene winScene = new Scene(winBox, 800, 600);
        winScene.getStylesheets().add(getClass().getResource("/css/client-style.css").toExternalForm());
        primaryStage.setScene(winScene);
    }

    /**
     * Updates the visual appearance of a grid square based on its current state.
     *
     * @param row The row of the square to update
     * @param col The column of the square to update
     */
    private void updateBoard(int row, int col) {
        Rectangle square = gridSquares.get(row + "," + col);
        square.getStyleClass().removeAll("team-a-held", "team-b-held", "both-held", "team-a-claimed", "team-b-claimed");

        if ("TEAM_A".equals(boardState[row][col])) {
            // Square is claimed by Team A (Red)
            square.getStyleClass().add("team-a-claimed");
            square.setFill(Color.rgb(255, 0, 0));
        } else if ("TEAM_B".equals(boardState[row][col])) {
            // Square is claimed by Team B (Blue)
            square.getStyleClass().add("team-b-claimed");
            square.setFill(Color.rgb(0, 0, 255));
        } else {
            // Square is unclaimed, check if being held
            List<String> holdingTeams = heldState[row][col];
            if (holdingTeams.contains("TEAM_A") && holdingTeams.contains("TEAM_B")) {
                // Both teams holding - create red/blue gradient (tug-of-war)
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

    /**
     * Displays an error alert dialog with the specified title and message.
     *
     * @param title The alert title
     * @param message The alert message
     */
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Main method to launch the application.
     *
     * @param args Command line arguments (not used)
     */
    public static void main(String[] args) {
        launch(args);
    }
}