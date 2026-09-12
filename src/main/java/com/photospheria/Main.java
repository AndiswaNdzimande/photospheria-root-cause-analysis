package com.photospheria;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
 * 3. Works out which cells can actually be planted on.
 * 4. Decides on a planting strategy for Level 1 specifically:
 *    Level 1 has animals disabled and no scheduled weather events,
 *    and every plant unlock in the game requires one or the other.
 *    That means no new species can ever be unlocked in this level,
 *    so the best possible sample is an even, well-timed spread of
 *    the 5 starter plants: Grass, Rose Bush, Lavender,
 *    Dwarf Sunflower, and Oak Tree.
 * 5. Builds a valid submission JSON file and checks it for mistakes
 *    before saving it.
 *
 * The JSON data is read and written using Jackson's ObjectMapper.
 */
public class Main {

    /** A simple, readable stand-in for a (row, col) grid position. */
    static class Cell {
        final int row;
        final int col;
        Cell(int row, int col) {
            this.row = row;
            this.col = col;
        }
    }

    // The 5 starter plants every level begins with, and their catalogue
    // indices from plant_dataset.json.
    static final String[] STARTER_NAMES = {
            "Grass", "Rose Bush", "Lavender", "Dwarf Sunflower", "Oak Tree"
    };
    static final int[] STARTER_INDICES = {1, 2, 6, 5, 12};

    // How many plants we are allowed to place in a single tick.
    static final int MAX_PLANTS_PER_TICK = 20;

