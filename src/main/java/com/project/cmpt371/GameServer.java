package com.project.cmpt371;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class GameServer {
    private static final int PORT = 12345;
    private static final int GRID_SIZE = 10;
    private static final Map<Socket, ClientHandler> clients = new HashMap<>();
    private static String[][] boardState = new String[GRID_SIZE][GRID_SIZE];
    private static final ExecutorService executorService = Executors.newFixedThreadPool(10);

    public static void main(String[] args) {
        // Initialize the board state as "UNCLAIMED"
        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                boardState[row][col] = "UNCLAIMED";
            }
        }

        System.out.println("Game Server started...");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress());
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                executorService.submit(clientHandler);
                synchronized (clients) {
                    clients.put(clientSocket, clientHandler);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void broadcastGameState() throws IOException {
        synchronized (clients) {
            for (ClientHandler clientHandler : clients.values()) {
                clientHandler.sendGameState(boardState);
            }
        }
    }

    private static void handleClaimRequest(ClientHandler client, int row, int col) throws IOException {
        if ("UNCLAIMED".equals(boardState[row][col])) {
            // Update the board state and notify other players
            boardState[row][col] = client.getTeam() == 1 ? "TEAM_A" : "TEAM_B";
            broadcastGameState();
            checkWinCondition(client);
        }
    }

    private static void checkWinCondition(ClientHandler client) {
        // Simple horizontal check for 10 consecutive squares (extend with vertical, diagonal)
        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col <= GRID_SIZE - 10; col++) {
                if (isConsecutive(row, col, 0, 1)) {
                    broadcastWinCondition(client);
                    return;
                }
            }
        }
    }

    private static boolean isConsecutive(int row, int col, int rowDelta, int colDelta) {
        String team = boardState[row][col];
        if ("UNCLAIMED".equals(team)) return false;

        for (int i = 1; i < 10; i++) {
            int nextRow = row + i * rowDelta;
            int nextCol = col + i * colDelta;
            if (nextRow >= GRID_SIZE || nextCol >= GRID_SIZE || !boardState[nextRow][nextCol].equals(team)) {
                return false;
            }
        }
        return true;
    }

    private static void broadcastWinCondition(ClientHandler winningClient) {
        synchronized (clients) {
            for (ClientHandler clientHandler : clients.values()) {
                clientHandler.sendMessage("GAME_OVER " + (winningClient.getTeam() == 1 ? "TEAM_A" : "TEAM_B") + " WINS!");
            }
        }
    }

    static class ClientHandler implements Runnable {
        private Socket socket;
        private DataInputStream inputStream;
        private DataOutputStream outputStream;
        private int team;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                inputStream = new DataInputStream(socket.getInputStream());
                outputStream = new DataOutputStream(socket.getOutputStream());

                // Assign team to player
                team = (clients.size() % 2 == 0) ? 1 : 2;
                sendMessage("TEAM_ASSIGNMENT " + team);

                // Notify the game state to the player
                sendGameState(boardState);

                // Handle incoming messages
                while (true) {
                    String message = inputStream.readUTF();
                    if (message.startsWith("CLAIM_REQUEST")) {
                        int row = Integer.parseInt(message.split(" ")[1]);
                        int col = Integer.parseInt(message.split(" ")[2]);
                        handleClaimRequest(this, row, col);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                synchronized (clients) {
                    clients.remove(socket);
                }
            }
        }

        public int getTeam() {
            return team;
        }

        public void sendMessage(String message) {
            try {
                outputStream.writeUTF(message);
            } catch (IOException e) {
                e.printStackTrace();
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
    }
}
