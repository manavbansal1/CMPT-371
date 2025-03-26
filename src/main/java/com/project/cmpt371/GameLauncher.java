package com.project.cmpt371;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

public class GameLauncher extends Application {
    private static final int MAX_PLAYERS_PER_TEAM = 3;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Game Launcher");
        primaryStage.getIcons().add(new Image(String.valueOf(getClass().getResource("/Images/icon.png"))));

        Button hostButton = new Button("Host Game");
        Button joinButton = new Button("Join Game");

        hostButton.setId("hostButton");
        joinButton.setId("joinButton");

        Image titleImage = new Image(String.valueOf(getClass().getResource("/Images/GameLauncherTitle.png")));
        ImageView titleImageView = new ImageView(titleImage);

        VBox optionsBox = new VBox(20, hostButton, joinButton);
        VBox screenBox = new VBox(30, titleImageView, optionsBox);
        optionsBox.setAlignment(Pos.CENTER);
        screenBox.setAlignment(Pos.CENTER);

        Scene scene = new Scene(screenBox, 400, 300);
        scene.getStylesheets().add(getClass().getResource("/css/launcher-style.css").toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.show();

        hostButton.setOnAction(e -> showHostScreen(primaryStage));
        joinButton.setOnAction(e -> showJoinScreen(primaryStage));
    }

