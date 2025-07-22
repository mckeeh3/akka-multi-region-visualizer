package io.example.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.example.application.GridCellView;

public class PredatorTest {

  @Test
  // @Disabled
  void testNextCellNorth() {
    var xyTopLeft = 8;
    var color = "blue";
    var rows = 5;
    var cols = 5;
    var predatorRowCol = Point.fromRowCol(5, 10);
    var predatorRange = 50;
    var nextGridCellId = Predator.nextGridCellId(predatorRowCol.id(), createGridCells(xyTopLeft, color, rows, cols), predatorRange);
    assertEquals(Point.fromRowCol(predatorRowCol.row(), predatorRowCol.col() + 1).id(), nextGridCellId);
  }

  @Test
  // @Disabled
  void testNextCellNorthEast() {
    var xyTopLeft = 8;
    var color = "blue";
    var rows = 5;
    var cols = 5;
    var predatorRowCol = Point.fromRowCol(5, 15);
    var predatorRange = 50;
    var nextGridCellId = Predator.nextGridCellId(predatorRowCol.id(), createGridCells(xyTopLeft, color, rows, cols), predatorRange);
    assertEquals(Point.fromRowCol(predatorRowCol.row() - 1, predatorRowCol.col() + 1).id(), nextGridCellId);
  }

  @Test
  // @Disabled
  void testNextCellEast() {
    var xyTopLeft = 8;
    var color = "blue";
    var rows = 5;
    var cols = 5;
    var predatorRowCol = Point.fromRowCol(10, 15);
    var predatorRange = 50;
    var nextGridCellId = Predator.nextGridCellId(predatorRowCol.id(), createGridCells(xyTopLeft, color, rows, cols), predatorRange);
    assertEquals(Point.fromRowCol(predatorRowCol.row() - 1, predatorRowCol.col()).id(), nextGridCellId);
  }

  @Test
  // @Disabled
  void testNextCellSouthEast() {
    var xyTopLeft = 8;
    var color = "blue";
    var rows = 5;
    var cols = 5;
    var predatorRowCol = Point.fromRowCol(15, 15);
    var predatorRange = 50;
    var nextGridCellId = Predator.nextGridCellId(predatorRowCol.id(), createGridCells(xyTopLeft, color, rows, cols), predatorRange);
    assertEquals(Point.fromRowCol(predatorRowCol.col() - 1, predatorRowCol.row() - 1).id(), nextGridCellId);
  }

  @Test
  // @Disabled
  void testNextCellSouth() {
    var xyTopLeft = 8;
    var color = "blue";
    var rows = 5;
    var cols = 5;
    var predatorRowCol = Point.fromRowCol(15, 10);
    var predatorRange = 50;
    var nextGridCellId = Predator.nextGridCellId(predatorRowCol.id(), createGridCells(xyTopLeft, color, rows, cols), predatorRange);
    assertEquals(Point.fromRowCol(predatorRowCol.row(), predatorRowCol.col() - 1).id(), nextGridCellId);
  }

  @Test
  // @Disabled
  void testNextCellSouthWest() {
    var xyTopLeft = 8;
    var color = "blue";
    var rows = 5;
    var cols = 5;
    var predatorRowCol = Point.fromRowCol(15, 5);
    var predatorRange = 50;
    var nextGridCellId = Predator.nextGridCellId(predatorRowCol.id(), createGridCells(xyTopLeft, color, rows, cols), predatorRange);
    assertEquals(Point.fromRowCol(predatorRowCol.row() + 1, predatorRowCol.col() - 1).id(), nextGridCellId);
  }

  @Test
  // @Disabled
  void testNextCellWest() {
    var xyTopLeft = 8;
    var color = "blue";
    var rows = 5;
    var cols = 5;
    var predatorRowCol = Point.fromRowCol(10, 5);
    var predatorRange = 50;
    var nextGridCellId = Predator.nextGridCellId(predatorRowCol.id(), createGridCells(xyTopLeft, color, rows, cols), predatorRange);
    assertEquals(Point.fromRowCol(predatorRowCol.row() + 1, predatorRowCol.col()).id(), nextGridCellId);
  }

  @Test
  // @Disabled
  void testNextCellNorthWest() {
    var xyTopLeft = 8;
    var color = "blue";
    var rows = 5;
    var cols = 5;
    var predatorRowCol = Point.fromRowCol(5, 5);
    var predatorRange = 50;
    var nextGridCellId = Predator.nextGridCellId(predatorRowCol.id(), createGridCells(xyTopLeft, color, rows, cols), predatorRange);
    assertEquals(Point.fromRowCol(predatorRowCol.row() + 1, predatorRowCol.col() + 1).id(), nextGridCellId);
  }

  @Test
  // @Disabled
  void testDetectionDistanceOfSingleCell() {
    var preyRow = 5;
    var preyCol = 15;
    var preyCell = createGridCell(preyCol, preyRow, "blue");

    var predatorRowCol = Point.fromRowCol(preyRow + 100, preyCol);
    var predatorRange = 200;
    var nextGridCellId = Predator.nextGridCellId(predatorRowCol.id(), List.of(preyCell), predatorRange);
    assertEquals(Point.fromRowCol(predatorRowCol.row(), predatorRowCol.col() - 1).id(), nextGridCellId);
  }

