package com.photospheria;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * Generates a deterministic, multi-phase Level 2 planting plan.
 *
 * <p>The strategy is designed around known Level 2 unlock chains:</p>
 * <ul>
 *     <li>Grass + Rose Bush -> Loamcrawlers -> Blue Moss</li>
 *     <li>Lavender -> Nectaris</li>
 *     <li>Dwarf Sunflower + Rose Bush -> Solwings</li>
 *     <li>Blue Moss + animal support -> Silver Fern</li>
 *     <li>Blue Moss + Crimson Vine -> Purple Canopy Tree</li>
 *     <li>Nectaris + Lavender + Blue Moss -> Moonpetal Lily</li>
 *     <li>Rain + sufficient Blue Moss -> Mire Bloom</li>
 * </ul>
 *
 * <p>The plan intentionally plants coverage thresholds directly instead of
 * depending entirely on stochastic-looking spread behaviour. It also reserves
 * a late refresh phase so the final sample contains younger, surviving plants.</p>
 */
public final class Level2Strategy {

    public static final int GRASS = 1;
    public static final int ROSE_BUSH = 2;
    public static final int BLUE_MOSS = 3;
    public static final int CRIMSON_VINE = 4;
    public static final int DWARF_SUNFLOWER = 5;
    public static final int LAVENDER = 6;
    public static final int ORANGE_BLOSSOM = 7;
    public static final int SILVER_FERN = 8;
    public static final int PURPLE_CANOPY_TREE = 10;
    public static final int OAK_TREE = 12;
    public static final int MOONPETAL_LILY = 15;
    public static final int MIRE_BLOOM = 18;
    public static final int SKYVINE = 20;

    private static final int MAX_PLANTS_PER_TICK = 20;

    private Level2Strategy() {
        // Utility class; no instances required.
    }

    /**
     * Builds the complete Level 2 action list.
     *
     * @param grid    parsed Level 2 grid
     * @param mapper  Jackson mapper used to create JSON nodes
     * @param actions destination array for generated tick actions
     */
    public static void build(GridModel grid, ObjectMapper mapper, ArrayNode actions) {
        List<int[]> cells = grid.getPlantableCells();
        CellCursor cursor = new CellCursor(cells);

        if (!grid.areAnimalsEnabled()) {
            throw new IllegalStateException("Level2Strategy requires animals_enabled=true.");
        }

        /*
         * PHASE 1 - Force the easiest animal triggers and coverage thresholds.
         * Level 2 contains 7,000 cells, so the direct counts below are chosen
         * around the documented 2%, 3% and 4% coverage thresholds.
         */
        int tick = 0;
        tick = plantBatch(actions, mapper, cursor, tick, GRASS, 300);          // > 4%
        tick = plantBatch(actions, mapper, cursor, tick, ROSE_BUSH, 180);      // > 2%
        tick = plantBatch(actions, mapper, cursor, tick, LAVENDER, 300);       // > 4%
        tick = plantBatch(actions, mapper, cursor, tick, DWARF_SUNFLOWER, 220);// > 3%
        tick = plantBatch(actions, mapper, cursor, tick, OAK_TREE, 20);        // Barkskips trigger

        /*
         * Small delay gives the animal/unlock state time to react before the
         * first locked species is requested.
         */
        tick = Math.max(tick + 8, 70);

        /*
         * PHASE 2 - Blue Moss chain.
         * Loamcrawlers should be present after the Grass/Rose thresholds.
         * 380 Blue Moss plants exceed 5% of the 7,000-cell world, which also
         * prepares the Rain -> Mire Bloom unlock later in the run.
         */
        tick = plantBatch(actions, mapper, cursor, tick, BLUE_MOSS, 380);

        /*
         * PHASE 3 - Flowering/vine unlocks.
         * Nectaris should already be active from Lavender coverage, allowing
         * Crimson Vine and Orange Blossom to be planted.
         */
        tick += 5;
        tick = plantBatch(actions, mapper, cursor, tick, CRIMSON_VINE, 300);   // > 4%
        tick = plantBatch(actions, mapper, cursor, tick, ORANGE_BLOSSOM, 160); // > 2%

        /*
         * PHASE 4 - Secondary unlocks that depend on Blue Moss and Crimson
         * Vine. Silver Fern is pushed above 4% to support later chains.
         */
        tick += 5;
        tick = plantBatch(actions, mapper, cursor, tick, SILVER_FERN, 300);
        tick = plantBatch(actions, mapper, cursor, tick, PURPLE_CANOPY_TREE, 80);
        tick = plantBatch(actions, mapper, cursor, tick, MOONPETAL_LILY, 300); // > 4%
        tick = plantBatch(actions, mapper, cursor, tick, SKYVINE, 120);

        /*
         * PHASE 5 - Rain event unlock.
         * Level 2 Rain occurs at tick 250. We deliberately schedule Mire Bloom
         * after that point so the event condition can already be satisfied.
         */
        tick = Math.max(tick, 255);
        tick = plantBatch(actions, mapper, cursor, tick, MIRE_BLOOM, 220);

        /*
         * PHASE 6 - Late diversity refresh.
         * Early plants can run out of nutrients before the final tick. Fresh
         * plants from several unlocked species are therefore added near the end
         * of the simulation. The refresh remains balanced to improve final
         * diversity rather than allowing one starter species to dominate.
         */
        int refreshTick = 405;
        int[] refreshSpecies = {
                GRASS,
                ROSE_BUSH,
                BLUE_MOSS,
                CRIMSON_VINE,
                DWARF_SUNFLOWER,
                LAVENDER,
                ORANGE_BLOSSOM,
                SILVER_FERN,
                PURPLE_CANOPY_TREE,
                OAK_TREE,
                MOONPETAL_LILY,
                MIRE_BLOOM,
                SKYVINE
        };

        while (refreshTick < grid.getTotalTicks() && cursor.hasNext()) {
            ArrayNode plants = mapper.createArrayNode();

            for (int slot = 0;
                 slot < MAX_PLANTS_PER_TICK && cursor.hasNext();
                 slot++) {
                int[] position = cursor.next();
                int species = refreshSpecies[slot % refreshSpecies.length];
                appendPlantAction(plants, mapper, species, position[0], position[1]);
            }

            if (!plants.isEmpty()) {
                appendTickAction(actions, mapper, refreshTick, plants);
            }

            refreshTick++;
        }
    }

