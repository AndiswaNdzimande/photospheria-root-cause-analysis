package com.photospheria;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Generates a deterministic multi-phase strategy for Photospheria Level 4.
 *
 * Level 4 has a 200 x 300 grid, 800 ticks, animals enabled and several
 * world events. The strategy focuses on:
 *
 * 1. Building a large starter ecosystem.
 * 2. Triggering useful animal conditions.
 * 3. Attempting realistic plant unlock chains.
 * 4. Taking advantage of Rain, Ash Eclipse and Drought.
 * 5. Preserving final diversity with late-game planting.
 */
public final class Level4Strategy {

    private static final int MAX_PLANTS_PER_TICK = 20;

    // Starter species
    private static final int GRASS = 1;
    private static final int ROSE_BUSH = 2;
    private static final int DWARF_SUNFLOWER = 5;
    private static final int LAVENDER = 6;
    private static final int OAK_TREE = 12;

    // Early unlock targets
    private static final int BLUE_MOSS = 3;
    private static final int CRIMSON_VINE = 4;
    private static final int ORANGE_BLOSSOM = 7;
    private static final int SILVER_FERN = 8;
    private static final int GLOWCAP_FUNGUS = 9;

    // Mid/late unlock targets
    private static final int PURPLE_CANOPY_TREE = 10;
    private static final int MOONPETAL_LILY = 15;
    private static final int CRYSTAL_CACTUS = 17;
    private static final int MIRE_BLOOM = 18;
    private static final int SKYVINE = 20;

    private Level4Strategy() {
        // Utility class.
    }

