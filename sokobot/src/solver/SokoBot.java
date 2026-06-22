/**
 * CSINTSY MCO1: SokoBot
 * Group Members:
 * - Aguete, Sofia Ashley
 * - Gaspar, Chrisane Ianna
 * - Maglente, Michael Stephen
 * - Malicsi, Atreuz Patrick
 */

package solver;

import java.util.*;

public class SokoBot {

    private static final int INF = 100000;
    private static final int HEURISTIC_WEIGHT = 10;

    private final Set<Point> walls = new HashSet<>();
    private final Set<Point> goals = new HashSet<>();
    private final Set<Point> safeTiles = new HashSet<>();
    private final Map<Point, Map<Point, Integer>> goalDistances = new HashMap<>();
    private final int[][] directions = {{0, -1, 'u'}, {0, 1, 'd'}, {-1, 0, 'l'}, {1, 0, 'r'}};

    /**
     * Main method called by the Sokoban program.
     * It reads the map, gets the starting player and crate positions,
     * prepares the helper data, then runs the A* solver.
     */
    public String solveSokobanPuzzle(int width, int height, char[][] mapData, char[][] itemsData) {
        walls.clear();
        goals.clear();
        safeTiles.clear();
        goalDistances.clear();

        Point initialPlayer = null;
        Set<Point> initialBoxes = new HashSet<>();

        // Read the map and separate the fixed parts from the movable parts.
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                char tile = (r < mapData.length && c < mapData[r].length) ? mapData[r][c] : ' ';
                char item = (r < itemsData.length && c < itemsData[r].length) ? itemsData[r][c] : ' ';

                if (tile == '#') walls.add(new Point(c, r));
                else if (tile == '.') goals.add(new Point(c, r));

                if (item == '@') initialPlayer = new Point(c, r);
                else if (item == '$') initialBoxes.add(new Point(c, r));
                else if (item == '*') {
                    initialBoxes.add(new Point(c, r));
                    goals.add(new Point(c, r));
                }
            }
        }

        if (initialPlayer == null || goals.isEmpty()) return "";

        computeSafeTiles(width, height);
        computeGoalDistances(width, height);

        BoardState startState = new BoardState(initialPlayer, initialBoxes, "", 0, 0);
        return runMacroAStar(startState, width, height);
    }

    /**
     * Runs the main macro A* search.
     * The solver first checks all places the player can walk to, then it only
     * creates a new state when a crate is pushed. This keeps the search smaller
     * than treating every walking step as its own state.
     */
    private String runMacroAStar(BoardState startState, int width, int height) {
        PriorityQueue<BoardState> pq = new PriorityQueue<>();
        Set<StateKey> visited = new HashSet<>();

        Point startCanonical = getCanonical(startState.player, startState.boxes, width, height);
        startState.fCost = getHeuristic(startState.boxes, width);

        visited.add(new StateKey(startState.boxes, startCanonical));
        pq.add(startState);

        while (!pq.isEmpty()) {
            BoardState curr = pq.poll();

            if (goals.containsAll(curr.boxes)) {
                return curr.moveHistory;
            }

            // Find all player positions reachable without pushing a crate.f
            Queue<Point> queue = new LinkedList<>();
            Map<Point, String> paths = new HashMap<>();

            queue.add(curr.player);
            paths.put(curr.player, "");

            while (!queue.isEmpty()) {
                Point p = queue.poll();
                String pathSoFar = paths.get(p);

                for (int[] dir : directions) {
                    Point next = new Point(p.x + dir[0], p.y + dir[1]);

                    if (isValidFloor(next, width, height)
                            && !curr.boxes.contains(next)
                            && !paths.containsKey(next)) {
                        paths.put(next, pathSoFar + (char) dir[2]);
                        queue.add(next);
                    }
                }
            }
            // From each reachable player position, check if a nearby crate can be pushed.
            for (Map.Entry<Point, String> entry : paths.entrySet()) {
                Point playerPos = entry.getKey();
                String pathToPlayerPos = entry.getValue();

                for (int[] dir : directions) {
                    Point boxPos = new Point(playerPos.x + dir[0], playerPos.y + dir[1]);

                    if (!curr.boxes.contains(boxPos)) {
                        continue;
                    }

                    Point pushPos = new Point(boxPos.x + dir[0], boxPos.y + dir[1]);

                    if (!isValidFloor(pushPos, width, height) || curr.boxes.contains(pushPos)) {
                        continue;
                    }

                    // Skip pushes that move crates into positions that are unlikely to reach a goal.
                    if (!safeTiles.contains(pushPos)) {
                        continue;
                    }

                    // Create a new crate layout after the push.
                    Set<Point> newBoxes = new HashSet<>(curr.boxes);
                    newBoxes.remove(boxPos);
                    newBoxes.add(pushPos);

                    // Avoid simple crate-wall patterns that usually make the puzzle unsolvable.
                    if (has2x2DeadlockAround(pushPos, newBoxes)) {
                        continue;
                    }

                    Point newPlayer = boxPos;
                    Point newCanonical = getCanonical(newPlayer, newBoxes, width, height);
                    StateKey newKey = new StateKey(newBoxes, newCanonical);

                    // Skip this state if the same crate layout and player area were already checked.
                    if (visited.contains(newKey)) {
                        continue;
                    }

                    visited.add(newKey);

                    int newG = curr.gCost + 1;
                    int h = getHeuristic(newBoxes, width);

                    if (h >= INF) {
                        continue;
                    }

                    String newHistory = curr.moveHistory + pathToPlayerPos + (char) dir[2];
                    BoardState nextState = new BoardState(newPlayer, newBoxes, newHistory, newG, newG + h);
                    pq.add(nextState);
                }
            }
        }

        return "";
    }

    /**
     * Finds the tiles where crates are still useful.
     * This starts from the goal tiles and works backward to check which
     * crate positions can still lead to a goal.
     */
    private void computeSafeTiles(int width, int height) {
        Queue<Point> queue = new LinkedList<>();
        for (Point goal : goals) {
            safeTiles.add(goal);
            queue.add(goal);
        }

        while (!queue.isEmpty()) {
            Point curr = queue.poll();
            for (int[] d : directions) {
                Point pushFrom = new Point(curr.x + d[0], curr.y + d[1]);
                Point playerPos = new Point(pushFrom.x + d[0], pushFrom.y + d[1]);

                if (isValidFloor(pushFrom, width, height) && isValidFloor(playerPos, width, height)) {
                    if (!safeTiles.contains(pushFrom)) {
                        safeTiles.add(pushFrom);
                        queue.add(pushFrom);
                    }
                }
            }
        }
    }

    /**
     * Precomputes distances from each goal to the floor tiles.
     * These distances are used by the heuristic during A* search.
     */
    private void computeGoalDistances(int width, int height) {
        for (Point goal : goals) {
            Map<Point, Integer> distances = new HashMap<>();
            Queue<Point> queue = new LinkedList<>();

            distances.put(goal, 0);
            queue.add(goal);

            while (!queue.isEmpty()) {
                Point curr = queue.poll();
                int dist = distances.get(curr);

                for (int[] d : directions) {
                    Point next = new Point(curr.x + d[0], curr.y + d[1]);
                    if (isValidFloor(next, width, height)) {
                        if (!distances.containsKey(next)) {
                            distances.put(next, dist + 1);
                            queue.add(next);
                        }
                    }
                }
            }
            goalDistances.put(goal, distances);
        }
    }

    /**
     * Estimates how close the current crate positions are to the goals.
     * Each crate is matched to the nearest available goal. This is not
     * always perfect, but it is fast and works well for simpler levels.
     */
    private int getHeuristic(Set<Point> boxes, int width) {
        int totalDistance = 0;
        List<Point> availableGoals = new ArrayList<>(goals);

        // Sort boxes so the heuristic gives consistent results each time.
        List<Point> sortedBoxes = new ArrayList<>(boxes);
        sortedBoxes.sort(Comparator.comparingInt(p -> p.y * width + p.x));

        for (Point box : sortedBoxes) {
            int minDistance = INF;
            Point bestGoal = null;

            for (Point goal : availableGoals) {
                Map<Point, Integer> dists = goalDistances.get(goal);
                if (dists != null && dists.containsKey(box)) {
                    int dist = dists.get(box);
                    if (dist < minDistance) {
                        minDistance = dist;
                        bestGoal = goal;
                    }
                }
            }

            if (bestGoal != null) {
                availableGoals.remove(bestGoal);
                totalDistance += minDistance;
            } else {
                return INF; // No reachable goal was found for this crate position.
            }
        }
        return totalDistance * HEURISTIC_WEIGHT; // Highly aggressive weighting to prioritize pushing
    }

    /**
     * Gets one representative player position for the current reachable area.
     * If the player can walk around without pushing crates, those positions
     * are mostly the same for the search. Using one position helps reduce
     * repeated states.
     */
    private Point getCanonical(Point player, Set<Point> boxes, int width, int height) {
        Queue<Point> q = new LinkedList<>();
        Set<Point> vis = new HashSet<>();
        q.add(player);
        vis.add(player);
        Point canonical = player;

        while (!q.isEmpty()) {
            Point p = q.poll();
            if (p.y < canonical.y || (p.y == canonical.y && p.x < canonical.x)) {
                canonical = p;
            }
            for (int[] dir : directions) {
                Point next = new Point(p.x + dir[0], p.y + dir[1]);
                if (isValidFloor(next, width, height) && !boxes.contains(next) && !vis.contains(next)) {
                    vis.add(next);
                    q.add(next);
                }
            }
        }
        return canonical;
    }

    /**
     * Checks for a simple 2x2 deadlock near the crate that was just moved.
     * If a 2x2 area is filled with walls/crates and one of the crates is not
     * on a goal, the puzzle usually cannot be solved from that state anymore.
     */
    private boolean has2x2DeadlockAround(Point movedBox, Set<Point> boxes) {
        int[][] topLeftOffsets = {
                {0, 0},
                {-1, 0},
                {0, -1},
                {-1, -1}
        };

        for (int[] offset : topLeftOffsets) {
            Point topLeft = new Point(movedBox.x + offset[0], movedBox.y + offset[1]);

            Point p1 = topLeft;
            Point p2 = new Point(topLeft.x + 1, topLeft.y);
            Point p3 = new Point(topLeft.x, topLeft.y + 1);
            Point p4 = new Point(topLeft.x + 1, topLeft.y + 1);

            if (isBlockedFor2x2(p1, boxes) &&
                    isBlockedFor2x2(p2, boxes) &&
                    isBlockedFor2x2(p3, boxes) &&
                    isBlockedFor2x2(p4, boxes)) {

                if (isNonGoalBox(p1, boxes) ||
                        isNonGoalBox(p2, boxes) ||
                        isNonGoalBox(p3, boxes) ||
                        isNonGoalBox(p4, boxes)) {
                    return true;
                }
            }
        }

        return false;
    }

    // A tile counts as blocked if it is a wall or already has a crate.
    private boolean isBlockedFor2x2(Point p, Set<Point> boxes) {
        return walls.contains(p) || boxes.contains(p);
    }

    // Used by the 2x2 deadlock check to see if a crate is stuck off-goal.
    private boolean isNonGoalBox(Point p, Set<Point> boxes) {
        return boxes.contains(p) && !goals.contains(p);
    }

    /**
     * Checks if a tile is inside the board and not a wall.
     */
    private boolean isValidFloor(Point p, int width, int height) {
        return p.x >= 0 && p.x < width && p.y >= 0 && p.y < height && !walls.contains(p);
    }
}

