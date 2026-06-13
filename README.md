# COMPILE USING THIS COMMAND:
javac -cp src src/main/*.java src/gui/*.java src/reader/*.java src/solver/*.java

# NEW FILES:
src/gui/CSVLogger.java: Logs Map Name, Time to Complete, Moves and Status (Pass or Fail)

# Files Affected:
src/reader/MapData.java: Added a mapName string variable to hold the name of the currently loaded map.

src/reader/FileReader.java: Updated the file reading logic to capture the filename/keyword and store it inside the MapData object.

src/gui/GamePanel.java: Added a currentMapName variable to inherit the context from MapData, and updated both the success and timeout CSVLogger.logResult() calls to pass the map name accurately.
