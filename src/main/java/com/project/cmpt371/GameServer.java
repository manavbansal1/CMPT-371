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
    private static Map<String, Integer>[][] heldState = new HashMap[GRID_SIZE][GRID_SIZE];
    private static final ExecutorService executorService = Executors.newFixedThreadPool(10);
    private static final ScheduledExecutorService timerService = Executors.newScheduledThreadPool(1);
    private static Map<String, ScheduledFuture<?>> claimTimers = new HashMap<>();
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
                heldState[row][col] = new HashMap<>();
            }
        }
        teamAPlayers.clear();
        teamBPlayers.clear();
        teamACount = 0;
        teamBCount = 0;
        clients.clear();
        clientCounter = 0;
        claimTimers.clear();
    }

    private static void broadcastGameState() throws IOException {
        synchronized (clients) {
            for (ClientHandler clientHandler : clients.values()) {
                clientHandler.sendGameState(boardState);
            }
        }
    }

    private static void handleHoldRequest(ClientHandler client, int row, int col) throws IOException {
        synchronized (boardState) {
            if ("UNCLAIMED".equals(boardState[row][col])) {
                String team = client.getTeam();
                Map<String, Integer> holdMap = heldState[row][col];
                int newCount = holdMap.getOrDefault(team, 0) + 1;
                holdMap.put(team, newCount);

                if (newCount == 1) {
                    broadcastHoldInfo(row, col, team);
                    // Only schedule claim timer if one team is holding
                    if (holdMap.size() == 1) {
                        scheduleClaimTimer(row, col);
                    } else if (holdMap.size() > 1) {
                        // Cancel timer when both teams are holding - tug of war mode
                        cancelClaimTimer(row, col);
                    }
                }
            }
        }
    }

    private static void handleReleaseRequest(ClientHandler client, int row, int col) throws IOException {
        synchronized (boardState) {
            String team = client.getTeam();
            Map<String, Integer> holdMap = heldState[row][col];
            
            if (holdMap.containsKey(team)) {
                int count = holdMap.get(team);
                if (count > 1) {
                    holdMap.put(team, count - 1);
                } else {
                    holdMap.remove(team);
                    broadcastReleaseInfo(row, col, team);
                    
                    // Check if this was a contested block (tug of war)
                    if (holdMap.size() == 1) {
                        // The remaining team wins the block immediately
                        String winningTeam = holdMap.keySet().iterator().next();
                        boardState[row][col] = winningTeam;
                        heldState[row][col].clear();
                        cancelClaimTimer(row, col);
                        broadcastGameState();
                        checkWinCondition(null);
                        broadcastTeamScores();
                    } else if (holdMap.size() == 0) {
                        // No teams holding, do nothing
                        cancelClaimTimer(row, col);
                    } else {
                        // This shouldn't happen, but handle it anyway
                        cancelClaimTimer(row, col);
                    }
                }
            }
        }
    }

    private static void scheduleClaimTimer(int row, int col) {
        String key = row + "," + col;
        ScheduledFuture<?> future = timerService.schedule(() -> {
            synchronized (boardState) {
                Map<String, Integer> holdMap = heldState[row][col];
                if (holdMap.size() == 1) {
                    String team = holdMap.keySet().iterator().next();
                    boardState[row][col] = team;
                    heldState[row][col].clear();
                    try {
                        broadcastGameState();
                        checkWinCondition(null);
                        broadcastTeamScores();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }, 2, TimeUnit.SECONDS);
        claimTimers.put(key, future);
    }

    private static void cancelClaimTimer(int row, int col) {
        String key = row + "," + col;
        ScheduledFuture<?> future = claimTimers.remove(key);
        if (future != null) {
            future.cancel(false);
        }
    }

    private static void broadcastHoldInfo(int row, int col, String team) {
        synchronized (clients) {
            for (ClientHandler clientHandler : clients.values()) {
                clientHandler.sendMessage("HOLD_START " + row + " " + col + " " + team);
            }
        }
    }

    private static void broadcastReleaseInfo(int row, int col, String team) {
        synchronized (clients) {
            for (ClientHandler clientHandler : clients.values()) {
                clientHandler.sendMessage("HOLD_END " + row + " " + col + " " + team);
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
                    System.out.println(clientId + " (" + playerName + ") assigned to " + team);
                    sendMessage("TEAM_ASSIGNMENT " + team + " " + playerName);
                    broadcastMessage("CHAT " + playerName + " connected");
                    broadcastTeamLists();
                    sendGameState(boardState);
                    sendInitialHeldState();
                    broadcastTeamScores();
                }

                while (true) {
                    String message = inputStream.readUTF();
                    if (message.startsWith("HOLD_START")) {
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
                        System.out.println("Player " + playerName + " left team " + team);
                        broadcastMessage("CHAT " + playerName + " disconnected");
                        broadcastTeamLists();
                        broadcastTeamScores();
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
            sendMessage(sb.toString().trim());
        }

        public void sendInitialHeldState() throws IOException {
            StringBuilder sb = new StringBuilder("INITIAL_HELD_STATE ");
            for (int row = 0; row < GRID_SIZE; row++) {
                for (int col = 0; col < GRID_SIZE; col++) {
                    Map<String, Integer> holdMap = heldState[row][col];
                    if (holdMap.isEmpty()) {
                        sb.append("NONE ");
                    } else {
                        sb.append(String.join(",", holdMap.keySet())).append(" ");
                    }
                }
            }
            sendMessage(sb.toString().trim());
        }
    }
}