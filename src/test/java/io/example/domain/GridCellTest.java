package io.example.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

public class GridCellTest {

  @Test
  public void testLineShape() {
    var line = GridCell.Shape.ofLine(0, 0, 0, 10, 1);
    assertTrue(line.isInside(0, 0));
    assertTrue(line.isInside(0, 10));
    assertFalse(line.isInside(1, 0));
    assertFalse(line.isInside(1, 10));
  }

  @Test
  public void testLineShapeWithMultipleAngles() {
    var angles = List.of(0.0, 45.0, 90.0, 135.0, 180.0, 225.0, 270.0, 315.0);
    for (var angle : angles) {
      var length = 10;
      var line = (GridCell.Shape.Line) GridCell.Shape.ofLine(0, 0, angle, length, 1);
      // do the math to get the expected end row and col
      // Using standard mathematical coordinate system:
      // 0° = right (positive col direction)
      // 90° = up (negative row direction)
      var expectedEndRow = (int) Math.round(-Math.sin(Math.toRadians(angle)) * length);
      var expectedEndCol = (int) Math.round(Math.cos(Math.toRadians(angle)) * length);
      System.out.println("angle: " + angle + ", expectedEndRow: " + expectedEndRow + ", expectedEndCol: " + expectedEndCol);
      assertTrue(line.isInside(0, 0));
      assertTrue(line.isInside(expectedEndRow, expectedEndCol));
      assertFalse(line.isInside(expectedEndRow + (expectedEndRow < 0 ? -1 : 1), expectedEndCol));
      assertFalse(line.isInside(line.endRow(), line.endCol() + (expectedEndCol < 0 ? -1 : 1)));
    }
  }

  @Test
  public void testLineShapeAt90Degrees() {
    var line = (GridCell.Shape.Line) GridCell.Shape.ofLine(0, 0, 90.0, 10, 1);
    System.out.println("Line from (0,0) to (" + line.endRow() + "," + line.endCol() + ")");

    // For 90 degrees, we expect:
    // startRow = 0, startCol = 0
    // endRow = -10 (up 10 units), endCol = 0 (no horizontal movement)
    System.out.println("Expected: startRow=0, startCol=0, endRow=-10, endCol=0");
    System.out.println("Actual: startRow=" + line.startRow() + ", startCol=" + line.startCol() +
        ", endRow=" + line.endRow() + ", endCol=" + line.endCol());

    // Test points that should be inside the line
    assertTrue(line.isInside(0, 0)); // start point
    assertTrue(line.isInside(line.endRow(), line.endCol())); // end point

    // For a vertical line at col=0, any point with col!=0 should be outside
    assertFalse(line.isInside(0, 1)); // 1 unit to the right
    assertFalse(line.isInside(0, -1)); // 1 unit to the left
    assertFalse(line.isInside(-5, 1)); // 1 unit to the right of middle of line
    assertFalse(line.isInside(-5, -1)); // 1 unit to the left of middle of line
  }
}
