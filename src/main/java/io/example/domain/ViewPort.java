package io.example.domain;

/**
 * Represents a viewport on the grid visualization system. A viewport defines a rectangular viewing area with mouse
 * position information.
 */
public record ViewPort(
    Location topLeft,
    Location bottomRight,
    Location mouse) {

  /**
   * Creates a ViewPort with the specified coordinates.
   * 
   * @param topLeftRow     the row coordinate of the top-left corner
   * @param topLeftCol     the column coordinate of the top-left corner
   * @param bottomRightRow the row coordinate of the bottom-right corner
   * @param bottomRightCol the column coordinate of the bottom-right corner
   * @param mouseRow       the row coordinate of the mouse position
   * @param mouseCol       the column coordinate of the mouse position
   * @return a new ViewPort instance
   */
  public static ViewPort of(int topLeftRow, int topLeftCol, int bottomRightRow, int bottomRightCol, int mouseRow, int mouseCol) {
    return new ViewPort(
        new Location(topLeftRow, topLeftCol),
        new Location(bottomRightRow, bottomRightCol),
        new Location(mouseRow, mouseCol));
  }

  /**
   * Represents a location on the grid with row and column coordinates.
   */
  public record Location(int row, int col) {}
}