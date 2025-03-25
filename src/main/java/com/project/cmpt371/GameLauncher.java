package com.project.cmpt371;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
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
        primaryStage.getIcons().add(new Image(String.valueOf(getClass().getResource("/Images/icon.png"))));

        Button hostButton = new Button("Host game");
        Button joinButton = new Button("Join game");

        hostButton.setPrefSize(200, 50);
        joinButton.setPrefSize(200, 50);
        
        // Inline styling for buttons
        hostButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-size: 16px;");
        joinButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-size: 16px;");

        Image titleImage = new Image(String.valueOf(getClass().getResource("/Images/GameLauncherTitle.png")));
        ImageView titleImageView = new ImageView(titleImage);

        VBox optionsBox = new VBox(15, hostButton, joinButton);
        VBox screenBox = new VBox(25, titleImageView, optionsBox);
        optionsBox.setAlignment(Pos.CENTER);
        screenBox.setAlignment(Pos.CENTER);

        // Style the main container
        screenBox.setStyle("-fx-background-color: #f0f0f0;");

        Scene scene = new Scene(screenBox, 400, 300);
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

        // Style labels
        ipLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #333333;");
        portLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #333333;");

        VBox centerBox = new VBox(10, ipLabel, portLabel);
        centerBox.setAlignment(Pos.CENTER);

        Button startButton = new Button("Start");
        startButton.setStyle("-fx-background-color: #FF5722; -fx-text-fill: white; -fx-font-size: 14px;");

        startButton.setOnAction(e -> {
            GameClient.serverIP = "localhost";
            GameClient.serverPort = 12345;
            new Thread(() -> Platform.runLater(() -> {
                try {
                    new GameClient().start(new Stage());
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            })).start();
            primaryStage.close();
        });

        BorderPane hostPane = new BorderPane();
        hostPane.setCenter(centerBox);
        hostPane.setStyle("-fx-background-color: #ffffff;");

        HBox bottomBox = new HBox(startButton);
        bottomBox.setAlignment(Pos.BOTTOM_RIGHT);
        bottomBox.setPadding(new Insets(10));
        hostPane.setBottom(bottomBox); // Note: 'customBox' seems to be a typo; should be 'bottomBox'

        Scene hostScene = new Scene(hostPane, 400, 300);
        primaryStage.setScene(hostScene);
    }

    private void showJoinScreen(Stage primaryStage) {
        Label ipPrompt = new Label("Enter Host IP:");
        TextField ipField = new TextField();
        Label portPrompt = new Label("Enter Port:");
        TextField portField = new TextField();

        // Style labels and text fields
        ipPrompt.setStyle("-fx-font-size: 14px; -fx-text-fill: #333333;");
        portPrompt.setStyle("-fx-font-size: 14px; -fx-text-fill: #333333;");
        ipField.setStyle("-fx-pref-width: 150px; -fx-background-color: #f9f9f9; -fx-border-color: #cccccc;");
        portField.setStyle("-fx-pref-width: 150px; -fx-background-color: #f9f9f9; -fx-border-color: #cccccc;");

        VBox centerBox = new VBox(10, ipPrompt, ipField, portPrompt, portField);
        centerBox.setAlignment(Pos.CENTER);

        Button joinButton = new Button("Join");
        joinButton.setStyle("-fx-background-color: #9C27B0; -fx-text-fill: white; -fx-font-size: 14px;");

        joinButton.setOnAction(e -> {
            String ip = ipField.getText().trim();
            int port = 12345;
            try {
                port = Integer.parseInt(portField.getText().trim());
            } catch (NumberFormatException ex) {
                // Add error handling if desired
            }
            GameClient.serverIP = ip;
            GameClient.serverPort = port;
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
        joinPane.setStyle("-fx-background-color: #ffffff;");

        HBox bottomBox = new HBox(joinButton);
        bottomBox.setAlignment(Pos.BOTTOM_RIGHT);
        bottomBox.setPadding(new Insets(10));
        joinPane.setBottom(bottomBox); // Note: 'customBox' should be 'bottomBox'

        Scene joinScene = new Scene(joinPane, 400, 300);
        primaryStage.setScene(joinScene);
    }

    public static void main(String[] args) {
        launch(args);
    }
}