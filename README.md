# Team Box Conquest

## Introduction
Team Box Conquest is a team-based multiplayer game developed for CMPT 371 Spring 2025. The game follows a client-server architecture where two teams compete to claim squares on a game board by holding them for a specified duration.

## Game Rules
- The game board consists of a 10×10 grid of unclaimed squares.
- Players are divided into two teams, with each player representing their team with a team flag.
- Players simultaneously attempt to claim unclaimed squares:
  1. Left-click on an unclaimed square to attempt to claim it.
  2. Hold the left mouse button for 2 seconds to successfully claim the square for your team.
  3. If you release before 2 seconds, the square remains unclaimed and available for others.
- While a player is attempting to claim a square, that square is locked and unavailable to other players.
- All players can see in real-time what squares other players are attempting to claim.
- The game ends when either:
  1. A team connects 10 consecutive squares in a row, column, or diagonal (similar to tic-tac-toe).
  2. All squares have been claimed, in which case the team with the most consecutive squares in any direction wins.

## Technical Architecture

### Client-Server Model
This game uses a socket-based client-server architecture:
- One player can start a server.
- All players (including the server starter) connect to the server as clients.
- The server manages game state, player teams, handles connections, and enforces game rules.

### Shared Object Management
Each square on the game board is a shared object that requires locking for concurrency control:
- When a player begins claiming a square, the client sends a "lock request" to the server.
- The server grants or denies the lock based on the square's availability.
- While locked, no other player can attempt to claim that square.
- The server tracks the 2-second holding period for each claim attempt.
- When a square is successfully claimed or a claim attempt is abandoned, the lock is released.

### Network Communication
All communication is implemented using raw sockets:
- Custom application-layer messaging protocol
- No third-party networking libraries or frameworks
- Direct socket programming for all client-server communication

## Application-Layer Messaging Protocol
Our game uses the following message types:

1. **Connection Messages**
   - `CONNECT`: Client requests to join the game
   - `CONNECT_ACK`: Server acknowledges connection and assigns player ID and team
   - `TEAM_ASSIGNMENT`: Server assigns player to a team (balanced teams)

2. **Game State Messages**
   - `GAME_STATE`: Server broadcasts current board state to all players
   - `PLAYER_LIST`: Server broadcasts list of connected players and their teams

3. **Action Messages**
   - `CLAIM_REQUEST`: Client requests to start claiming a square
   - `CLAIM_RESPONSE`: Server grants or denies claim request
   - `CLAIM_PROGRESS`: Server broadcasts claim progress (timer updates)
   - `CLAIM_SUCCESSFUL`: Server broadcasts when a square is successfully claimed
   - `CLAIM_ABANDONED`: Server broadcasts when a claim attempt is abandoned
   - `CLAIM_CANCEL`: Client cancels an in-progress claim attempt

4. **Game Flow Messages**
   - `GAME_START`: Server notifies clients that the game has started
   - `GAME_END`: Server notifies clients of game end and winning team
   - `CONSECUTIVE_UPDATE`: Server broadcasts current longest consecutive sequence for each team

## Implementation Details

### Server Implementation
The server handles:
- Player connection and team assignment
- Real-time game state synchronization
- Square locking mechanism
- 2-second hold timer validation
- Consecutive square detection
- Win condition checking

### Client Implementation
The client handles:
- User interface and rendering game state
- Sending player actions to the server
- Visual feedback for claim attempts
- Displaying real-time updates of other players' actions

### Concurrency Control
- The server maintains a lock state for each square on the board.
- When a player attempts to claim a square, the server verifies its availability.
- If available, the server locks the square and starts a 2-second timer.
- The lock prevents race conditions where multiple players try to claim the same square.
- All clients are notified of lock states to provide visual feedback.
