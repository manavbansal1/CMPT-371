package com.project.cmpt371;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class GameLauncher extends Application {

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Game Launcher");

        // Create the initial UI with two options
        Button hostButton = new Button("Host game");
        Button joinButton = new Button("Join game");

        hostButton.setPrefSize(200, 50);
        joinButton.setPrefSize(200, 50);

        VBox optionsBox = new VBox(20, hostButton, joinButton);
        optionsBox.setAlignment(Pos.CENTER);

        Scene scene = new Scene(optionsBox, 400, 300);
        primaryStage.setScene(scene);
        primaryStage.show();

        // Handle Host game button click
        hostButton.setOnAction(e -> showHostScreen(primaryStage));

        // Handle Join game button click
        joinButton.setOnAction(e -> showJoinScreen(primaryStage));
    }

    private void showHostScreen(Stage primaryStage) {
        // Start the server in a new thread
        new Thread(() -> {
            try {
                GameServer.main(new String[0]);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }).start();

        // Get local IP address
        String ip = "Unknown";
        try {
            ip = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        Label ipLabel = new Label("IP Address: " + ip);
        Label portLabel = new Label("Port: 12345");

        VBox centerBox = new VBox(10, ipLabel, portLabel);
        centerBox.setAlignment(Pos.CENTER);

        // Create the Start button at bottom right
        Button startButton = new Button("Start");
        startButton.setOnAction(e -> {
            // Set the GameClient connection details (host mode)
            GameClient.serverIP = "localhost";
            GameClient.serverPort = 12345;
            // Launch the game client in a new stage
            new Thread(() -> Platform.runLater(() -> {
                try {
                    new GameClient().start(new Stage());
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            })).start();
            primaryStage.close(); // Optionally close the launcher
        });

        BorderPane hostPane = new BorderPane();
        hostPane.setCenter(centerBox);

        HBox bottomBox = new HBox(startButton);
        bottomBox.setAlignment(Pos.BOTTOM_RIGHT);
        bottomBox.setPadding(new Insets(10));
        hostPane.setBottom(bottomBox);

        Scene hostScene = new Scene(hostPane, 400, 300);
        primaryStage.setScene(hostScene);
    }

    private void showJoinScreen(Stage primaryStage) {
        // Create UI for joining a game: two text fields for IP and Port
        Label ipPrompt = new Label("Enter Host IP:");
        TextField ipField = new TextField();
        Label portPrompt = new Label("Enter Port:");
        TextField portField = new TextField();

        VBox centerBox = new VBox(10, ipPrompt, ipField, portPrompt, portField);
        centerBox.setAlignment(Pos.CENTER);

        // Create the Join button at bottom right
        Button joinButton = new Button("Join");
        joinButton.setOnAction(e -> {
            String ip = ipField.getText().trim();
            int port = 12345;
            try {
                port = Integer.parseInt(portField.getText().trim());
            } catch (NumberFormatException ex) {
                // Could add error handling here (e.g., alert dialog)
            }
            // Set the GameClient connection details (join mode)
            GameClient.serverIP = ip;
            GameClient.serverPort = port;
            // Launch the game client in a new stage
            new Thread(() -> Platform.runLater(() -> {
                try {
                    new GameClient().start(new Stage());
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            })).start();
            primaryStage.close();
        });

        BorderPane joinPane = new BorderPane();
        joinPane.setCenter(centerBox);

        HBox bottomBox = new HBox(joinButton);
        bottomBox.setAlignment(Pos.BOTTOM_RIGHT);
        bottomBox.setPadding(new Insets(10));
        joinPane.setBottom(bottomBox);

        Scene joinScene = new Scene(joinPane, 400, 300);
        primaryStage.setScene(joinScene);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
