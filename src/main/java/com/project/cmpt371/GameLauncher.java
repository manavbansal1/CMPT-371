package com.project.cmpt371;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * GameLauncher serves as the entry point for the Team Box Conquest game.
 * It provides a GUI for users to either host a new game or join an existing one.
 * This launcher handles:
 * - Starting a new game server
 * - Connecting to an existing server
 * - Player name selection
 * - Team selection and availability checking
 * - Game client initialization
 */
public class GameLauncher extends Application {
    /** Maximum number of players allowed per team */
    private static final int MAX_PLAYERS_PER_TEAM = 3;

    /**
     * Initialize and display the main launcher interface.
     * Provides options to host a new game or join an existing one.
     *
     * @param primaryStage The primary stage provided by JavaFX
     */
    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Game Launcher");
        primaryStage.getIcons().add(new Image(String.valueOf(getClass().getResource("/Images/icon.png"))));

        // Create main menu buttons
        Button hostButton = new Button("Host Game");
        Button joinButton = new Button("Join Game");

        // Set button IDs for CSS styling
        hostButton.setId("hostButton");
        joinButton.setId("joinButton");

        // Load title image
        Image titleImage = new Image(String.valueOf(getClass().getResource("/Images/GameLauncherTitle.png")));
        ImageView titleImageView = new ImageView(titleImage);

        // Layout setup for main menu
        VBox optionsBox = new VBox(20, hostButton, joinButton);
        VBox screenBox = new VBox(30, titleImageView, optionsBox);
        optionsBox.setAlignment(Pos.CENTER);
        screenBox.setAlignment(Pos.CENTER);

        // Create scene with styling
        Scene scene = new Scene(screenBox, 400, 300);
        scene.getStylesheets().add(getClass().getResource("/css/launcher-style.css").toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.show();

        // Add button event handlers
        hostButton.setOnAction(e -> showHostScreen(primaryStage));
        joinButton.setOnAction(e -> showJoinScreen(primaryStage));
    }

    /**
     * Displays the host screen showing IP and port information.
     * Starts the game server in a separate thread.
     *
     * @param primaryStage The primary stage to update
     */
    private void showHostScreen(Stage primaryStage) {
        // Start the game server in a separate thread
        new Thread(() -> {
            try {
                GameServer.main(new String[0]);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }).start();

        // Get local IP address to display for other players to connect
        final String ip;
        try {
            ip = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return; // Exit method if IP cannot be determined
        }

        // Create IP address display with copy button
        Label ipLabel = new Label("IP Address: " + ip);
        ipLabel.setId("ipLabel");
        Button ipCopyButton = new Button("Copy");
        ipCopyButton.setId("ipCopyButton");
        ipCopyButton.setOnAction(e -> {
            // Copy IP to clipboard
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(ip);
            clipboard.setContent(content);
            
            // Provide visual feedback
            ipCopyButton.setText("Copied!");
            ipCopyButton.setDisable(true);
            
            // Reset button after 2 seconds
            new Thread(() -> {
                try {
                    Thread.sleep(2000); // Show "Copied!" for 2 seconds
                    Platform.runLater(() -> {
                        ipCopyButton.setText("Copy");
                        ipCopyButton.setDisable(false);
                    });
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }).start();
        });
        HBox ipBox = new HBox(10, ipLabel, ipCopyButton);
        ipBox.setAlignment(Pos.CENTER);

        // Create port display with copy button
        Label portLabel = new Label("Port: 12345");
        portLabel.setId("portLabel");
        Button portCopyButton = new Button("Copy");
        portCopyButton.setId("portCopyButton");
        portCopyButton.setOnAction(e -> {
            // Copy port to clipboard
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString("12345");
            clipboard.setContent(content);
            
            // Provide visual feedback
            portCopyButton.setText("Copied!");
            portCopyButton.setDisable(true);
            
            // Reset button after 2 seconds
            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                    Platform.runLater(() -> {
                        portCopyButton.setText("Copy");
                        portCopyButton.setDisable(false);
                    });
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }).start();
        });
        HBox portBox = new HBox(10, portLabel, portCopyButton);
        portBox.setAlignment(Pos.CENTER);

        // Combine IP and port information
        VBox infoBox = new VBox(15, ipBox, portBox);
        infoBox.setAlignment(Pos.CENTER);

        // Add start button to proceed to player setup
        Button startButton = new Button("Start");
        startButton.setId("startButton");
        startButton.setOnAction(e -> showHostPlayerSetup(primaryStage));

        // Create layout for host screen
        BorderPane hostPane = new BorderPane();
        hostPane.setCenter(infoBox);

        HBox bottomBox = new HBox(startButton);
        bottomBox.setAlignment(Pos.BOTTOM_RIGHT);
        bottomBox.setPadding(new Insets(15));
        hostPane.setBottom(bottomBox);

        // Update scene
        Scene hostScene = new Scene(hostPane, 400, 300);
        hostScene.getStylesheets().add(getClass().getResource("/css/launcher-style.css").toExternalForm());
        primaryStage.setScene(hostScene);
    }

