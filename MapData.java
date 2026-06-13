package reader;

public class MapData {
  public String mapName = "Unknown"; // <-- We added this to store the map name
  public char[][] tiles;
  public int rows;
  public int columns;

  public void print() {
    for (int i = 0; i < rows; i++) {
      for (int j = 0; j < columns; j++) {
        System.out.print(tiles[i][j]);
      }
      System.out.println();
    }
  }
}