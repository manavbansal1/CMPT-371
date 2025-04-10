package com.project.cmpt371;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * The GameServer class manages the server-side logic for the Team Box Conquest game.
 * It handles client connections, game state management, player team assignments,
 * and the core game mechanics including the shared grid state and win conditions.
 */
public class GameServer {
    /** Server port number */
    private static final int PORT = 12345;
    
    /** Size of the game grid (10x10) */
    private static final int GRID_SIZE = 10;
    
    /** Maximum number of players allowed per team */
    private static final int MAX_PLAYERS_PER_TEAM = 3;
    
    /** Maximum total number of players allowed (across all teams) */
    private static final int MAX_TOTAL_PLAYERS = 6;
    
    /** Map of client IDs to their handlers */
    private static Map<String, ClientHandler> clients = new HashMap<>();
    
    /** Current ownership state of each square on the board */
    private static String[][] boardState = new String[GRID_SIZE][GRID_SIZE];
    
    /** Maps each grid cell to teams currently holding it and their count */
    private static Map<String, Integer>[][] heldState = new HashMap[GRID_SIZE][GRID_SIZE];
    
    /** Thread pool for handling multiple client connections */
    private static final ExecutorService executorService = Executors.newFixedThreadPool(10);
    
    /** Scheduled executor service for managing claim timers */
    private static final ScheduledExecutorService timerService = Executors.newScheduledThreadPool(1);
    
    /** Map of grid coordinates to their claim timers */
    private static Map<String, ScheduledFuture<?>> claimTimers = new HashMap<>();
    
    /** Count of players on Team A */
    private static int teamACount = 0;
    
    /** Count of players on Team B */
    private static int teamBCount = 0;
    
    /** Counter for generating unique client IDs */
    private static int clientCounter = 0;
    
    /** List of player names on Team A */
    private static List<String> teamAPlayers = new ArrayList<>();
    
    /** List of player names on Team B */
    private static List<String> teamBPlayers = new ArrayList<>();
    
    /**
     * Main method that initializes and starts the game server.
     * Sets up the initial board state and listens for client connections.
     *
     * @param args Command line arguments (not used)
     */
    public static void main(String[] args) {
        resetBoard();
        System.out.println("Game Server started on port " + PORT + "...");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                // Accept new client connection
                Socket clientSocket = serverSocket.accept();
                String clientId = "Client_" + clientCounter++;
                System.out.println("New client connected: " + clientSocket.getInetAddress() + ":" + 
                        clientSocket.getPort() + " as " + clientId);

                // Check if server is at capacity
                synchronized (clients) {
                    if (clients.size() >= MAX_TOTAL_PLAYERS) {
                        try (DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {
                            out.writeUTF("SERVER_FULL");
                            clientSocket.close();
                        }
                        continue;
                    }
                }

                // Create and register a new client handler
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

    /**
     * Resets the game board and player state to initial values.
     * Called at server start and after each game ends.
     */
    private static void resetBoard() {
        // Reset grid state
        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                boardState[row][col] = "UNCLAIMED";
                heldState[row][col] = new HashMap<>();
            }
        }
        
        // Reset team data
        teamAPlayers.clear();
        teamBPlayers.clear();
        teamACount = 0;
        teamBCount = 0;
        
        // Reset client tracking
        clients.clear();
        clientCounter = 0;
        
        // Cancel any active timers
        claimTimers.clear();
    }

    /**
     * Broadcasts the current game board state to all connected clients.
     *
     * @throws IOException If there's an error sending the game state
     */
    private static void broadcastGameState() throws IOException {
        synchronized (clients) {
            for (ClientHandler clientHandler : clients.values()) {
                clientHandler.sendGameState(boardState);
            }
        }
    }

    /**
     * Processes a client's request to hold (start claiming) a grid square.
     * Implements the logic for tracking which players are holding each square
     * and manages the claim timer.
     *
     * @param client The client handler for the player making the request
     * @param row The row of the square
     * @param col The column of the square
     * @throws IOException If there's an error broadcasting updates
     */
    private static void handleHoldRequest(ClientHandler client, int row, int col) throws IOException {
        synchronized (boardState) {
            // Only allow interaction with unclaimed squares
            if ("UNCLAIMED".equals(boardState[row][col])) {
                String team = client.getTeam();
                Map<String, Integer> holdMap = heldState[row][col];
                
                // Increment the count for this team
                int newCount = holdMap.getOrDefault(team, 0) + 1;
                holdMap.put(team, newCount);

                // If this is the first player from this team to hold the square
                if (newCount == 1) {
                    broadcastHoldInfo(row, col, team);
                    
                    // Manage the claim timer based on team count
                    if (holdMap.size() == 1) {
                        // Only one team is holding - start claim timer
                        scheduleClaimTimer(row, col);
                    } else if (holdMap.size() > 1) {
                        // Multiple teams are holding (tug-of-war) - cancel timer
                        cancelClaimTimer(row, col);
                    }
                }
            }
        }
    }

