package com.photospheria;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Represents the Photospheria level grid and exposes deterministic lists of
 * coordinates that can be used by a planting strategy.
 *
 * <p>The level JSON is sparse: cells not explicitly listed are treated as the
 * default terrain. This implementation preserves the assumption already used
 * by the project that terrain value {@code 0} is plantable soil and non-zero
 * terrain values are obstacles/non-soil cells.</p>
 */
public class GridModel {

    private final int rows;
    private final int cols;
    private final int totalTicks;
    private final boolean animalsEnabled;
    private final boolean[][] plantableSoil;

    /**
     * Builds a grid model from one level JSON document.
     *
     * @param levelRoot root JSON node containing rows, columns, ticks,
     *                  animals_enabled and the sparse cells array
     * @throws IllegalArgumentException when required level metadata is missing
     */
    public GridModel(JsonNode levelRoot) {
        if (levelRoot == null
                || !levelRoot.has("rows")
                || !levelRoot.has("cols")
                || !levelRoot.has("ticks")
                || !levelRoot.has("animals_enabled")) {
            throw new IllegalArgumentException("Level JSON is missing required metadata.");
        }

        this.rows = levelRoot.get("rows").asInt();
        this.cols = levelRoot.get("cols").asInt();
        this.totalTicks = levelRoot.get("ticks").asInt();
        this.animalsEnabled = levelRoot.get("animals_enabled").asBoolean();
        this.plantableSoil = new boolean[rows][cols];

        initialiseDefaultSoil();
        applySparseTerrainOverrides(levelRoot.get("cells"));
    }

    /**
     * Marks every grid position as plantable before sparse terrain overrides
     * from the level file are applied.
     */
    private void initialiseDefaultSoil() {
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                plantableSoil[row][col] = true;
            }
        }
    }

    /**
     * Applies terrain values from the sparse level cell array.
     *
     * @param cells sparse terrain cell definitions, or {@code null}
     */
    private void applySparseTerrainOverrides(JsonNode cells) {
        if (cells == null || !cells.isArray()) {
            return;
        }

        for (JsonNode cell : cells) {
            int row = cell.path("row").asInt(-1);
            int col = cell.path("col").asInt(-1);
            int terrain = cell.has("terrain") ? cell.get("terrain").asInt() : 0;

            if (isInsideGrid(row, col)) {
                plantableSoil[row][col] = terrain == 0;
            }
        }
    }

    /**
     * Returns plantable coordinates in a deterministic spread-out order.
     *
     * <p>Instead of consuming the map strictly row by row, cells are ordered by
     * a checkerboard-style bucket first. This reduces the large solid strips
     * produced by the earlier Level 2 submission and gives naturally spreading
     * plants more surrounding space.</p>
     *
     * @return ordered list of {@code [row, col]} coordinate pairs
     */
    public List<int[]> getPlantableCells() {
        List<int[]> validCells = new ArrayList<>();

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                if (plantableSoil[row][col]) {
                    validCells.add(new int[]{row, col});
                }
            }
        }

        validCells.sort(Comparator
                .comparingInt((int[] p) -> ((p[0] & 1) << 1) + (p[1] & 1))
                .thenComparingInt(p -> p[0])
                .thenComparingInt(p -> p[1]));

        return validCells;
    }

    /**
     * Checks whether a coordinate lies inside the level boundaries.
     *
     * @param row zero-based row
     * @param col zero-based column
     * @return {@code true} when the coordinate is inside the grid
     */
    public boolean isInsideGrid(int row, int col) {
        return row >= 0 && row < rows && col >= 0 && col < cols;
    }

    /** @return number of rows in the level */
    public int getRows() {
        return rows;
    }

    /** @return number of columns in the level */
    public int getCols() {
        return cols;
    }

    /** @return total number of simulation ticks */
    public int getTotalTicks() {
        return totalTicks;
    }

    /** @return whether animal mechanics are enabled for this level */
    public boolean areAnimalsEnabled() {
        return animalsEnabled;
    }
}
