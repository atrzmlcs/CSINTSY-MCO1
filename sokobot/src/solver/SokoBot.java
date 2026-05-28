package solver;

import java.util.HashSet;
import java.util.Set;

public class SokoBot {

    // Global lookups for static components (Walls and Targets never move during simulation)
    private Set<Point> walls = new HashSet<>();
    private Set<Point> goals = new HashSet<>();

    public String solveSokobanPuzzle(int width, int height, char[][] mapData, char[][] itemsData) {
        // Clear any structural data from prior puzzle executions
        walls.clear();
        goals.clear();

        Point initialPlayer = null;
        Set<Point> initialBoxes = new HashSet<>();

        // ==========================================
        // STEP 3: PARSING THE MAP AND ITEMS DATA
        // ==========================================
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                
                // 1. Process static background topography from mapData
                if (mapData[r][c] == '#') {
                    walls.add(new Point(c, r));
                } else if (mapData[r][c] == '.') {
                    goals.add(new Point(c, r));
                }

                // 2. Process dynamic entity objects from itemsData
                if (itemsData[r][c] == '@') {
                    initialPlayer = new Point(c, r);
                } else if (itemsData[r][c] == '$') {
                    initialBoxes.add(new Point(c, r));
                } else if (itemsData[r][c] == '*') {
                    // Overlapping case: A box sitting squarely on top of a goal tile
                    initialBoxes.add(new Point(c, r));
                    goals.add(new Point(c, r));
                }
            }
        }

        // Establish the root snapshot state of the puzzle board
        BoardState startState = new BoardState(initialPlayer, initialBoxes, "");

        // ==========================================
        // STEP 4: TRIGGER SEARCH ALGORITHM
        // ==========================================
        return runSearchAlgorithm(startState);
    }

    /**
     * Engine for graph traversal pathfinding logic.
     * We will build out the final move explorer queue here in Step 4.
     */
    /**
     * Engine for graph traversal pathfinding logic using a Breadth-First Search (BFS).
     * Explores valid board moves systematically to guarantee the shortest solution string.
     */
    private String runSearchAlgorithm(BoardState startState) {
        // Track unique board layouts we have already explored to prevent infinite loops
        Set<BoardState> visited = new HashSet<>();
        
        // Queue to manage FIFO (First-In, First-Out) node exploration
        java.util.Queue<BoardState> queue = new java.util.LinkedList<>();
        
        // Initialize search boundaries
        queue.add(startState);
        visited.add(startState);

        // Movement offsets paired with matching character command outputs
        // Format: {deltaX, deltaY, movementCharacter}
        int[][] directions = {
            {0, -1, 'u'}, // Up
            {0, 1, 'd'},  // Down
            {-1, 0, 'l'}, // Left
            {1, 0, 'r'}   // Right
        };

        while (!queue.isEmpty()) {
            BoardState current = queue.poll();

            // SUCCESS CONDITION: Check if all current boxes match target goal nodes
            if (goals.containsAll(current.boxes)) {
                return current.moveHistory;
            }

            // Evaluate all 4 possible paths from the current coordinates
            for (int[] dir : directions) {
                int dx = dir[0];
                int dy = dir[1];
                char moveChar = (char) dir[2];

                // Determine intended player target tile
                Point nextPlayer = new Point(current.player.x + dx, current.player.y + dy);

                // Invalid Step: Player walks straight into a structural wall
                if (walls.contains(nextPlayer)) {
                    continue;
                }

                // Interaction State: Player attempts to walk into a crate tile
                if (current.boxes.contains(nextPlayer)) {
                    // Calculate the position behind the crate
                    Point behindBox = new Point(nextPlayer.x + dx, nextPlayer.y + dy);

                    // Blocked: Crate would be pushed into a wall or another crate
                    if (walls.contains(behindBox) || current.boxes.contains(behindBox)) {
                        continue;
                    }

                    // Valid Push: Calculate updated crate cluster positioning
                    Set<Point> newBoxes = new HashSet<>(current.boxes);
                    newBoxes.remove(nextPlayer);   // Remove box from old spot
                    newBoxes.add(behindBox);       // Add box to new pushed spot

                    BoardState nextState = new BoardState(nextPlayer, newBoxes, current.moveHistory + moveChar);

                    if (!visited.contains(nextState)) {
                        visited.add(nextState);
                        queue.add(nextState);
                    }
                } 
                // Regular State: Player walks into an open empty floor tile
                else {
                    BoardState nextState = new BoardState(nextPlayer, current.boxes, current.moveHistory + moveChar);

                    if (!visited.contains(nextState)) {
                        visited.add(nextState);
                        queue.add(nextState);
                    }
                }
            }
        }

        // Return empty string if the board is mathematically impossible to clear
        return "";
    }
}

// ==========================================
// STEP 2: HELPER STRUCTURES (Package-Private)
// ==========================================

/**
 * Tracks unique coordinate coordinates on the 2D grid matrix.
 */
class Point {
    int x, y;
    
    Point(int x, int y) { 
        this.x = x; 
        this.y = y; 
    }
    
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Point)) return false;
        Point p = (Point) o;
        return this.x == p.x && this.y == p.y;
    }
    
    @Override
    public int hashCode() {
        return (x * 31) + y;
    }
}

/**
 * Captures a unique snapshot instance of mutable elements during lookaheads.
 */
class BoardState {
    Point player;
    Set<Point> boxes;
    String moveHistory; // String sequence of directions used to reach this state (e.g., "uulld")

    BoardState(Point player, Set<Point> boxes, String moveHistory) {
        this.player = player;
        this.boxes = new HashSet<>(boxes);
        this.moveHistory = moveHistory;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof BoardState)) return false;
        BoardState s = (BoardState) o;
        return this.player.equals(s.player) && this.boxes.equals(s.boxes);
    }

    @Override
    public int hashCode() {
        return player.hashCode() + boxes.hashCode();
    }
}