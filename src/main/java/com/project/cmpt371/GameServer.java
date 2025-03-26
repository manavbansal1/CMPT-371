package com.project.cmpt371;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class GameServer {
    private static final int PORT = 12345;
    private static final int GRID_SIZE = 10;
    private static final int MAX_PLAYERS_PER_TEAM = 3;
    private static final int MAX_TOTAL_PLAYERS = 6;
    private static Map<String, ClientHandler> clients = new HashMap<>();
    private static String[][] boardState = new String[GRID_SIZE][GRID_SIZE];
    private static String[][] heldState = new String[GRID_SIZE][GRID_SIZE]; // Tracks who is holding a block
    private static final ExecutorService executorService = Executors.newFixedThreadPool(10);
    private static int teamACount = 0;
    private static int teamBCount = 0;
    private static int clientCounter = 0;
    private static List<String> teamAPlayers = new ArrayList<>();
    private static List<String> teamBPlayers = new ArrayList<>();

    public static void main(String[] args) {
        resetBoard();
        System.out.println("Game Server started on port " + PORT + "...");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                String clientId = "Client_" + clientCounter++;
                System.out.println("New client connected: " + clientSocket.getInetAddress() + ":" + clientSocket.getPort() + " as " + clientId);

                synchronized (clients) {
                    if (clients.size() >= MAX_TOTAL_PLAYERS) {
                        try (DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {
                            out.writeUTF("SERVER_FULL");
                            clientSocket.close();
                        }
                        continue;
                    }
                }

                ClientHandler clientHandler = new ClientHandler(clientSocket, clientId);
                executorService.submit(clientHandler);
                synchronized (clients) {
                    clients.put(clientId, clientHandler);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void resetBoard() {
        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                boardState[row][col] = "UNCLAIMED";
                heldState[row][col] = null; // Null means not held
            }
        }
        teamAPlayers.clear();
        teamBPlayers.clear();
        teamACount = 0;
        teamBCount = 0;
        clients.clear();
        clientCounter = 0;
    }

    private static void broadcastGameState() throws IOException {
        synchronized (clients) {
            for (ClientHandler clientHandler : clients.values()) {
                clientHandler.sendGameState(boardState);
                clientHandler.sendHeldState(heldState);
            }
        }
    }

    private static void handleClaimRequest(ClientHandler client, int row, int col) throws IOException {
        synchronized (boardState) {
            if ("UNCLAIMED".equals(boardState[row][col]) && heldState[row][col] != null && heldState[row][col].equals(client.clientId)) {
                boardState[row][col] = client.getTeam().equals("TEAM_A") ? "TEAM_A" : "TEAM_B";
                heldState[row][col] = null; // Release hold after claim
                broadcastGameState();
                checkWinCondition(client);
                broadcastTeamScores();
            }
        }
    }

    private static void handleHoldRequest(ClientHandler client, int row, int col) throws IOException {
        synchronized (boardState) {
            if ("UNCLAIMED".equals(boardState[row][col]) && heldState[row][col] == null) {
                heldState[row][col] = client.clientId;
                broadcastGameState();
            }
        }
    }

    private static void handleReleaseRequest(ClientHandler client, int row, int col) throws IOException {
        synchronized (boardState) {
            if (heldState[row][col] != null && heldState[row][col].equals(client.clientId)) {
                heldState[row][col] = null;
                broadcastGameState();
            }
        }
    }

    private static void checkWinCondition(ClientHandler client) {
        int maxA = getMaxConsecutive("TEAM_A");
        int maxB = getMaxConsecutive("TEAM_B");

        if (maxA >= 10) {
            broadcastWinCondition("TEAM_A");
            resetBoard();
        } else if (maxB >= 10) {
            broadcastWinCondition("TEAM_B");
            resetBoard();
        } else if (isBoardFull()) {
            String winner = maxA > maxB ? "TEAM_A" : maxA < maxB ? "TEAM_B" : "TIE";
            broadcastWinCondition(winner);
            resetBoard();
        }
    }

    private static boolean isBoardFull() {
        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                if ("UNCLAIMED".equals(boardState[row][col])) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int getMaxConsecutive(String team) {
        int max = 0;
        // Horizontal
        for (int row = 0; row < GRID_SIZE; row++) {
            int count = 0;
            for (int col = 0; col < GRID_SIZE; col++) {
                if (boardState[row][col].equals(team)) {
                    count++;
                    max = Math.max(max, count);
                } else {
                    count = 0;
                }
            }
        }
        // Vertical
        for (int col = 0; col < GRID_SIZE; col++) {
            int count = 0;
            for (int row = 0; row < GRID_SIZE; row++) {
                if (boardState[row][col].equals(team)) {
                    count++;
                    max = Math.max(max, count);
                } else {
                    count = 0;
                }
            }
        }
        // Diagonal (top-left to bottom-right)
        for (int startRow = 0; startRow < GRID_SIZE; startRow++) {
            int count = 0;
            for (int row = startRow, col = 0; row < GRID_SIZE && col < GRID_SIZE; row++, col++) {
                if (boardState[row][col].equals(team)) {
                    count++;
                    max = Math.max(max, count);
                } else {
                    count = 0;
                }
            }
        }
        for (int startCol = 1; startCol < GRID_SIZE; startCol++) {
            int count = 0;
            for (int row = 0, col = startCol; row < GRID_SIZE && col < GRID_SIZE; row++, col++) {
                if (boardState[row][col].equals(team)) {
                    count++;
                    max = Math.max(max, count);
                } else {
                    count = 0;
                }
            }
        }
        // Diagonal (top-right to bottom-left)
        for (int startRow = 0; startRow < GRID_SIZE; startRow++) {
            int count = 0;
            for (int row = startRow, col = GRID_SIZE - 1; row < GRID_SIZE && col >= 0; row++, col--) {
                if (boardState[row][col].equals(team)) {
                    count++;
                    max = Math.max(max, count);
                } else {
                    count = 0;
                }
            }
        }
        for (int startCol = GRID_SIZE - 2; startCol >= 0; startCol--) {
            int count = 0;
            for (int row = 0, col = startCol; row < GRID_SIZE && col >= 0; row++, col--) {
                if (boardState[row][col].equals(team)) {
                    count++;
                    max = Math.max(max, count);
                } else {
                    count = 0;
                }
            }
        }
        return max;
    }

    private static void broadcastWinCondition(String winner) {
        synchronized (clients) {
            for (ClientHandler clientHandler : clients.values()) {
                clientHandler.sendMessage("GAME_OVER " + winner);
            }
        }
    }

    private static void broadcastTeamScores() {
        int maxA = getMaxConsecutive("TEAM_A");
        int maxB = getMaxConsecutive("TEAM_B");
        synchronized (clients) {
            for (ClientHandler clientHandler : clients.values()) {
                clientHandler.sendMessage("TEAM_SCORES " + maxA + " " + maxB);
            }
        }
    }

    private static void broadcastTeamLists() {
        String teamAList = String.join(",", teamAPlayers);
        String teamBList = String.join(",", teamBPlayers);
        synchronized (clients) {
            for (ClientHandler clientHandler : clients.values()) {
                clientHandler.sendMessage("TEAM_LISTS " + teamAList + " " + teamBList);
            }
        }
    }

    static class ClientHandler implements Runnable {
        private Socket socket;
        private DataInputStream inputStream;
        private DataOutputStream outputStream;
        private String team;
        private String playerName;
        private String clientId;

        public ClientHandler(Socket socket, String clientId) {
            this.socket = socket;
            this.clientId = clientId;
        }

        @Override
        public void run() {
            try {
                inputStream = new DataInputStream(socket.getInputStream());
                outputStream = new DataOutputStream(socket.getOutputStream());

                String initMessage = inputStream.readUTF();
                if (initMessage.equals("CHECK_CAPACITY")) {
                    synchronized (clients) {
                        outputStream.writeUTF(clients.size() < MAX_TOTAL_PLAYERS ? "OK" : "SERVER_FULL");
                    }
                    socket.close();
                    return;
                } else if (initMessage.equals("TEAM_STATUS_REQUEST")) {
                    synchronized (GameServer.class) {
                        outputStream.writeUTF("TEAM_STATUS " + teamACount + " " + teamBCount);
                    }
                    socket.close();
                    return;
                }

                if (initMessage.startsWith("PLAYER_INFO")) {
                    String[] parts = initMessage.split(" ");
                    playerName = parts[1];
                    String requestedTeam = parts[2];

                    synchronized (GameServer.class) {
                        if (requestedTeam.equals("TEAM_A") && teamACount < MAX_PLAYERS_PER_TEAM) {
                            team = "TEAM_A";
                            teamACount++;
                            teamAPlayers.add(playerName);
                        } else if (requestedTeam.equals("TEAM_B") && teamBCount < MAX_PLAYERS_PER_TEAM) {
                            team = "TEAM_B";
                            teamBCount++;
                            teamBPlayers.add(playerName);
                        } else {
                            sendMessage("TEAM_FULL");
                            socket.close();
                            return;
                        }
                    }
                    System.out.println(clientId + " (" + playerName + ") assigned to " + team + ". Team A: " + teamACount + ", Team B: " + teamBCount);
                    sendMessage("TEAM_ASSIGNMENT " + team + " " + playerName);
                    broadcastMessage("CHAT " + playerName + " connected");
                    broadcastTeamLists();
                }

                sendGameState(boardState);
                broadcastTeamScores();

                while (true) {
                    String message = inputStream.readUTF();
                    if (message.startsWith("CLAIM_REQUEST")) {
                        int row = Integer.parseInt(message.split(" ")[1]);
                        int col = Integer.parseInt(message.split(" ")[2]);
                        handleClaimRequest(this, row, col);
                    } else if (message.startsWith("HOLD_START")) {
                        int row = Integer.parseInt(message.split(" ")[1]);
                        int col = Integer.parseInt(message.split(" ")[2]);
                        handleHoldRequest(this, row, col);
                    } else if (message.startsWith("HOLD_END")) {
                        int row = Integer.parseInt(message.split(" ")[1]);
                        int col = Integer.parseInt(message.split(" ")[2]);
                        handleReleaseRequest(this, row, col);
                    } else if (message.startsWith("CHAT")) {
                        String chatMsg = message.substring(5);
                        broadcastMessage("CHAT " + playerName + ": " + chatMsg);
                    }
                }
            } catch (IOException e) {
                System.out.println(clientId + " (" + playerName + ") disconnected: " + e.getMessage());
            } finally {
                try {
                    if (!socket.isClosed()) {
                        socket.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                synchronized (clients) {
                    clients.remove(clientId);
                    if (team != null) {
                        if ("TEAM_A".equals(team)) {
                            teamACount--;
                            teamAPlayers.remove(playerName);
                        } else if ("TEAM_B".equals(team)) {
                            teamBCount--;
                            teamBPlayers.remove(playerName);
                        }
                        System.out.println("Player " + playerName + " left team " + team + ". Team A: " + teamACount + ", Team B: " + teamBCount);
                        broadcastMessage("CHAT " + playerName + " disconnected");
                        broadcastTeamLists();
                        broadcastTeamScores();
                    } else {
                        System.out.println(clientId + " disconnected before team assignment.");
                    }
                }
            }
        }

        public String getTeam() {
            return team;
        }

        public void sendMessage(String message) {
            try {
                outputStream.writeUTF(message);
                outputStream.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void broadcastMessage(String message) {
            synchronized (clients) {
                for (ClientHandler client : clients.values()) {
                    client.sendMessage(message);
                }
            }
        }

        public void sendGameState(String[][] gameState) throws IOException {
            StringBuilder sb = new StringBuilder("GAME_STATE ");
            for (int row = 0; row < GRID_SIZE; row++) {
                for (int col = 0; col < GRID_SIZE; col++) {
                    sb.append(gameState[row][col]).append(" ");
                }
            }
            sendMessage(sb.toString());
        }

        public void sendHeldState(String[][] heldState) throws IOException {
            StringBuilder sb = new StringBuilder("HELD_STATE ");
            for (int row = 0; row < GRID_SIZE; row++) {
                for (int col = 0; col < GRID_SIZE; col++) {
                    sb.append(heldState[row][col] == null ? "NONE" : heldState[row][col]).append(" ");
                }
            }
            sendMessage(sb.toString());
        }
    }
}