// Stores a board coordinate using x as column and y as row.
class Point {
    int x, y;
    Point(int x, int y) { this.x = x; this.y = y; }
    @Override public boolean equals(Object o) {
        if (!(o instanceof Point)) return false;
        Point p = (Point) o; return this.x == p.x && this.y == p.y;
    }
    @Override public int hashCode() { return (x * 31) + y; }
}

// Used for visited checking. It stores the crate layout and the player's reachable area.
class StateKey {
    Set<Point> boxes;
    Point canonicalPlayer;

    StateKey(Set<Point> boxes, Point canonicalPlayer) {
        this.boxes = new HashSet<>(boxes);
        this.canonicalPlayer = canonicalPlayer;
    }

    @Override public boolean equals(Object o) {
        if (!(o instanceof StateKey)) return false;
        StateKey k = (StateKey) o;
        return this.canonicalPlayer.equals(k.canonicalPlayer) && this.boxes.equals(k.boxes);
    }

    @Override public int hashCode() { return canonicalPlayer.hashCode() * 31 + boxes.hashCode(); }
}

// Represents one possible puzzle state used by the A* search.
class BoardState implements Comparable<BoardState> {
    Point player;
    Set<Point> boxes;
    String moveHistory;
    int gCost;
    int fCost;

    BoardState(Point player, Set<Point> boxes, String moveHistory, int gCost, int fCost) {
        this.player = player;
        this.boxes = new HashSet<>(boxes);
        this.moveHistory = moveHistory;
        this.gCost = gCost;
        this.fCost = fCost;
    }

    @Override
    public int compareTo(BoardState other) {
        int fCompare = Integer.compare(this.fCost, other.fCost);
        if (fCompare != 0) {
            return fCompare;
        }

        // If fCost is tied, prefer the state with more pushes already made.
        // Since f = g + h, higher g usually means lower remaining heuristic.
        return Integer.compare(other.gCost, this.gCost);
    }
}