  @Test
  // @Disabled
  void testOneColumnAboveVertical() {
    var xyTopLeft = 100;
    var color = "blue";
    var rows = 30;
    var cols = 1;
    var predatorRowCol = Point.fromRowCol(xyTopLeft, xyTopLeft - 2);
    var predatorRange = 50;
    var nextGridCellId = Predator.nextGridCellId(predatorRowCol.id(), createGridCells(xyTopLeft, color, rows, cols), predatorRange);
    assertEquals(Point.fromRowCol(predatorRowCol.row() + 1, predatorRowCol.col()).id(), nextGridCellId);
  }

  @Test
  // @Disabled
  void testOneRowRightHorizontal() {
    var xyTopLeft = 100;
    var color = "blue";
    var rows = 1;
    var cols = 30;
    var predatorRowCol = Point.fromRowCol(xyTopLeft - 2, xyTopLeft);
    var predatorRange = 50;
    var nextGridCellId = Predator.nextGridCellId(predatorRowCol.id(), createGridCells(xyTopLeft, color, rows, cols), predatorRange);
    assertEquals(Point.fromRowCol(predatorRowCol.row(), predatorRowCol.col() + 1).id(), nextGridCellId);
  }

  @Test
  // @Disabled
  void testNearbySmallClusterDistantLargeCluster() {
    var predatorRowCol = Point.fromRowCol(100, 100);
    var predatorRange = 100;

    var largeClusterRows = 11;
    var largeClusterCols = 11;
    var largeClusterXTopLeft = predatorRowCol.row() - 50;
    var largeClusterYTopLeft = predatorRowCol.col() - Math.round(largeClusterRows / 2);
    var largeClusterColor = "blue";
    var largeClusterPreyCells = createGridCells(largeClusterXTopLeft, largeClusterYTopLeft, largeClusterColor, largeClusterRows, largeClusterCols);

    var smallClusterRows = 5;
    var smallClusterCols = 5;
    var smallClusterXTopLeft = predatorRowCol.row() + 10;
    var smallClusterYTopLeft = predatorRowCol.col() - Math.round(smallClusterCols / 2);
    var smallClusterColor = "red";
    var smallClusterPreyCells = createGridCells(smallClusterXTopLeft, smallClusterYTopLeft, smallClusterColor, smallClusterRows, smallClusterCols);

    var allPreyCells = Stream.concat(largeClusterPreyCells.stream(), smallClusterPreyCells.stream()).toList();

    var nextGridCellId = Predator.nextGridCellId(predatorRowCol.id(), allPreyCells, predatorRange);
    assertEquals(Point.fromRowCol(predatorRowCol.row(), predatorRowCol.col() + 1).id(), nextGridCellId);
  }

  @Test
  void testShortRangeSearch() {
    var predatorX = 100;
    var predatorY = 100;
    var predatorId = "%sx%s".formatted(predatorY, predatorX); // RxC, YxX
    var predatorRange = 100;

    var largeClusterRows = 11;
    var largeClusterCols = 11;
    var largeClusterXTopLeft = predatorX - Math.round(largeClusterCols / 2);
    var largeClusterYTopLeft = predatorY - Math.round(largeClusterRows / 2);
    var largeClusterColor = "blue";
    var largeClusterPreyCells = createGridCells(largeClusterXTopLeft, largeClusterYTopLeft, largeClusterColor, largeClusterRows, largeClusterCols);

    var nextGridCellId = Predator.nextGridCellId(predatorId, largeClusterPreyCells, predatorRange);
    assertTrue(Point.fromId(nextGridCellId).isNeighborOf(Point.fromId(predatorId)));
  }

  // Create a cluster of prey cells
  List<GridCellView.GridCellRow> createGridCells(int xyTopLeft, String color, int rows, int cols) {
    var gridCells = IntStream.range(xyTopLeft, rows + xyTopLeft)
        .mapToObj(rowY -> IntStream.range(xyTopLeft, cols + xyTopLeft)
            .mapToObj(colX -> createGridCell(colX, rowY, color))
            .toList())
        .toList();
    return gridCells.stream().flatMap(List::stream).toList();
  }

  // Create a cluster of prey cells
  List<GridCellView.GridCellRow> createGridCells(int xTopLeft, int yTopLeft, String color, int rows, int cols) {
    var gridCells = IntStream.range(yTopLeft, rows + yTopLeft)
        .mapToObj(rowY -> IntStream.range(xTopLeft, cols + xTopLeft)
            .mapToObj(colX -> createGridCell(colX, rowY, color))
            .toList())
        .toList();
    return gridCells.stream().flatMap(List::stream).toList();
  }

  GridCellView.GridCellRow createGridCell(int x, int y, String status) {
    return new GridCellView.GridCellRow(
        "" + y + "x" + x, // RxC, YxX
        status,
        GridCell.Color.of(status).toRgba(),
        x,
        y,
        Instant.now(),
        Instant.now(),
        Instant.now(),
        Instant.now(),
        Instant.now(),
        0,
        "",
        "",
        "");
  }

  @Test
  @Disabled
  void testDirectionVector() {
    var directionVector = new DirectionVector(1, 0);
    assertEquals(0, directionVector.degrees());
    assertEquals(0, directionVector.radians());
    assertEquals(1, directionVector.row());
    assertEquals(0, directionVector.col());
    assertEquals(0, directionVector.normalized().degrees());
    assertEquals(0, directionVector.normalized().radians());
    assertEquals(1, directionVector.normalized().row());
    assertEquals(0, directionVector.normalized().col());
  }
}
