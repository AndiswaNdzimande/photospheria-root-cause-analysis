package com.photospheria;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * Application entry point for generating and validating the Photospheria
 * Level 2 submission.
 */
public class Main {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_PLANTS_PER_TICK = 20;

    /**
     * Reads Level 2, builds the strategy, validates the generated actions and
     * writes {@code output/level2_submission.json}.
     *
     * @param args command-line arguments; not used
     */
    public static void main(String[] args) {
        try {
            File levelFile = new File("data/2.json");
            if (!levelFile.exists()) {
                System.err.println("Error: Input file data/2.json missing.");
                return;
            }

            JsonNode levelRoot = MAPPER.readTree(levelFile);
            GridModel grid = new GridModel(levelRoot);

            printLevelSummary(grid);

            ObjectNode submissionRoot = MAPPER.createObjectNode();
            ArrayNode actions = MAPPER.createArrayNode();

            Level2Strategy.build(grid, MAPPER, actions);
            submissionRoot.set("actions", actions);

            validateSubmission(
                    actions,
                    grid.getRows(),
                    grid.getCols(),
                    grid.getTotalTicks()
            );

            File outputDirectory = new File("output");
            if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
                throw new IllegalStateException("Could not create output directory.");
            }

            File outputFile = new File(outputDirectory, "level2_submission.json");
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(outputFile, submissionRoot);

            System.out.println();
            System.out.println("Validation passed: submission is well-formed.");
            System.out.println("Tick entries: " + actions.size());
            System.out.println("Submission written to: " + outputFile.getPath());

        } catch (Exception exception) {
            System.err.println("Failed to generate Level 2 submission.");
            exception.printStackTrace();
        }
    }

    /**
     * Prints the important level settings before generation begins.
     *
     * @param grid parsed grid model
     */
    private static void printLevelSummary(GridModel grid) {
        System.out.println("Photospheria Level 2");
        System.out.println("--------------------");
        System.out.println("Rows: " + grid.getRows());
        System.out.println("Columns: " + grid.getCols());
        System.out.println("Ticks: " + grid.getTotalTicks());
        System.out.println("Animals enabled: " + grid.areAnimalsEnabled());
        System.out.println("Plantable cells found: " + grid.getPlantableCells().size());
    }

    /**
     * Performs structural validation before the JSON is written/submitted.
     *
     * <p>The validator checks tick bounds, the twenty-plants-per-tick limit,
     * coordinate bounds, positive plant indices, duplicate ticks and duplicate
     * coordinates within the same tick.</p>
     *
     * @param actions action array to validate
     * @param maxRows number of grid rows
     * @param maxCols number of grid columns
     * @param maxTicks total simulation ticks
     */
    private static void validateSubmission(
            ArrayNode actions,
            int maxRows,
            int maxCols,
            int maxTicks
    ) {
        Set<Integer> seenTicks = new HashSet<>();

        for (JsonNode action : actions) {
            int tick = action.path("tick").asInt(-1);

            if (tick < 0 || tick >= maxTicks) {
                throw new IllegalStateException("Invalid tick: " + tick);
            }

            if (!seenTicks.add(tick)) {
                throw new IllegalStateException("Duplicate tick entry: " + tick);
            }

            JsonNode plants = action.get("plants");
            if (plants == null || !plants.isArray()) {
                throw new IllegalStateException("Missing plants array at tick " + tick);
            }

            if (plants.size() > MAX_PLANTS_PER_TICK) {
                throw new IllegalStateException(
                        "More than 20 plants scheduled at tick " + tick
                );
            }

            Set<String> coordinatesThisTick = new HashSet<>();

            for (JsonNode plant : plants) {
                int plantIndex = plant.path("plant_index").asInt(-1);
                int row = plant.path("row").asInt(-1);
                int col = plant.path("col").asInt(-1);

                if (plantIndex <= 0) {
                    throw new IllegalStateException(
                            "Invalid plant index " + plantIndex + " at tick " + tick
                    );
                }

                if (row < 0 || row >= maxRows || col < 0 || col >= maxCols) {
                    throw new IllegalStateException(
                            "Coordinate out of bounds at tick " + tick
                                    + ": (" + row + "," + col + ")"
                    );
                }

                String key = row + ":" + col;
                if (!coordinatesThisTick.add(key)) {
                    throw new IllegalStateException(
                            "Duplicate coordinate at tick " + tick + ": " + key
                    );
                }
            }
        }
    }
}