    /**
     * Reads Level 4, generates actions, validates them and writes
     * output/level4_submission.json.
     *
     * @throws Exception if input/output processing fails
     */
    public static void execute() throws Exception {

        ObjectMapper mapper = new ObjectMapper();
        File inputFile = new File("data/4.json");

        if (!inputFile.exists()) {
            throw new IllegalStateException(
                    "Missing data/4.json. Place the Level 4 JSON inside the data folder."
            );
        }

        JsonNode input = mapper.readTree(inputFile);

        int rows = input.get("rows").asInt();
        int cols = input.get("cols").asInt();
        int ticks = input.get("ticks").asInt();
        boolean animalsEnabled = input.get("animals_enabled").asBoolean();

        System.out.println("Photospheria Level 4");
        System.out.println("--------------------");
        System.out.println("Rows: " + rows);
        System.out.println("Columns: " + cols);
        System.out.println("Ticks: " + ticks);
        System.out.println("Animals enabled: " + animalsEnabled);

        printEvents(input);

        List<Cell> cells = findPlantableCells(input, rows, cols);

        // Reorder positions so planting is distributed across the large map.
        distributeCells(cells, rows, cols);

        System.out.println("Plantable cells found: " + cells.size());

        Cursor cursor = new Cursor(cells);

        ObjectNode root = mapper.createObjectNode();
        ArrayNode actions = mapper.createArrayNode();

        /*
         * PHASE 1
         * Establish pollination and tree-support species.
         *
         * Lavender helps attract Nectaris.
         * Rose Bush supports several flowering unlocks.
         * Oak Trees can attract Barkskips.
         */
        plantRange(actions, mapper, cursor, 0, 24, LAVENDER);
        plantRange(actions, mapper, cursor, 25, 44, ROSE_BUSH);

        /*
         * Rain occurs at tick 50.
         *
         * It is too early to guarantee Mire Bloom's Blue Moss threshold,
         * so we do not waste a large number of placements on Mire Bloom yet.
         */
        plantRange(actions, mapper, cursor, 45, 54, OAK_TREE);

        /*
         * Build Sunflower coverage to support Solwings while refreshing
         * Rose Bush.
         */
        plantAlternatingRange(
                actions,
                mapper,
                cursor,
                55,
                99,
                DWARF_SUNFLOWER,
                ROSE_BUSH
        );

        /*
         * PHASE 2
         * Grass is extremely important because Loamcrawlers, Verdelopes
         * and multiple later unlocks depend on it.
         */
        plantRange(actions, mapper, cursor, 100, 199, GRASS);

        /*
         * Continue Grass while adding Rose Bush.
         * This improves our chances of reaching the Loamcrawler conditions.
         */
        plantAlternatingRange(
                actions,
                mapper,
                cursor,
                200,
                239,
                GRASS,
                ROSE_BUSH
        );

        /*
         * Start attempting Blue Moss once the Grass/Rose ecosystem has
         * had time to establish and spread.
         */
        plantRange(actions, mapper, cursor, 240, 249, BLUE_MOSS);

        /*
         * Ash Eclipse occurs at tick 250.
         *
         * Phoenix Bloom requires a deep unlock chain, so we avoid blindly
         * spending actions on Phoenix Bloom this early.
         */
        plantRange(actions, mapper, cursor, 250, 269, BLUE_MOSS);

        /*
         * Refresh Grass immediately before the Drought event at tick 280.
         */
        plantRange(actions, mapper, cursor, 270, 279, GRASS);

        /*
         * Drought occurs at tick 280.
         *
         * Crystal Cactus requires Drought plus sufficient Grass coverage.
         * We therefore attempt Crystal Cactus immediately after the event.
         */
        plantRange(actions, mapper, cursor, 280, 299, CRYSTAL_CACTUS);

        /*
         * PHASE 3
         * Pollination-based diversity.
         */
        plantAlternatingRange(
                actions,
                mapper,
                cursor,
                300,
                329,
                ORANGE_BLOSSOM,
                CRIMSON_VINE
        );

        /*
         * More Blue Moss creates a stronger base for Silver Fern and
         * potentially Mire Bloom if the Rain state remains useful.
         */
        plantRange(actions, mapper, cursor, 330, 369, BLUE_MOSS);

        plantRange(actions, mapper, cursor, 370, 399, SILVER_FERN);

        /*
         * Dead matter becomes more likely later in the simulation because
         * early plant generations may have died.
         *
         * This makes the Glowcap Fungus prerequisite more plausible.
         */
        plantRange(actions, mapper, cursor, 400, 429, GLOWCAP_FUNGUS);

        /*
         * Purple Canopy Tree depends on Blue Moss and Crimson Vine.
         */
        plantRange(actions, mapper, cursor, 430, 449, PURPLE_CANOPY_TREE);

        /*
         * Moonpetal Lily depends on Nectaris, Lavender and Blue Moss.
         */
        plantRange(actions, mapper, cursor, 450, 469, MOONPETAL_LILY);

        /*
         * Skyvine depends on Crimson Vine and Purple Canopy Trees.
         */
        plantRange(actions, mapper, cursor, 470, 489, SKYVINE);

        /*
         * Attempt Mire Bloom later rather than directly after tick 50.
         * This is only useful if Rain-state effects persist and the Blue
         * Moss population is sufficiently established.
         */
        plantRange(actions, mapper, cursor, 490, 509, MIRE_BLOOM);

        /*
         * PHASE 4
         * Refresh all major starter species.
         *
         * Plants placed very early may no longer exist near the final tick.
         */
        int[] starterMix = {
                GRASS,
                ROSE_BUSH,
                DWARF_SUNFLOWER,
                LAVENDER,
                OAK_TREE
        };

        plantRotatingRange(
                actions,
                mapper,
                cursor,
                510,
                599,
                starterMix
        );

        /*
         * PHASE 5
         * Re-establish unlocked diversity before the final scoring phase.
         */
        int[] ecosystemMix = {
                GRASS,
                ROSE_BUSH,
                BLUE_MOSS,
                CRIMSON_VINE,
                DWARF_SUNFLOWER,
                LAVENDER,
                ORANGE_BLOSSOM,
                SILVER_FERN,
                GLOWCAP_FUNGUS,
                PURPLE_CANOPY_TREE,
                OAK_TREE,
                MOONPETAL_LILY,
                CRYSTAL_CACTUS,
                SKYVINE
        };

        plantRotatingRange(
                actions,
                mapper,
                cursor,
                600,
                699,
                ecosystemMix
        );

        /*
         * Earthquake occurs at tick 700.
         *
         * After the earthquake we favour a final diversity refresh instead
         * of attempting to predict the undocumented crack-generation rules.
         */
        int[] finalMix = {
                GRASS,
                ROSE_BUSH,
                BLUE_MOSS,
                CRIMSON_VINE,
                DWARF_SUNFLOWER,
                LAVENDER,
                ORANGE_BLOSSOM,
                SILVER_FERN,
                GLOWCAP_FUNGUS,
                PURPLE_CANOPY_TREE,
                OAK_TREE,
                MOONPETAL_LILY,
                CRYSTAL_CACTUS,
                MIRE_BLOOM,
                SKYVINE
        };

        plantRotatingRange(
                actions,
                mapper,
                cursor,
                705,
                799,
                finalMix
        );

        root.set("actions", actions);

        validate(actions, rows, cols, ticks);

        File output = new File("output/level4_submission.json");

        if (output.getParentFile() != null) {
            output.getParentFile().mkdirs();
        }

        mapper.writerWithDefaultPrettyPrinter().writeValue(output, root);

        System.out.println("Actions generated: " + actions.size());
        System.out.println("Validation passed.");
        System.out.println("Level 4 submission written to: " + output.getPath());
    }

