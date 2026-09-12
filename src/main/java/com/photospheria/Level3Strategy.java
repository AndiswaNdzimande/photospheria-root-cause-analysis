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
 * Builds a deterministic Level 3 submission for the Photospheria challenge.
 *
 * <p>The strategy is intentionally split into phases. It first establishes the
 * five starter species, then targets animal/unlock conditions, then takes
 * advantage of the Level 3 world events, and finally adds a late diversity
 * refresh so plants are more likely to survive until the scoring tick.</p>
 *
 * <p>This class does not attempt to simulate every hidden ecosystem rule.
 * Instead, it creates a legal, deterministic heuristic submission that is
 * much stronger than planting only Grass at tick 0.</p>
 */
public final class Level3Strategy {

    private static final int MAX_PLANTS_PER_TICK = 20;

    // Starter plants.
    private static final int GRASS = 1;
    private static final int ROSE_BUSH = 2;
    private static final int DWARF_SUNFLOWER = 5;
    private static final int LAVENDER = 6;
    private static final int OAK_TREE = 12;

    // Early/mid-game unlock targets.
    private static final int BLUE_MOSS = 3;
    private static final int CRIMSON_VINE = 4;
    private static final int ORANGE_BLOSSOM = 7;
    private static final int SILVER_FERN = 8;
    private static final int GLOWCAP_FUNGUS = 9;
    private static final int CRYSTAL_CACTUS = 17;
    private static final int MIRE_BLOOM = 18;

    private Level3Strategy() {
        // Utility class.
    }

    /**
     * Reads Level 3 input, generates a multi-phase strategy and writes the
     * challenge-format JSON file to output/level3_submission.json.
     *
     * @throws Exception if the level file cannot be read or output cannot be written
     */
    public static void execute() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode input = mapper.readTree(new File("data/3.json"));

        int rows = input.get("rows").asInt();
        int cols = input.get("cols").asInt();
        int ticks = input.get("ticks").asInt();
        boolean animalsEnabled = input.get("animals_enabled").asBoolean();

        System.out.println("Photospheria Level 3");
        System.out.println("--------------------");
        System.out.println("Rows: " + rows);
        System.out.println("Columns: " + cols);
        System.out.println("Ticks: " + ticks);
        System.out.println("Animals enabled: " + animalsEnabled);

        List<Cell> plantableCells = findPlantableCells(input, rows, cols);
        spreadCellsAcrossMap(plantableCells, rows, cols);

        System.out.println("Plantable cells found: " + plantableCells.size());

        ObjectNode root = mapper.createObjectNode();
        ArrayNode actions = mapper.createArrayNode();

        Cursor cursor = new Cursor(plantableCells);

        /*
         * PHASE 1 — Establish the starter ecosystem.
         *
         * Grass is pushed first because several animal and plant unlocks depend on
         * Grass coverage. Rose Bush then helps trigger Loamcrawlers, which is part
         * of the Blue Moss chain.
         */
        plantRange(actions, mapper, cursor, 0, 44, GRASS);
        plantRange(actions, mapper, cursor, 45, 57, ROSE_BUSH);

        /*
         * PHASE 2 — Attempt the first major unlock.
         *
         * Once Grass and Rose conditions are established, Blue Moss is scheduled.
         * A large Blue Moss population is useful later for Silver Fern and for the
         * Rain-event species Mire Bloom.
         */
        plantRange(actions, mapper, cursor, 58, 112, BLUE_MOSS);

        /*
         * PHASE 3 — Pollination chain.
         *
         * Lavender is used to encourage Nectaris. Rose Bush is refreshed so its
         * coverage is still useful when Orange Blossom / Crimson Vine are attempted.
         */
        plantRange(actions, mapper, cursor, 113, 135, LAVENDER);
        plantRange(actions, mapper, cursor, 136, 158, ROSE_BUSH);

        /*
         * Level 3 Rain occurs at tick 150. Blue Moss is already heavily planted by
         * this point, so Mire Bloom is attempted immediately after the event.
         */
        plantRange(actions, mapper, cursor, 159, 176, MIRE_BLOOM);

        /*
         * Dwarf Sunflower supports the Solwings chain. Oak Trees are inexpensive
         * to establish and may trigger Barkskips.
         */
        plantRange(actions, mapper, cursor, 177, 210, DWARF_SUNFLOWER);
        plantRange(actions, mapper, cursor, 211, 212, OAK_TREE);

