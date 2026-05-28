package solver;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

public class SokoBot {

    // Topography lookups (Walls and Targets never move during simulation)
    private Set<Point> walls = new HashSet<>();
    private Set<Point> goals = new HashSet<>();

    public String solveSokobanPuzzle(int width, int height, char[][] mapData, char[][] itemsData) {
        // Reset lookups between level changes
        walls.clear();
        goals.clear();

        Point initialPlayer = null;
        Set<Point> initialBoxes = new HashSet<>();

        // Parse the professor's dual-grid system
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                if (mapData[r][c] == '#') {
                    walls.add(new Point(c, r));
                } else if (mapData[r][c] == '.') {
                    goals.add(new Point(c, r));
                }

                if (itemsData[r][c] == '@') {
                    initialPlayer = new Point(c, r);
                } else if (itemsData[r][c] == '$') {
                    initialBoxes.add(new Point(c, r));
                } else if (itemsData[r][c] == '*') {
                    initialBoxes.add(new Point(c, r));
                    goals.add(new Point(c, r));
                }
            }
        }

        BoardState startState = new BoardState(initialPlayer, initialBoxes, "");
        return runOptimizedBFS(startState);
    }

    /**
     * Reverts to the stable FIFO Queue structure that crushed the initial maps,
     * but protects it from infinite state explosions using a corner filter.
     */
    private String runOptimizedBFS(BoardState startState) {
        Set<BoardState> visited = new HashSet<>();
        Queue<BoardState> queue = new LinkedList<>();
        
        queue.add(startState);
        visited.add(startState);

        // Standard Sokoban directional rules
        int[][] directions = {
            {0, -1, 'u'}, // Up
            {0, 1, 'd'},  // Down
            {-1, 0, 'l'}, // Left
            {1, 0, 'r'}   // Right
        };

        while (!queue.isEmpty()) {
            BoardState current = queue.poll();

            // SUCCESS CONDITION: All boxes match target goal nodes
            if (goals.containsAll(current.boxes)) {
                return current.moveHistory;
            }

            for (int[] dir : directions) {
                int dx = dir[0];
                int dy = dir[1];
                char moveChar = (char) dir[2];

                Point nextPlayer = new Point(current.player.x + dx, current.player.y + dy);

                // Stop if the player walks directly into a wall
                if (walls.contains(nextPlayer)) {
                    continue; 
                }

                // Interaction: Player attempts to push a crate
                if (current.boxes.contains(nextPlayer)) {
                    Point behindBox = new Point(nextPlayer.x + dx, nextPlayer.y + dy);

                    // Blocked if the tile behind the box is a wall or another box
                    if (walls.contains(behindBox) || current.boxes.contains(behindBox)) {
                        continue; 
                    }

                    // DEADLOCK SHIELD: If pushing this box into a corner ruins the game,
                    // discard this entire board configuration immediately!
                    if (isDeadlocked(behindBox)) {
                        continue;
                    }

                    // Valid Push Configuration
                    Set<Point> newBoxes = new HashSet<>(current.boxes);
                    newBoxes.remove(nextPlayer);
                    newBoxes.add(behindBox);

                    BoardState nextState = new BoardState(nextPlayer, newBoxes, current.moveHistory + moveChar);
                    
                    if (!visited.contains(nextState)) {
                        visited.add(nextState);
                        queue.add(nextState);
                    }
                } 
                // Standard Step: Player walks onto empty floor
                else {
                    BoardState nextState = new BoardState(nextPlayer, current.boxes, current.moveHistory + moveChar);

                    if (!visited.contains(nextState)) {
                        visited.add(nextState);
                        queue.add(nextState);
                    }
                }
            }
        }
        
        // Return empty sequence if map layout is mathematically impossible
        return ""; 
    }

    /**
     * Mathematically checks if a box has been pushed into an inescapable corner.
     * Corners are permanent deadlocks unless that corner happens to be a target goal.
     */
    private boolean isDeadlocked(Point box) {
        if (goals.contains(box)) {
            return false; // Safe: Pushed directly onto a valid goal spot
        }

        boolean wallUp = walls.contains(new Point(box.x, box.y - 1));
        boolean wallDown = walls.contains(new Point(box.x, box.y + 1));
        boolean wallLeft = walls.contains(new Point(box.x - 1, box.y));
        boolean wallRight = walls.contains(new Point(box.x + 1, box.y));

        // Corner configurations: If two adjacent orthogonal sides are walls, it's trapped.
        if (wallUp && wallLeft) return true;
        if (wallUp && wallRight) return true;
        if (wallDown && wallLeft) return true;
        if (wallDown && wallRight) return true;

        return false;
    }
}

// ==========================================
// INFRASTRUCTURE STRUCTURES
// ==========================================
class Point {
    int x, y;
    Point(int x, int y) { this.x = x; this.y = y; }
    
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Point)) return false;
        Point p = (Point) o;
        return this.x == p.x && this.y == p.y;
    }
    
    @Override
    public int hashCode() { return (x * 31) + y; }
}

class BoardState {
    Point player;
    Set<Point> boxes;
    String moveHistory;

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
    public int hashCode() { return player.hashCode() + boxes.hashCode(); }
}