    public static void main(String[] args) throws Exception {

        JsonNode levelData = readLevelDataFromJsonFile();
        displayLevelInformation(levelData);

        int rows = levelData.get("rows").asInt();
        int cols = levelData.get("cols").asInt();
        int ticks = levelData.get("ticks").asInt();

        List<Cell> plantableCells = findPlantableSoilCells(levelData, rows, cols);
        System.out.println("Plantable cells found: " + plantableCells.size()
                + " out of " + (rows * cols) + " total cells");

        int[] plantingWindow = calculateSafeLatePlantingWindow(ticks);
        int startTick = plantingWindow[0];
        int endTick = plantingWindow[1];
        System.out.println("Safe planting window: tick " + startTick + " to " + endTick);

        List<List<Cell>> cellsPerSpecies = splitCellsEvenlyAcrossStarters(
                plantableCells, startTick, endTick);

        ObjectNode submission = buildPlantingSchedule(cellsPerSpecies, startTick, endTick);

        validateSubmission(submission, rows, cols, ticks);

        writeSubmissionToFile(submission, "output/level1_submission.json");
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

    /**
     * Works out every cell we are actually allowed to plant on.
     *
     * The level file only lists "special" cells (soil variants, stone,
     * paths, water, etc). Any cell NOT listed is assumed to be ordinary
     * plantable dirt. Of the listed cells, only ones marked with
     * terrain 0 are soil; anything else (path, stone, water) is not
     * plantable.
     *
     * @param levelData the JSON data containing the level information
     * @param rows      number of rows in the grid
     * @param cols      number of columns in the grid
     * @return a list of every plantable Cell in the grid
     */
    public static List<Cell> findPlantableSoilCells(JsonNode levelData, int rows, int cols) {

        boolean[][] isPlantable = new boolean[rows][cols];
        for (boolean[] row : isPlantable) {
            java.util.Arrays.fill(row, true); // default: plantable dirt
        }

        for (JsonNode cellNode : levelData.get("cells")) {
            int row = cellNode.get("row").asInt();
            int col = cellNode.get("col").asInt();
            int terrain = cellNode.get("terrain").asInt();
            isPlantable[row][col] = (terrain == 0);
        }

        List<Cell> plantableCells = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                if (isPlantable[row][col]) {
                    plantableCells.add(new Cell(row, col));
                }
            }
        }
        return plantableCells;
    }

    /**
     * Works out the safest window of ticks to plant in.
     *
     * Every occupied cell loses 1 nutrient point per tick starting
     * from 100, and dies once it hits 0. That gives any single
     * planting a lifespan of roughly 100 ticks. Since only the FINAL
     * tick's state is scored, planting too early wastes the plant
     * entirely - it will already be dead by scoring time. So we plant
     * everything inside the last ~100 ticks (with a small safety
     * margin), which guarantees survival to the end while still
     * banking close to the maximum possible lifespan credit.
     *
     * @param totalTicks the total number of ticks in the level
     * @return a two-element array: {startTick, endTick}
     */
    public static int[] calculateSafeLatePlantingWindow(int totalTicks) {

        int safetyMarginTicks = 5;
        int estimatedNutrientLifespan = 100;

        int startTick = Math.max(0, totalTicks - estimatedNutrientLifespan + safetyMarginTicks);
        int endTick = totalTicks - 1; // the last tick we're allowed to act on

        return new int[]{startTick, endTick};
    }

    /**
     * Splits the plantable cells evenly across the 5 starter species.
     *
     * An even split matters because the scoring function rewards
     * DIVERSITY measured as entropy - and entropy is highest when
     * every species has roughly the same share of the sample, not
     * just when many species are present. We also cap how many cells
     * we use at the number we can actually schedule inside the safe
     * planting window, since each tick only allows 20 plantings.
     *
     * @param plantableCells all cells we are allowed to plant on
     * @param startTick      first tick of the safe planting window
     * @param endTick        last tick of the safe planting window
     * @return a list of 5 cell groups, one per starter species, in
     *         the same order as STARTER_NAMES / STARTER_INDICES
     */
    public static List<List<Cell>> splitCellsEvenlyAcrossStarters(
            List<Cell> plantableCells, int startTick, int endTick) {

        int windowLengthInTicks = endTick - startTick + 1;
        int maxPlantingsPossible = windowLengthInTicks * MAX_PLANTS_PER_TICK;

        int cellsToUse = Math.min(plantableCells.size(), maxPlantingsPossible);
        cellsToUse -= cellsToUse % STARTER_INDICES.length; // keep the split even

        int cellsPerSpecies = cellsToUse / STARTER_INDICES.length;

        List<List<Cell>> groups = new ArrayList<>();
        for (int species = 0; species < STARTER_INDICES.length; species++) {
            int fromIndex = species * cellsPerSpecies;
            int toIndex = fromIndex + cellsPerSpecies;
            groups.add(plantableCells.subList(fromIndex, toIndex));
        }
        return groups;
    }

    /**
     * Builds the actual submission JSON, scheduling plantings tick by
     * tick, never exceeding 20 plants in any single tick, and never
     * planting the same cell twice.
     *
     * @param cellsPerSpecies cell groups from splitCellsEvenlyAcrossStarters
     * @param startTick       first tick of the safe planting window
     * @param endTick         last tick of the safe planting window
     * @return the submission as a Jackson ObjectNode, ready to validate/save
     */
    public static ObjectNode buildPlantingSchedule(
            List<List<Cell>> cellsPerSpecies, int startTick, int endTick) {

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode submission = mapper.createObjectNode();
        ArrayNode actions = mapper.createArrayNode();

        int[] nextCellIndexForSpecies = new int[STARTER_INDICES.length];
        int totalScheduled = 0;
        int totalCellsAvailable = cellsPerSpecies.stream().mapToInt(List::size).sum();

        for (int tick = startTick; tick <= endTick && totalScheduled < totalCellsAvailable; tick++) {

            ArrayNode plantsThisTick = mapper.createArrayNode();

            // Round-robin across species so every tick plants a healthy mix,
            // which keeps the entropy calculation balanced throughout, not
            // just at the very end.
            int species = 0;
            while (plantsThisTick.size() < MAX_PLANTS_PER_TICK && totalScheduled < totalCellsAvailable) {
                List<Cell> group = cellsPerSpecies.get(species);
                int nextIndex = nextCellIndexForSpecies[species];

                if (nextIndex < group.size()) {
                    Cell cell = group.get(nextIndex);
                    ObjectNode plant = mapper.createObjectNode();
                    plant.put("plant_index", STARTER_INDICES[species]);
                    plant.put("row", cell.row);
                    plant.put("col", cell.col);
                    plantsThisTick.add(plant);
                    nextCellIndexForSpecies[species]++;
                    totalScheduled++;
                }

                species = (species + 1) % STARTER_INDICES.length;
            }

            if (plantsThisTick.size() > 0) {
                ObjectNode tickEntry = mapper.createObjectNode();
                tickEntry.put("tick", tick);
                tickEntry.set("plants", plantsThisTick);
                actions.add(tickEntry);
            }
        }

        submission.set("actions", actions);
        return submission;
    }

    /**
     * Checks the submission for mistakes before we save it, so we
     * catch problems ourselves instead of finding out from a failed
     * or zero-scored competition run.
     *
     * @param submission the built submission JSON
     * @param rows       number of rows in the grid
     * @param cols       number of columns in the grid
     * @param totalTicks total number of ticks in the level
     */
    public static void validateSubmission(ObjectNode submission, int rows, int cols, int totalTicks) {

        Set<Long> cellsAlreadyUsed = new HashSet<>();

        for (JsonNode action : submission.get("actions")) {
            int tick = action.get("tick").asInt();
            if (tick < 0 || tick > totalTicks - 1) {
                throw new IllegalStateException("Tick out of bounds: " + tick);
            }

            JsonNode plants = action.get("plants");
            if (plants.size() > MAX_PLANTS_PER_TICK) {
                throw new IllegalStateException(
                        "Tick " + tick + " has more than " + MAX_PLANTS_PER_TICK + " plants");
            }

            for (JsonNode plant : plants) {
                int row = plant.get("row").asInt();
                int col = plant.get("col").asInt();
                if (row < 0 || row >= rows || col < 0 || col >= cols) {
                    throw new IllegalStateException("Cell out of bounds: (" + row + ", " + col + ")");
                }

                long cellKey = (long) row * cols + col;
                if (!cellsAlreadyUsed.add(cellKey)) {
                    throw new IllegalStateException(
                            "Cell (" + row + ", " + col + ") is planted more than once");
                }
            }
        }

        System.out.println("Validation passed: submission is well-formed.");
    }

    /**
     * Writes the finished, validated submission to disk.
     *
     * @param submission   the submission JSON to write
     * @param outputPath   where to write it, e.g. "output/level1_submission.json"
     * @throws Exception if the file cannot be written
     */
    public static void writeSubmissionToFile(ObjectNode submission, String outputPath) throws Exception {

        File outputFile = new File(outputPath);
        outputFile.getParentFile().mkdirs();

        ObjectMapper mapper = new ObjectMapper();
        mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, submission);

        System.out.println("Submission written to: " + outputFile.getPath());
    }
}