    /**
     * Displays the player setup screen for the host.
     * Shows team status and allows name and team selection.
     *
     * @param primaryStage The primary stage to update
     */
    private void showHostPlayerSetup(Stage primaryStage) {
        // Get current team status from local server
        String[] teamStatus = getTeamStatus("localhost", 12345);
        if (teamStatus == null) {
            showAlert("Error", "Failed to get team status from server.");
            return;
        }
        int teamACount = Integer.parseInt(teamStatus[0]);
        int teamBCount = Integer.parseInt(teamStatus[1]);

        // Create player name input
        Label nameLabel = new Label("Enter Your Name:");
        TextField nameField = new TextField();
        
        // Create team selection dropdown
        Label teamLabel = new Label("Choose Team:");
        ComboBox<String> teamChoice = new ComboBox<>();
        teamChoice.getItems().addAll("Red (Team A)", "Blue (Team B)");
        
        // Display available spots for each team
        Label teamStatusLabel = new Label("Red Team: " + (MAX_PLAYERS_PER_TEAM - teamACount) + 
                " spots | Blue Team: " + (MAX_PLAYERS_PER_TEAM - teamBCount) + " spots");

        // Create join button
        Button joinButton = new Button("Join");
        joinButton.setId("joinGameButton");
        joinButton.setOnAction(e -> {
            // Validate inputs
            String name = nameField.getText().trim();
            String team = teamChoice.getValue();
            if (name.isEmpty() || team == null) {
                showAlert("Error", "Please enter a name and select a team.");
                return;
            }
            
            // Verify team capacity
            if (team.contains("Red") && teamACount >= MAX_PLAYERS_PER_TEAM) {
                showAlert("Error", "Red Team is full!");
                return;
            }
            if (team.contains("Blue") && teamBCount >= MAX_PLAYERS_PER_TEAM) {
                showAlert("Error", "Blue Team is full!");
                return;
            }

            // Set client properties
            GameClient.serverIP = "localhost";
            GameClient.serverPort = 12345;
            GameClient.playerName = name;
            GameClient.teamColor = team.contains("Red") ? "TEAM_A" : "TEAM_B";

            // Launch the game client
            launchClient(primaryStage);
        });

        // Layout for player setup screen
        VBox centerBox = new VBox(15, nameLabel, nameField, teamLabel, teamChoice, teamStatusLabel, joinButton);
        centerBox.setAlignment(Pos.CENTER);

        // Update scene
        Scene setupScene = new Scene(centerBox, 400, 300);
        setupScene.getStylesheets().add(getClass().getResource("/css/launcher-style.css").toExternalForm());
        primaryStage.setScene(setupScene);
    }

    /**
     * Displays the join screen for connecting to an existing game.
     * Allows entry of IP address and port.
     *
     * @param primaryStage The primary stage to update
     */
    private void showJoinScreen(Stage primaryStage) {
        // Create IP input field
        Label ipPrompt = new Label("Enter Host IP:");
        TextField ipField = new TextField();
        
        // Create port input field with default value
        Label portPrompt = new Label("Enter Port:");
        TextField portField = new TextField("12345");

        // Create join button
        Button joinButton = new Button("Join");
        joinButton.setId("joinGameButton");
        joinButton.setOnAction(e -> {
            // Validate input
            String ip = ipField.getText().trim();
            int port;
            try {
                port = Integer.parseInt(portField.getText().trim());
            } catch (NumberFormatException ex) {
                showAlert("Error", "Invalid port number.");
                return;
            }
            if (ip.isEmpty()) {
                showAlert("Error", "Please enter an IP address.");
                return;
            }

            // Test connection and check server capacity
            try (Socket tempSocket = new Socket(ip, port)) {
                DataOutputStream tempOut = new DataOutputStream(tempSocket.getOutputStream());
                DataInputStream tempIn = new DataInputStream(tempSocket.getInputStream());

                tempOut.writeUTF("CHECK_CAPACITY");
                String response = tempIn.readUTF();
                if (response.equals("SERVER_FULL")) {
                    showAlert("Error", "Server is full (6 players max).");
                    return;
                }

                // Set connection parameters and proceed to player setup
                GameClient.serverIP = ip;
                GameClient.serverPort = port;
                showJoinPlayerSetup(primaryStage);
            } catch (IOException ex) {
                showAlert("Error", "Failed to connect to server: " + ex.getMessage());
            }
        });

        // Layout for join screen
        VBox centerBox = new VBox(15, ipPrompt, ipField, portPrompt, portField, joinButton);
        centerBox.setAlignment(Pos.CENTER);

        // Update scene
        Scene joinScene = new Scene(centerBox, 400, 300);
        joinScene.getStylesheets().add(getClass().getResource("/css/launcher-style.css").toExternalForm());
        primaryStage.setScene(joinScene);
    }

