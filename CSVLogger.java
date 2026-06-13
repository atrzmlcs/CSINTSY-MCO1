package gui;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.IOException;

public class CSVLogger {
    private static final String CSV_FILE_PATH = "sokobot_results.csv";

    public static void logResult(String mapName, int moves, double timeInSeconds, boolean solved) {
        File file = new File(CSV_FILE_PATH);
        boolean isNewFile = !file.exists();

        try (FileWriter fw = new FileWriter(file, true); 
             PrintWriter pw = new PrintWriter(fw)) {
            
            // Write the revised header row if the file was just created
            if (isNewFile) {
                pw.println("Map Name,Moves,Time (Seconds),Status");
            }

            // Convert the boolean evaluation to explicit PASS or FAIL strings
            String status = solved ? "PASS" : "FAIL";

            // Append the comprehensive data row
            pw.printf("%s,%d,%.3f,%s%n", mapName, moves, timeInSeconds, status);
            
        } catch (IOException e) {
            System.out.println("Error writing to CSV: " + e.getMessage());
        }
    }
}