        /*
         * PHASE 4 — Diversity unlock attempts while pollination conditions are
         * still comparatively fresh.
         */
        plantAlternatingRange(
                actions, mapper, cursor,
                213, 236,
                ORANGE_BLOSSOM, CRIMSON_VINE
        );

        /*
         * Refresh Grass and Rose to give Loamcrawler-dependent species another
         * window later in the simulation.
         */
        plantRange(actions, mapper, cursor, 237, 281, GRASS);
        plantRange(actions, mapper, cursor, 282, 304, ROSE_BUSH);

        /*
         * Level 3 Ash Eclipse occurs at tick 300. We do not blindly submit deep
         * Ash-Eclipse species that require several unverified preceding unlocks.
         * Instead we continue building species whose prerequisites are earlier
         * and more realistic.
         */
        plantRange(actions, mapper, cursor, 305, 338, SILVER_FERN);

        /*
         * By this point a substantial amount of early vegetation may have died,
         * producing dead matter. Glowcap Fungus is therefore attempted later,
         * when its dead-matter prerequisite is more plausible.
         */
        plantRange(actions, mapper, cursor, 339, 372, GLOWCAP_FUNGUS);

        /*
         * Drought occurs very early in Level 3. If event state persists after the
         * event, this phase exploits the refreshed Grass coverage for Crystal Cactus.
         */
        plantRange(actions, mapper, cursor, 373, 395, CRYSTAL_CACTUS);

        /*
         * PHASE 5 — Mid-game resilience.
         *
         * Re-establish the starter mix before the final scoring window. This
         * improves ecosystem activity and avoids relying entirely on very old plants.
         */
        int[] starterMix = {GRASS, ROSE_BUSH, LAVENDER, DWARF_SUNFLOWER, OAK_TREE};
        plantRotatingRange(actions, mapper, cursor, 500, 579, starterMix);

        /*
         * PHASE 6 — Late survival and diversity refresh.
         *
         * Level 3 ends at tick 800. Plants placed from about tick 705 onward have
         * a much better chance of still being alive at the final scoring tick.
         *
         * The list contains starter plants plus the most realistic Level 3 unlock
         * targets from the earlier phases.
         */
        int[] finalDiversityMix = {
                GRASS,
                ROSE_BUSH,
                BLUE_MOSS,
                CRIMSON_VINE,
                DWARF_SUNFLOWER,
                LAVENDER,
                ORANGE_BLOSSOM,
                SILVER_FERN,
                GLOWCAP_FUNGUS,
                OAK_TREE,
                CRYSTAL_CACTUS,
                MIRE_BLOOM
        };

        plantRotatingRange(actions, mapper, cursor, 705, 799, finalDiversityMix);

        root.set("actions", actions);

        validate(actions, rows, cols, ticks);