    /**
     * Processes a client's request to release a grid square they were holding.
     * Handles the "tug-of-war" mechanics when multiple teams contest a square.
     *
     * @param client The client handler for the player making the request
     * @param row The row of the square
     * @param col The column of the square
     * @throws IOException If there's an error broadcasting updates
     */
    private static void handleReleaseRequest(ClientHandler client, int row, int col) throws IOException {
        synchronized (boardState) {
            String team = client.getTeam();
            Map<String, Integer> holdMap = heldState[row][col];
            
            if (holdMap.containsKey(team)) {
                int count = holdMap.get(team);
                if (count > 1) {
                    // Multiple players from this team are holding - decrement count
                    holdMap.put(team, count - 1);
                } else {
                    // Last player from this team is releasing - remove team
                    holdMap.remove(team);
                    broadcastReleaseInfo(row, col, team);
                    
                    // Handle contested square resolution (tug-of-war)
                    if (holdMap.size() == 1) {
                        // Only one team left - they win the square immediately
                        String winningTeam = holdMap.keySet().iterator().next();
                        boardState[row][col] = winningTeam;
                        heldState[row][col].clear();
                        cancelClaimTimer(row, col);
                        broadcastGameState();
                        checkWinCondition(null);
                        broadcastTeamScores();
                    } else if (holdMap.size() == 0) {
                        // No teams holding - cancel any timer
                        cancelClaimTimer(row, col);
                    } else {
                        // This shouldn't happen, but handle it anyway
                        cancelClaimTimer(row, col);
                    }
                }
            }
        }
    }

