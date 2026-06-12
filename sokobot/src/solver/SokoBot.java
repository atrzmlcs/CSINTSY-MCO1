package solver;

import java.util.*;

public class SokoBot {

    private final Set<Point> walls = new HashSet<>();
    private final Set<Point> goals = new HashSet<>();
    private final Set<Point> safeTiles = new HashSet<>();
    private final Map<Point, Map<Point, Integer>> goalDistances = new HashMap<>();
    private final int[][] directions = {{0, -1, 'u'}, {0, 1, 'd'}, {-1, 0, 'l'}, {1, 0, 'r'}};

    public String solveSokobanPuzzle(int width, int height, char[][] mapData, char[][] itemsData) {
        walls.clear();
        goals.clear();
        safeTiles.clear();
        goalDistances.clear();

        Point initialPlayer = null;
        Set<Point> initialBoxes = new HashSet<>();

        // Parse environment safely
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

    private int getHeuristic(Set<Point> boxes, int width) {
        int totalDistance = 0;
        List<Point> availableGoals = new ArrayList<>(goals);
        
        // Sorting guarantees the greedy matching is 100% deterministic and stable
        List<Point> sortedBoxes = new ArrayList<>(boxes);
        sortedBoxes.sort(Comparator.comparingInt(p -> p.y * width + p.x));
        
        for (Point box : sortedBoxes) {
            int minDistance = 100000;
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
                return 100000; // Impossible Dead-End
            }
        }
        return totalDistance * 10; // Highly aggressive weighting to prioritize pushing
    }

    /**
     * Determines the top-leftmost reachable floor tile from the player's current spot.
     * This forces the algorithm to treat walking around an empty room as a single, identical state.
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

            // 1. MACRO SCAN: Find all floor tiles the player can currently walk to
            Queue<Point> q = new LinkedList<>();
            Map<Point, String> paths = new HashMap<>();

            q.add(curr.player);
            paths.put(curr.player, "");

            while (!q.isEmpty()) {
                Point p = q.poll();
                String pathSoFar = paths.get(p);

                for (int[] dir : directions) {
                    Point next = new Point(p.x + dir[0], p.y + dir[1]);
                    if (isValidFloor(next, width, height) && !curr.boxes.contains(next) && !paths.containsKey(next)) {
                        paths.put(next, pathSoFar + (char)dir[2]);
                        q.add(next);
                    }
                }
            }

            // 2. MACRO PUSH: Only generate new realities if a box is being pushed!
            for (Map.Entry<Point, String> entry : paths.entrySet()) {
                Point p = entry.getKey();
                String pathToP = entry.getValue();

                for (int[] dir : directions) {
                    Point boxPos = new Point(p.x + dir[0], p.y + dir[1]);
                    
                    if (curr.boxes.contains(boxPos)) {
                        Point pushPos = new Point(boxPos.x + dir[0], boxPos.y + dir[1]);

                        if (isValidFloor(pushPos, width, height) && !curr.boxes.contains(pushPos)) {
                            if (safeTiles.contains(pushPos)) { 
                                
                                Set<Point> newBoxes = new HashSet<>(curr.boxes);
                                newBoxes.remove(boxPos);
                                newBoxes.add(pushPos);

                                Point newPlayer = boxPos;
                                Point newCanonical = getCanonical(newPlayer, newBoxes, width, height);
                                StateKey newKey = new StateKey(newBoxes, newCanonical);

                                if (!visited.contains(newKey)) {
                                    visited.add(newKey);
                                    
                                    int newG = curr.gCost + 1; // 1 Box Push = 1 Cost
                                    int h = getHeuristic(newBoxes, width);
                                    
                                    if (h < 100000) { 
                                        String newHistory = curr.moveHistory + pathToP + (char)dir[2];
                                        BoardState nextState = new BoardState(newPlayer, newBoxes, newHistory, newG, newG + h);
                                        pq.add(nextState);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return ""; 
    }

    private boolean isValidFloor(Point p, int width, int height) {
        return p.x >= 0 && p.x < width && p.y >= 0 && p.y < height && !walls.contains(p);
    }
}

// ==========================================
// DATA STRUCTURES
// ==========================================
class Point {
    int x, y;
    Point(int x, int y) { this.x = x; this.y = y; }
    @Override public boolean equals(Object o) {
        if (!(o instanceof Point)) return false;
        Point p = (Point) o; return this.x == p.x && this.y == p.y;
    }
    @Override public int hashCode() { return (x * 31) + y; }
}

class StateKey {
    Set<Point> boxes;
    Point canonicalPlayer;

    StateKey(Set<Point> boxes, Point canonicalPlayer) {
        this.boxes = boxes;
        this.canonicalPlayer = canonicalPlayer;
    }

    @Override public boolean equals(Object o) {
        if (!(o instanceof StateKey)) return false;
        StateKey k = (StateKey) o;
        return this.canonicalPlayer.equals(k.canonicalPlayer) && this.boxes.equals(k.boxes);
    }

    @Override public int hashCode() { return canonicalPlayer.hashCode() * 31 + boxes.hashCode(); }
}

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

    @Override public int compareTo(BoardState other) { return Integer.compare(this.fCost, other.fCost); }
}
