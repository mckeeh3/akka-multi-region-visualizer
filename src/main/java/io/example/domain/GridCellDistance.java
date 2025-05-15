package io.example.domain;

/**
 * Utility class for calculating distances between grid cells and points.
 */
public class GridCellDistance {

    /**
     * Calculates the Euclidean distance from a grid cell to a center point.
     * 
     * @param cellId  The ID of the grid cell in "RxC" format (row x column)
     * @param centerX The X-coordinate of the center point
     * @param centerY The Y-coordinate of the center point
     * @return The Euclidean distance from the cell to the center point
     */
    public static double calculateDistance(String cellId, int centerX, int centerY) {
        String[] rc = cellId.split("x"); // RxC / YxX
        int x = Integer.parseInt(rc[1]);
        int y = Integer.parseInt(rc[0]);

        return Math.sqrt(Math.pow(centerX - x, 2) + Math.pow(centerY - y, 2));
    }

    /**
     * Determines whether a grid cell is within a specified radius from a center point. This is equivalent to the insideRadius method in GridCell.State
     * but provided as a utility.
     * 
     * @param cellId  The ID of the grid cell in "RxC" format (row x column)
     * @param centerX The X-coordinate of the center point
     * @param centerY The Y-coordinate of the center point
     * @param radius  The radius to check against (limited to a maximum of 50)
     * @return True if the cell is inside the radius, false otherwise
     */
    public static boolean isInsideRadius(String cellId, int centerX, int centerY, int radius) {
        return calculateDistance(cellId, centerX, centerY) <= Math.min(50, radius);
    }

    /**
     * Calculates the Manhattan distance from a grid cell to a center point. This is the sum of the absolute differences of their Cartesian coordinates.
     * 
     * @param cellId  The ID of the grid cell in "RxC" format (row x column)
     * @param centerX The X-coordinate of the center point
     * @param centerY The Y-coordinate of the center point
     * @return The Manhattan distance from the cell to the center point
     */
    public static int calculateManhattanDistance(String cellId, int centerX, int centerY) {
        String[] rc = cellId.split("x"); // RxC / YxX
        int x = Integer.parseInt(rc[1]);
        int y = Integer.parseInt(rc[0]);

        return Math.abs(centerX - x) + Math.abs(centerY - y);
    }
}