    /**
     * Displays the player setup screen for a joining player.
     * Shows team status and allows name and team selection.
     *
     * @param primaryStage The primary stage to update
     */
    private void showJoinPlayerSetup(Stage primaryStage) {
        // Get team status from remote server
        String[] teamStatus = getTeamStatus(GameClient.serverIP, GameClient.serverPort);
        if (teamStatus == null) {
            showAlert("Error", "Failed to get team status from server.");
            return;
        }
        int teamACount = Integer.parseInt(teamStatus[0]);
        int teamBCount = Integer.parseInt(teamStatus[1]);

        // Create player name input
        Label nameLabel = new Label("Enter Your Name:");
        TextField nameField = new TextField();
        
        // Create team selection dropdown
        Label teamLabel = new Label("Choose Team:");
        ComboBox<String> teamChoice = new ComboBox<>();
        teamChoice.getItems().addAll("Red (Team A)", "Blue (Team B)");
        
        // Display available spots for each team
        Label teamStatusLabel = new Label("Red Team: " + (MAX_PLAYERS_PER_TEAM - teamACount) + 
                " spots | Blue Team: " + (MAX_PLAYERS_PER_TEAM - teamBCount) + " spots");

        // Create join button
        Button joinButton = new Button("Join");
        joinButton.setId("joinGameButton");
        joinButton.setOnAction(e -> {
            // Validate inputs
            String name = nameField.getText().trim();
            String team = teamChoice.getValue();
            if (name.isEmpty() || team == null) {
                showAlert("Error", "Please enter a name and select a team.");
                return;
            }
            
            // Verify team capacity
            if (team.contains("Red") && teamACount >= MAX_PLAYERS_PER_TEAM) {
                showAlert("Error", "Red Team is full!");
                return;
            }
            if (team.contains("Blue") && teamBCount >= MAX_PLAYERS_PER_TEAM) {
                showAlert("Error", "Blue Team is full!");
                return;
            }

            // Set client properties (server IP and port already set)
            GameClient.playerName = name;
            GameClient.teamColor = team.contains("Red") ? "TEAM_A" : "TEAM_B";

            // Launch the game client
            launchClient(primaryStage);
        });

        // Layout for player setup screen
        VBox centerBox = new VBox(15, nameLabel, nameField, teamLabel, teamChoice, teamStatusLabel, joinButton);
        centerBox.setAlignment(Pos.CENTER);

        // Update scene
        Scene setupScene = new Scene(centerBox, 400, 300);
        setupScene.getStylesheets().add(getClass().getResource("/css/launcher-style.css").toExternalForm());
        primaryStage.setScene(setupScene);
    }

    /**
     * Launches the game client in a new window and closes the launcher.
     *
     * @param primaryStage The launcher stage to close after launching
     */
    private void launchClient(Stage primaryStage) {
        new Thread(() -> {
            try {
                Platform.runLater(() -> {
                    try {
                        // Start new GameClient instance in a new window
                        new GameClient().start(new Stage());
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        showAlert("Error", "Failed to launch client: " + ex.getMessage());
                    }
                });
                // Close the launcher window
                Platform.runLater(primaryStage::close);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * Queries the server for current team occupancy status.
     *
     * @param ip   The server IP address
     * @param port The server port
     * @return String array with [teamACount, teamBCount] or null if request fails
     */
    private String[] getTeamStatus(String ip, int port) {
        try (Socket socket = new Socket(ip, port)) {
            // Create data streams for communication
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            // Send team status request
            out.writeUTF("TEAM_STATUS_REQUEST");
            String response = in.readUTF();
            
            // Parse response if valid
            if (response.startsWith("TEAM_STATUS")) {
                String[] parts = response.split(" ");
                return new String[]{parts[1], parts[2]};
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Displays an error alert dialog with the specified title and message.
     *
     * @param title   The alert title
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