        File outputFile = new File("output/level3_submission.json");
        File parent = outputFile.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }

        mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, root);

        System.out.println("Actions generated: " + actions.size());
        System.out.println("Validation passed.");
        System.out.println("Level 3 submission written to: " + outputFile.getPath());
    }

    /**
     * Finds cells that are safe candidates for planting.
     *
     * <p>The level files use a sparse cell list, so every coordinate is initially
     * treated as normal terrain. Explicit cells with a non-zero terrain value are
     * marked as blocked. Soil id 0 is not treated as blocked because it can
     * represent Dirt.</p>
     *
     * @param input root Level 3 JSON node
     * @param rows number of rows
     * @param cols number of columns
     * @return plantable coordinates
     */
    private static List<Cell> findPlantableCells(JsonNode input, int rows, int cols) {
        boolean[][] blocked = new boolean[rows][cols];

        JsonNode cells = input.get("cells");
        if (cells != null && cells.isArray()) {
            for (JsonNode cell : cells) {
                if (!cell.has("row") || !cell.has("col")) {
                    continue;
                }

                int row = cell.get("row").asInt();
                int col = cell.get("col").asInt();
                int terrain = cell.has("terrain") ? cell.get("terrain").asInt() : 0;

                if (row >= 0 && row < rows && col >= 0 && col < cols && terrain != 0) {
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
     * Reorders coordinates so consecutive placements are spread across the map
     * rather than filling complete rows from left to right.
     *
     * @param cells coordinates to reorder
     * @param rows world row count
     * @param cols world column count
     */
    private static void spreadCellsAcrossMap(List<Cell> cells, int rows, int cols) {
        cells.sort(
                Comparator
                        .comparingInt((Cell cell) -> Math.floorMod(cell.row * 37 + cell.col * 53, rows * cols))
                        .thenComparingInt(cell -> cell.row)
                        .thenComparingInt(cell -> cell.col)
        );
    }

    /**
     * Plants one species at up to 20 cells for every tick in a range.
     *
     * @param actions output action list
     * @param mapper Jackson mapper
     * @param cursor coordinate allocator
     * @param startTick first tick, inclusive
     * @param endTick last tick, inclusive
     * @param plantIndex plant catalogue index
     */
    private static void plantRange(
            ArrayNode actions,
            ObjectMapper mapper,
            Cursor cursor,
            int startTick,
            int endTick,
            int plantIndex
    ) {
        for (int tick = startTick; tick <= endTick && cursor.hasNext(); tick++) {
            ArrayNode plants = mapper.createArrayNode();

            for (int i = 0; i < MAX_PLANTS_PER_TICK && cursor.hasNext(); i++) {
                Cell cell = cursor.next();
                addPlant(mapper, plants, plantIndex, cell);
            }

            addTick(mapper, actions, tick, plants);
        }
    }

    /**
     * Alternates two plant species during a tick range.
     *
     * @param actions output actions
     * @param mapper Jackson mapper
     * @param cursor coordinate allocator
     * @param startTick first tick
     * @param endTick last tick
     * @param firstPlant first plant index
     * @param secondPlant second plant index
     */
    private static void plantAlternatingRange(
            ArrayNode actions,
            ObjectMapper mapper,
            Cursor cursor,
            int startTick,
            int endTick,
            int firstPlant,
            int secondPlant
    ) {
        for (int tick = startTick; tick <= endTick && cursor.hasNext(); tick++) {
            ArrayNode plants = mapper.createArrayNode();

            for (int i = 0; i < MAX_PLANTS_PER_TICK && cursor.hasNext(); i++) {
                Cell cell = cursor.next();
                int species = (i % 2 == 0) ? firstPlant : secondPlant;
                addPlant(mapper, plants, species, cell);
            }

            addTick(mapper, actions, tick, plants);
        }
    }

    /**
     * Rotates through a species catalogue while planting.
     *
     * @param actions output actions
     * @param mapper Jackson mapper
     * @param cursor coordinate allocator
     * @param startTick first tick
     * @param endTick last tick
     * @param species plant indices to rotate through
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

        for (int tick = startTick; tick <= endTick && cursor.hasNext(); tick++) {
            ArrayNode plants = mapper.createArrayNode();

            for (int i = 0; i < MAX_PLANTS_PER_TICK && cursor.hasNext(); i++) {
                Cell cell = cursor.next();
                int plantIndex = species[rotation % species.length];
                rotation++;

                addPlant(mapper, plants, plantIndex, cell);
            }

            addTick(mapper, actions, tick, plants);
        }
    }

    /**
     * Adds one plant placement to a tick.
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
     * Adds one tick action to the submission.
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
     * Validates the generated submission before it is written.
     *
     * @param actions generated actions
     * @param rows row count
     * @param cols column count
     * @param ticks total number of simulation ticks
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
                throw new IllegalStateException("Invalid tick: " + tick);
            }

            if (usedTicks[tick]) {
                throw new IllegalStateException("Duplicate action for tick: " + tick);
            }
            usedTicks[tick] = true;

            JsonNode plants = action.get("plants");
            if (plants == null || !plants.isArray()) {
                throw new IllegalStateException("Missing plants array at tick " + tick);
            }

            if (plants.size() > MAX_PLANTS_PER_TICK) {
                throw new IllegalStateException("More than 20 plants at tick " + tick);
            }

            for (JsonNode plant : plants) {
                int index = plant.get("plant_index").asInt();
                int row = plant.get("row").asInt();
                int col = plant.get("col").asInt();

                if (index < 1 || index > 31) {
                    throw new IllegalStateException("Invalid plant index: " + index);
                }

                if (row < 0 || row >= rows || col < 0 || col >= cols) {
                    throw new IllegalStateException(
                            "Coordinate out of bounds: (" + row + "," + col + ")"
                    );
                }
            }
        }
    }

    /**
     * Immutable world coordinate.
     */
    private static final class Cell {
        private final int row;
        private final int col;

        private Cell(int row, int col) {
            this.row = row;
            this.col = col;
        }
    }

    /**
     * Sequential allocator for unique plantable coordinates.
     */
    private static final class Cursor {
        private final List<Cell> cells;
        private int index;

        private Cursor(List<Cell> cells) {
            this.cells = cells;
            this.index = 0;
        }

        private boolean hasNext() {
            return index < cells.size();
        }

        private Cell next() {
            return cells.get(index++);
        }
    }
}