    /**
     * Displays the world events defined in the Level 4 JSON.
     *
     * @param input Level 4 root JSON node
     */
    private static void printEvents(JsonNode input) {

        JsonNode commands = input.get("commands");

        if (commands == null || !commands.isArray()) {
            return;
        }

        System.out.println("World events:");

        for (JsonNode command : commands) {

            if ("event".equals(command.path("type").asText())) {

                System.out.println(
                        "  Tick "
                                + command.path("tick").asInt()
                                + ": "
                                + command.path("event").asText()
                );
            }
        }
    }

    /**
     * Builds a list of plantable coordinates.
     *
     * The Level JSON uses sparse explicit cell descriptions. Coordinates
     * start as plantable and cells with non-zero terrain are blocked.
     *
     * Soil id 0 is not automatically considered invalid because soil and
     * terrain are separate properties.
     *
     * @param input level JSON
     * @param rows grid height
     * @param cols grid width
     * @return list of plantable coordinates
     */
    private static List<Cell> findPlantableCells(
            JsonNode input,
            int rows,
            int cols
    ) {

        boolean[][] blocked = new boolean[rows][cols];

        JsonNode cells = input.get("cells");

        if (cells != null && cells.isArray()) {

            for (JsonNode cell : cells) {

                if (!cell.has("row") || !cell.has("col")) {
                    continue;
                }

                int row = cell.get("row").asInt();
                int col = cell.get("col").asInt();

                int terrain =
                        cell.has("terrain")
                                ? cell.get("terrain").asInt()
                                : 0;

                if (
                        row >= 0
                                && row < rows
                                && col >= 0
                                && col < cols
                                && terrain != 0
                ) {
                    blocked[row][col] = true;
                }
            }
        }

        List<Cell> result = new ArrayList<>();

        for (int row = 0; row < rows; row++) {

            for (int col = 0; col < cols; col++) {

                if (!blocked[row][col]) {
                    result.add(new Cell(row, col));
                }
            }
        }

        return result;
    }

    /**
     * Reorders cells so consecutive placements are distributed over the
     * whole Level 4 map rather than filling one row at a time.
     *
     * @param cells cells to reorder
     * @param rows number of rows
     * @param cols number of columns
     */
    private static void distributeCells(
            List<Cell> cells,
            int rows,
            int cols
    ) {

        int worldSize = rows * cols;

        cells.sort(
                Comparator
                        .comparingInt(
                                (Cell cell) ->
                                        Math.floorMod(
                                                cell.row * 47
                                                        + cell.col * 83,
                                                worldSize
                                        )
                        )
                        .thenComparingInt(cell -> cell.row)
                        .thenComparingInt(cell -> cell.col)
        );
    }

    /**
     * Plants one species for every tick in the requested range.
     */
    private static void plantRange(
            ArrayNode actions,
            ObjectMapper mapper,
            Cursor cursor,
            int startTick,
            int endTick,
            int plantIndex
    ) {

        for (
                int tick = startTick;
                tick <= endTick && cursor.hasNext();
                tick++
        ) {

            ArrayNode plants = mapper.createArrayNode();

            for (
                    int i = 0;
                    i < MAX_PLANTS_PER_TICK && cursor.hasNext();
                    i++
            ) {

                addPlant(
                        mapper,
                        plants,
                        plantIndex,
                        cursor.next()
                );
            }

            addTick(mapper, actions, tick, plants);
        }
    }

