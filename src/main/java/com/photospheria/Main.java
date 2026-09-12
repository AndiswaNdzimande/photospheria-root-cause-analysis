package com.photospheria;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;

/**
 * The Main class starts the Photospheria Root Cause Analysis program.
 *
 * This program:
 * 1. Reads the Photospheria level data from data/1.json.
 * 2. Extracts basic information about the level, such as:
 *    - number of rows
 *    - number of columns
 *    - number of ticks
 *    - whether animals are enabled
 * 3. Displays the level information in the terminal.
 *
 * The JSON file is read using Jackson's ObjectMapper.
 * The JSON data is stored in a JsonNode so that individual
 * values can be accessed by their names.
 */
public class Main {

    public static void main(String[] args) throws Exception {

        JsonNode levelData = readLevelDataFromJsonFile();

        displayLevelInformation(levelData);
    }

    /**
     * Reads the JSON data from the Level 1 file.
     *
     * @return the contents of data/1.json as a JsonNode
     * @throws Exception if the file cannot be found or read
     */
    public static JsonNode readLevelDataFromJsonFile() throws Exception {

        ObjectMapper jsonReader = new ObjectMapper();

        File levelOneFile = new File("data/1.json");

        return jsonReader.readTree(levelOneFile);
    }

    /**
     * Gets the basic level information from the JSON data
     * and displays it in the terminal.
     *
     * @param levelData the JSON data containing the level information
     */
    public static void displayLevelInformation(JsonNode levelData) {

        int numberOfRows = levelData.get("rows").asInt();
        int numberOfColumns = levelData.get("cols").asInt();
        int numberOfTicks = levelData.get("ticks").asInt();
        boolean animalsAreEnabled =
                levelData.get("animals_enabled").asBoolean();

        System.out.println("Photospheria Level");
        System.out.println("------------------");
        System.out.println("Rows: " + numberOfRows);
        System.out.println("Columns: " + numberOfColumns);
        System.out.println("Ticks: " + numberOfTicks);
        System.out.println("Animals enabled: " + animalsAreEnabled);
    }
}