    private void showHostScreen(Stage primaryStage) {
        new Thread(() -> {
            try {
                GameServer.main(new String[0]);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }).start();

        String ip = "Unknown";
        try {
            ip = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        Label ipLabel = new Label("IP Address: " + ip);
        Label portLabel = new Label("Port: 12345");

        VBox infoBox = new VBox(15, ipLabel, portLabel);
        infoBox.setAlignment(Pos.CENTER);

        Button startButton = new Button("Start");
        startButton.setId("startButton");

        startButton.setOnAction(e -> showHostPlayerSetup(primaryStage));

        BorderPane hostPane = new BorderPane();
        hostPane.setCenter(infoBox);

        HBox bottomBox = new HBox(startButton);
        bottomBox.setAlignment(Pos.BOTTOM_RIGHT);
        bottomBox.setPadding(new Insets(15));
        hostPane.setBottom(bottomBox); // Fixed typo: should be 'bottomBox'

        Scene hostScene = new Scene(hostPane, 400, 300);
        hostScene.getStylesheets().add(getClass().getResource("/css/launcher-style.css").toExternalForm());
        primaryStage.setScene(hostScene);
    }

    private void showHostPlayerSetup(Stage primaryStage) {
        String[] teamStatus = getTeamStatus("localhost", 12345);
        if (teamStatus == null) {
            showAlert("Error", "Failed to get team status from server.");
            return;
        }
        int teamACount = Integer.parseInt(teamStatus[0]);
        int teamBCount = Integer.parseInt(teamStatus[1]);

        Label nameLabel = new Label("Enter Your Name:");
        TextField nameField = new TextField();
        Label teamLabel = new Label("Choose Team:");
        ComboBox<String> teamChoice = new ComboBox<>();
        teamChoice.getItems().addAll("Red (Team A)", "Blue (Team B)");
        Label teamStatusLabel = new Label("Red Team: " + (MAX_PLAYERS_PER_TEAM - teamACount) + " spots | Blue Team: " + (MAX_PLAYERS_PER_TEAM - teamBCount) + " spots");

        Button joinButton = new Button("Join");
        joinButton.setId("joinGameButton");

        joinButton.setOnAction(e -> {
            String name = nameField.getText().trim();
            String team = teamChoice.getValue();
            if (name.isEmpty() || team == null) {
                showAlert("Error", "Please enter a name and select a team.");
                return;
            }
            if (team.contains("Red") && teamACount >= MAX_PLAYERS_PER_TEAM) {
                showAlert("Error", "Red Team is full!");
                return;
            }
            if (team.contains("Blue") && teamBCount >= MAX_PLAYERS_PER_TEAM) {
                showAlert("Error", "Blue Team is full!");
                return;
            }

            GameClient.serverIP = "localhost";
            GameClient.serverPort = 12345;
            GameClient.playerName = name;
            GameClient.teamColor = team.contains("Red") ? "TEAM_A" : "TEAM_B";

            launchClient(primaryStage);
        });

        VBox centerBox = new VBox(15, nameLabel, nameField, teamLabel, teamChoice, teamStatusLabel, joinButton);
        centerBox.setAlignment(Pos.CENTER);

        Scene setupScene = new Scene(centerBox, 400, 300);
        setupScene.getStylesheets().add(getClass().getResource("/css/launcher-style.css").toExternalForm());
        primaryStage.setScene(setupScene);
    }

    private void showJoinScreen(Stage primaryStage) {
        Label ipPrompt = new Label("Enter Host IP:");
        TextField ipField = new TextField();
        Label portPrompt = new Label("Enter Port:");
        TextField portField = new TextField("12345"); // Default port

        Button joinButton = new Button("Join");
        joinButton.setId("joinGameButton");

        joinButton.setOnAction(e -> {
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

            try (Socket tempSocket = new Socket(ip, port)) {
                DataOutputStream tempOut = new DataOutputStream(tempSocket.getOutputStream());
                DataInputStream tempIn = new DataInputStream(tempSocket.getInputStream());

                tempOut.writeUTF("CHECK_CAPACITY");
                String response = tempIn.readUTF();
                if (response.equals("SERVER_FULL")) {
                    showAlert("Error", "Server is full (6 players max).");
                    return;
                }

                GameClient.serverIP = ip;
                GameClient.serverPort = port;
                showJoinPlayerSetup(primaryStage);
            } catch (IOException ex) {
                showAlert("Error", "Failed to connect to server: " + ex.getMessage());
            }
        });

        VBox centerBox = new VBox(15, ipPrompt, ipField, portPrompt, portField, joinButton);
        centerBox.setAlignment(Pos.CENTER);

        Scene joinScene = new Scene(centerBox, 400, 300);
        joinScene.getStylesheets().add(getClass().getResource("/css/launcher-style.css").toExternalForm());
        primaryStage.setScene(joinScene);
    }

    private void showJoinPlayerSetup(Stage primaryStage) {
        String[] teamStatus = getTeamStatus(GameClient.serverIP, GameClient.serverPort);
        if (teamStatus == null) {
            showAlert("Error", "Failed to get team status from server.");
            return;
        }
        int teamACount = Integer.parseInt(teamStatus[0]);
        int teamBCount = Integer.parseInt(teamStatus[1]);

        Label nameLabel = new Label("Enter Your Name:");
        TextField nameField = new TextField();
        Label teamLabel = new Label("Choose Team:");
        ComboBox<String> teamChoice = new ComboBox<>();
        teamChoice.getItems().addAll("Red (Team A)", "Blue (Team B)");
        Label teamStatusLabel = new Label("Red Team: " + (MAX_PLAYERS_PER_TEAM - teamACount) + " spots | Blue Team: " + (MAX_PLAYERS_PER_TEAM - teamBCount) + " spots");

        Button joinButton = new Button("Join");
        joinButton.setId("joinGameButton");

        joinButton.setOnAction(e -> {
            String name = nameField.getText().trim();
            String team = teamChoice.getValue();
            if (name.isEmpty() || team == null) {
                showAlert("Error", "Please enter a name and select a team.");
                return;
            }
            if (team.contains("Red") && teamACount >= MAX_PLAYERS_PER_TEAM) {
                showAlert("Error", "Red Team is full!");
                return;
            }
            if (team.contains("Blue") && teamBCount >= MAX_PLAYERS_PER_TEAM) {
                showAlert("Error", "Blue Team is full!");
                return;
            }

            GameClient.playerName = name;
            GameClient.teamColor = team.contains("Red") ? "TEAM_A" : "TEAM_B";

            launchClient(primaryStage);
        });

        VBox centerBox = new VBox(15, nameLabel, nameField, teamLabel, teamChoice, teamStatusLabel, joinButton);
        centerBox.setAlignment(Pos.CENTER);

        Scene setupScene = new Scene(centerBox, 400, 300);
        setupScene.getStylesheets().add(getClass().getResource("/css/launcher-style.css").toExternalForm());
        primaryStage.setScene(setupScene);
    }

    private void launchClient(Stage primaryStage) {
        new Thread(() -> {
            try {
                Platform.runLater(() -> {
                    try {
                        new GameClient().start(new Stage());
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        showAlert("Error", "Failed to launch client: " + ex.getMessage());
                    }
                });
                Platform.runLater(primaryStage::close);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private String[] getTeamStatus(String ip, int port) {
        try (Socket socket = new Socket(ip, port)) {
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            out.writeUTF("TEAM_STATUS_REQUEST");
            String response = in.readUTF();
            if (response.startsWith("TEAM_STATUS")) {
                String[] parts = response.split(" ");
                return new String[]{parts[1], parts[2]};
            }
        } catch (IOException e) {
            e.printStackTrace();
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