    /**
     * Alternates between two species during a tick range.
     */
    private static void plantAlternatingRange(
            ArrayNode actions,
            ObjectMapper mapper,
            Cursor cursor,
            int startTick,
            int endTick,
            int first,
            int second
    ) {

        for (
                int tick = startTick;
                tick <= endTick && cursor.hasNext();
                tick++
        ) {

            ArrayNode plants = mapper.createArrayNode();

            for (
                    int i = 0;
                    i < MAX_PLANTS_PER_TICK && cursor.hasNext();
                    i++
            ) {

                int plant =
                        (i % 2 == 0)
                                ? first
                                : second;

                addPlant(
                        mapper,
                        plants,
                        plant,
                        cursor.next()
                );
            }

            addTick(mapper, actions, tick, plants);
        }
    }

    /**
     * Cycles through a collection of species over a tick range.
     */
    private static void plantRotatingRange(
            ArrayNode actions,
            ObjectMapper mapper,
            Cursor cursor,
            int startTick,
            int endTick,
            int[] species
    ) {

        int rotation = 0;

        for (
                int tick = startTick;
                tick <= endTick && cursor.hasNext();
                tick++
        ) {

            ArrayNode plants = mapper.createArrayNode();

            for (
                    int i = 0;
                    i < MAX_PLANTS_PER_TICK && cursor.hasNext();
                    i++
            ) {

                int plant =
                        species[rotation % species.length];

                rotation++;

                addPlant(
                        mapper,
                        plants,
                        plant,
                        cursor.next()
                );
            }

            addTick(mapper, actions, tick, plants);
        }
    }

    /**
     * Adds one plant placement to a plants array.
     */
    private static void addPlant(
            ObjectMapper mapper,
            ArrayNode plants,
            int plantIndex,
            Cell cell
    ) {

        ObjectNode plant = mapper.createObjectNode();

        plant.put("plant_index", plantIndex);
        plant.put("row", cell.row);
        plant.put("col", cell.col);

        plants.add(plant);
    }

    /**
     * Adds one tick action to the final submission.
     */
    private static void addTick(
            ObjectMapper mapper,
            ArrayNode actions,
            int tick,
            ArrayNode plants
    ) {

        if (plants.isEmpty()) {
            return;
        }

        ObjectNode action = mapper.createObjectNode();

        action.put("tick", tick);
        action.set("plants", plants);

        actions.add(action);
    }

    /**
     * Performs structural validation before writing the submission.
     */
    private static void validate(
            ArrayNode actions,
            int rows,
            int cols,
            int ticks
    ) {

        boolean[] usedTicks = new boolean[ticks];

        for (JsonNode action : actions) {

            int tick = action.get("tick").asInt();

            if (tick < 0 || tick >= ticks) {

                throw new IllegalStateException(
                        "Invalid tick: " + tick
                );
            }

            if (usedTicks[tick]) {

                throw new IllegalStateException(
                        "Duplicate tick: " + tick
                );
            }

            usedTicks[tick] = true;

            JsonNode plants = action.get("plants");

            if (
                    plants == null
                            || !plants.isArray()
            ) {

                throw new IllegalStateException(
                        "Missing plants array at tick "
                                + tick
                );
            }

            if (plants.size() > MAX_PLANTS_PER_TICK) {

                throw new IllegalStateException(
                        "More than 20 plants at tick "
                                + tick
                );
            }

            for (JsonNode plant : plants) {

                int plantIndex =
                        plant.get("plant_index").asInt();

                int row =
                        plant.get("row").asInt();

                int col =
                        plant.get("col").asInt();

                if (
                        plantIndex < 1
                                || plantIndex > 31
                ) {

                    throw new IllegalStateException(
                            "Invalid plant index: "
                                    + plantIndex
                    );
                }

                if (
                        row < 0
                                || row >= rows
                                || col < 0
                                || col >= cols
                ) {

                    throw new IllegalStateException(
                            "Coordinate outside world: "
                                    + row
                                    + ","
                                    + col
                    );
                }
            }
        }
    }

    /**
     * Immutable grid coordinate.
     */
    private static final class Cell {

        private final int row;
        private final int col;

        private Cell(
                int row,
                int col
        ) {

            this.row = row;
            this.col = col;
        }
    }

    /**
     * Allocates each planting coordinate only once.
     */
    private static final class Cursor {

        private final List<Cell> cells;

        private int index;

        private Cursor(
                List<Cell> cells
        ) {

            this.cells = cells;
        }

        private boolean hasNext() {

            return index < cells.size();
        }

        private Cell next() {

            return cells.get(index++);
        }
    }
}