    /**
     * Plants one species across as many consecutive ticks as required.
     *
     * @param actions     destination action array
     * @param mapper      Jackson mapper
     * @param cursor      source of unused coordinates
     * @param startTick   first tick on which planting may occur
     * @param plantIndex  Photospheria plant index
     * @param targetCount desired number of direct placements
     * @return first unused tick after this batch
     */
    private static int plantBatch(
            ArrayNode actions,
            ObjectMapper mapper,
            CellCursor cursor,
            int startTick,
            int plantIndex,
            int targetCount
    ) {
        int remaining = targetCount;
        int tick = startTick;

        while (remaining > 0 && cursor.hasNext()) {
            ArrayNode plants = mapper.createArrayNode();
            int countThisTick = Math.min(MAX_PLANTS_PER_TICK, remaining);

            for (int i = 0; i < countThisTick && cursor.hasNext(); i++) {
                int[] position = cursor.next();
                appendPlantAction(plants, mapper, plantIndex, position[0], position[1]);
                remaining--;
            }

            if (!plants.isEmpty()) {
                appendTickAction(actions, mapper, tick, plants);
            }
            tick++;
        }

        return tick;
    }

    /**
     * Adds one plant placement to a tick's plants array.
     *
     * @param container  plants array for one tick
     * @param mapper     Jackson mapper
     * @param plantIndex catalogue index of the species
     * @param row        target row
     * @param col        target column
     */
    private static void appendPlantAction(
            ArrayNode container,
            ObjectMapper mapper,
            int plantIndex,
            int row,
            int col
    ) {
        ObjectNode plant = mapper.createObjectNode();
        plant.put("plant_index", plantIndex);
        plant.put("row", row);
        plant.put("col", col);
        container.add(plant);
    }

    /**
     * Adds one complete tick action to the submission.
     *
     * @param actions action array for the whole submission
     * @param mapper  Jackson mapper
     * @param tick    simulation tick
     * @param plants  placements scheduled for the tick
     */
    private static void appendTickAction(
            ArrayNode actions,
            ObjectMapper mapper,
            int tick,
            ArrayNode plants
    ) {
        ObjectNode action = mapper.createObjectNode();
        action.put("tick", tick);
        action.set("plants", plants);
        actions.add(action);
    }

    /**
     * Simple deterministic cursor that guarantees each directly planted cell is
     * used at most once by this strategy.
     */
    private static final class CellCursor {
        private final List<int[]> cells;
        private int index;

        /**
         * Creates a cursor over an ordered list of plantable cells.
         *
         * @param cells plantable coordinates
         */
        private CellCursor(List<int[]> cells) {
            this.cells = cells;
            this.index = 0;
        }

        /** @return whether another unused coordinate exists */
        private boolean hasNext() {
            return index < cells.size();
        }

        /**
         * Returns the next unused coordinate.
         *
         * @return {@code [row, col]} coordinate
         */
        private int[] next() {
            return cells.get(index++);
        }
    }
}