    /**
     * Schedules a timer for claiming a square after the required hold period (2 seconds).
     * The timer only executes if one team is still holding when it expires.
     *
     * @param row The row of the square
     * @param col The column of the square
     */
    private static void scheduleClaimTimer(int row, int col) {
        String key = row + "," + col;
        ScheduledFuture<?> future = timerService.schedule(() -> {
            synchronized (boardState) {
                Map<String, Integer> holdMap = heldState[row][col];
                if (holdMap.size() == 1) {
                    // Timer completed - award square to the holding team
                    String team = holdMap.keySet().iterator().next();
                    boardState[row][col] = team;
                    heldState[row][col].clear();
                    try {
                        // Broadcast updates and check for win
                        broadcastGameState();
                        checkWinCondition(null);
                        broadcastTeamScores();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }, 2, TimeUnit.SECONDS);
        
        // Store the timer for potential cancellation
        claimTimers.put(key, future);
    }

    /**
     * Cancels an active claim timer for a grid square.
     * Used when a square becomes contested or when a claim completes.
     *
     * @param row The row of the square
     * @param col The column of the square
     */
    private static void cancelClaimTimer(int row, int col) {
        String key = row + "," + col;
        ScheduledFuture<?> future = claimTimers.remove(key);
        if (future != null) {
            future.cancel(false);
        }
    }

    /**
     * Broadcasts to all clients that a team has started holding a square.
     *
     * @param row The row of the square
     * @param col The column of the square
     * @param team The team that started holding
     */
    private static void broadcastHoldInfo(int row, int col, String team) {
        synchronized (clients) {
            for (ClientHandler clientHandler : clients.values()) {
                clientHandler.sendMessage("HOLD_START " + row + " " + col + " " + team);
            }
        }
    }

    /**
     * Broadcasts to all clients that a team has released a square.
     *
     * @param row The row of the square
     * @param col The column of the square
     * @param team The team that released
     */
    private static void broadcastReleaseInfo(int row, int col, String team) {
        synchronized (clients) {
            for (ClientHandler clientHandler : clients.values()) {
                clientHandler.sendMessage("HOLD_END " + row + " " + col + " " + team);
            }
        }
    }

    /**
     * Checks if either team has met the win condition.
     * A team wins by having 10 consecutive squares in any direction
     * or by having the most consecutive squares when the board is full.
     *
     * @param client The client that triggered the check (not used)
     */
    private static void checkWinCondition(ClientHandler client) {
        // Calculate longest consecutive sequences for each team
        int maxA = getMaxConsecutive("TEAM_A");
        int maxB = getMaxConsecutive("TEAM_B");

        // Check primary win condition - 10 consecutive squares
        if (maxA >= 10) {
            broadcastWinCondition("TEAM_A");
            resetBoard();
        } else if (maxB >= 10) {
            broadcastWinCondition("TEAM_B");
            resetBoard();
        } else if (isBoardFull()) {
            // Board is full - determine winner by longest sequence
            String winner = maxA > maxB ? "TEAM_A" : maxA < maxB ? "TEAM_B" : "TIE";
            broadcastWinCondition(winner);
            resetBoard();
        }
    }

    /**
     * Checks if the game board is completely filled (no unclaimed squares).
     *
     * @return true if the board is full, false otherwise
     */
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

    /**
     * Calculates the maximum number of consecutive squares claimed by a team.
     * Checks horizontal, vertical, and both diagonal directions.
     *
     * @param team The team to check ("TEAM_A" or "TEAM_B")
     * @return The maximum consecutive count
     */
    private static int getMaxConsecutive(String team) {
        int max = 0;
        
        // Check horizontal rows
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
        
        // Check vertical columns
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
        
        // Check diagonal (top-left to bottom-right), starting from leftmost column
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
        
        // Check diagonal (top-left to bottom-right), starting from top row
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
        
        // Check diagonal (top-right to bottom-left), starting from rightmost column
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
        
        // Check diagonal (top-right to bottom-left), starting from top row
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

    /**
     * Broadcasts the game over message to all clients with the winner information.
     *
     * @param winner The winning team ("TEAM_A", "TEAM_B", or "TIE")
     */
    private static void broadcastWinCondition(String winner) {
        synchronized (clients) {
            for (ClientHandler clientHandler : clients.values()) {
                clientHandler.sendMessage("GAME_OVER " + winner);
            }
        }
    }

    /**
     * Broadcasts the current team scores (longest consecutive sequences) to all clients.
     */
    private static void broadcastTeamScores() {
        int maxA = getMaxConsecutive("TEAM_A");
        int maxB = getMaxConsecutive("TEAM_B");
        synchronized (clients) {
            for (ClientHandler clientHandler : clients.values()) {
                clientHandler.sendMessage("TEAM_SCORES " + maxA + " " + maxB);
            }
        }
    }

    /**
     * Broadcasts the current team player lists to all clients.
     */
    private static void broadcastTeamLists() {
        String teamAList = String.join(",", teamAPlayers);
        String teamBList = String.join(",", teamBPlayers);
        synchronized (clients) {
            for (ClientHandler clientHandler : clients.values()) {
                clientHandler.sendMessage("TEAM_LISTS " + teamAList + " " + teamBList);
            }
        }
    }

    /**
     * The ClientHandler class manages communication with a single connected client.
     * It processes incoming messages and manages the client's state in the game.
     */
    static class ClientHandler implements Runnable {
        /** Socket for communication with the client */
        private Socket socket;
        
        /** Input stream for receiving messages from the client */
        private DataInputStream inputStream;
        
        /** Output stream for sending messages to the client */
        private DataOutputStream outputStream;
        
        /** The team assigned to this client */
        private String team;
        
        /** The player's name */
        private String playerName;
        
        /** Unique identifier for this client */
        private String clientId;

        /**
         * Creates a new client handler for the given socket and ID.
         *
         * @param socket The client socket
         * @param clientId The unique client identifier
         */
        public ClientHandler(Socket socket, String clientId) {
            this.socket = socket;
            this.clientId = clientId;
        }

        /**
         * Main processing loop for client messages.
         * Handles initial connection setup and subsequent game actions.
         */
        @Override
        public void run() {
            try {
                // Set up data streams
                inputStream = new DataInputStream(socket.getInputStream());
                outputStream = new DataOutputStream(socket.getOutputStream());

                // Handle initial message
                String initMessage = inputStream.readUTF();
                
                // Check if this is a capacity check or team status request
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

                // Process player information and team assignment
                if (initMessage.startsWith("PLAYER_INFO")) {
                    String[] parts = initMessage.split(" ");
                    playerName = parts[1];
                    String requestedTeam = parts[2];

                    // Assign player to requested team if space available
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
                            // Team full or invalid request
                            sendMessage("TEAM_FULL");
                            socket.close();
                            return;
                        }
                    }
                    
                    // Notify clients of new player
                    System.out.println(clientId + " (" + playerName + ") assigned to " + team);
                    sendMessage("TEAM_ASSIGNMENT " + team + " " + playerName);
                    broadcastMessage("CHAT " + playerName + " connected");
                    broadcastTeamLists();
                    sendGameState(boardState);
                    sendInitialHeldState();
                    broadcastTeamScores();
                }

                // Main message processing loop
                while (true) {
                    String message = inputStream.readUTF();
                    
                    // Process message based on type
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
                // Clean up resources
                try {
                    if (!socket.isClosed()) {
                        socket.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                
                // Remove client and update team counts
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

        /**
         * Gets the team assigned to this client.
         *
         * @return The team ID ("TEAM_A" or "TEAM_B")
         */
        public String getTeam() {
            return team;
        }

        /**
         * Sends a message to this client.
         *
         * @param message The message to send
         */
        public void sendMessage(String message) {
            try {
                outputStream.writeUTF(message);
                outputStream.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /**
         * Broadcasts a message to all connected clients.
         *
         * @param message The message to broadcast
         */
        private void broadcastMessage(String message) {
            synchronized (clients) {
                for (ClientHandler client : clients.values()) {
                    client.sendMessage(message);
                }
            }
        }

        /**
         * Sends the current game state to this client.
         *
         * @param gameState The current board state
         * @throws IOException If sending fails
         */
        public void sendGameState(String[][] gameState) throws IOException {
            StringBuilder sb = new StringBuilder("GAME_STATE ");
            for (int row = 0; row < GRID_SIZE; row++) {
                for (int col = 0; col < GRID_SIZE; col++) {
                    sb.append(gameState[row][col]).append(" ");
                }
            }
            sendMessage(sb.toString().trim());
        }

        /**
         * Sends the initial held state of the board to a newly connected client.
         *
         * @throws IOException If sending fails
         */
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