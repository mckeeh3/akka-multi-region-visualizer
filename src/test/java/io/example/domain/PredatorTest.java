package io.example.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

import io.example.application.GridCellView;

public class PredatorTest {

  @Test
  // @Disabled
  void testNextCellNorth() {
    var xyTopLeft = 8;
    var color = "blue";
    var rows = 5;
    var cols = 5;
    var predatorX = 10;
    var predatorY = 5;
    var predatorId = "%sx%s".formatted(predatorY, predatorX); // RxC, YxX
    var predatorRange = 50;
    var nextGridCellId = Predator.nextGridCellId(predatorId, createGridCells(xyTopLeft, color, rows, cols), predatorRange);
    assertEquals("%dx%d".formatted(predatorY + 1, predatorX), nextGridCellId);
  }

  @Test
  // @Disabled
  void testNextCellNorthEast() {
    var xyTopLeft = 8;
    var color = "blue";
    var rows = 5;
    var cols = 5;
    var predatorX = 15;
    var predatorY = 5;
    var predatorId = "%sx%s".formatted(predatorY, predatorX); // RxC, YxX
    var predatorRange = 50;
    var nextGridCellId = Predator.nextGridCellId(predatorId, createGridCells(xyTopLeft, color, rows, cols), predatorRange);
    assertEquals("%dx%d".formatted(predatorY + 1, predatorX - 1), nextGridCellId);
  }

  @Test
  // @Disabled
  void testNextCellEast() {
    var xyTopLeft = 8;
    var color = "blue";
    var rows = 5;
    var cols = 5;
    var predatorX = 15;
    var predatorY = 10;
    var predatorId = "%sx%s".formatted(predatorY, predatorX); // RxC, YxX
    var predatorRange = 50;
    var nextGridCellId = Predator.nextGridCellId(predatorId, createGridCells(xyTopLeft, color, rows, cols), predatorRange);
    assertEquals("%dx%d".formatted(predatorY, predatorX - 1), nextGridCellId);
  }

  @Test
  // @Disabled
  void testNextCellSouthEast() {
    var xyTopLeft = 8;
    var color = "blue";
    var rows = 5;
    var cols = 5;
    var predatorX = 15;
    var predatorY = 15;
    var predatorId = "%sx%s".formatted(predatorY, predatorX); // RxC, YxX
    var predatorRange = 50;
    var nextGridCellId = Predator.nextGridCellId(predatorId, createGridCells(xyTopLeft, color, rows, cols), predatorRange);
    assertEquals("%dx%d".formatted(predatorY - 1, predatorX - 1), nextGridCellId);
  }

  @Test
  // @Disabled
  void testNextCellSouth() {
    var xyTopLeft = 8;
    var color = "blue";
    var rows = 5;
    var cols = 5;
    var predatorX = 10;
    var predatorY = 15;
    var predatorId = "%sx%s".formatted(predatorY, predatorX); // RxC, YxX
    var predatorRange = 50;
    var nextGridCellId = Predator.nextGridCellId(predatorId, createGridCells(xyTopLeft, color, rows, cols), predatorRange);
    assertEquals("%dx%d".formatted(predatorY - 1, predatorX), nextGridCellId);
  }

  @Test
  // @Disabled
  void testNextCellSouthWest() {
    var xyTopLeft = 8;
    var color = "blue";
    var rows = 5;
    var cols = 5;
    var predatorX = 5;
    var predatorY = 15;
    var predatorId = "%sx%s".formatted(predatorY, predatorX); // RxC, YxX
    var predatorRange = 50;
    var nextGridCellId = Predator.nextGridCellId(predatorId, createGridCells(xyTopLeft, color, rows, cols), predatorRange);
    assertEquals("%dx%d".formatted(predatorY - 1, predatorX + 1), nextGridCellId);
  }

  @Test
  // @Disabled
  void testNextCellWest() {
    var xyTopLeft = 8;
    var color = "blue";
    var rows = 5;
    var cols = 5;
    var predatorX = 5;
    var predatorY = 10;
    var predatorId = "%sx%s".formatted(predatorY, predatorX); // RxC, YxX
    var predatorRange = 50;
    var nextGridCellId = Predator.nextGridCellId(predatorId, createGridCells(xyTopLeft, color, rows, cols), predatorRange);
    assertEquals("%dx%d".formatted(predatorY, predatorX + 1), nextGridCellId);
  }

  @Test
  // @Disabled
  void testNextCellNorthWest() {
    var xyTopLeft = 8;
    var color = "blue";
    var rows = 5;
    var cols = 5;
    var predatorX = 5;
    var predatorY = 5;
    var predatorId = "%sx%s".formatted(predatorY, predatorX); // RxC, YxX
    var predatorRange = 50;
    var nextGridCellId = Predator.nextGridCellId(predatorId, createGridCells(xyTopLeft, color, rows, cols), predatorRange);
    assertEquals("%dx%d".formatted(predatorY + 1, predatorX + 1), nextGridCellId);
  }

  @Test
  // @Disabled
  void testOneColumnAboveVertical() {
    var xyTopLeft = 100;
    var color = "blue";
    var rows = 30;
    var cols = 1;
    var predatorX = xyTopLeft;
    var predatorY = xyTopLeft - 2;
    var predatorId = "%sx%s".formatted(predatorY, predatorX); // RxC, YxX
    var predatorRange = 50;
    var nextGridCellId = Predator.nextGridCellId(predatorId, createGridCells(xyTopLeft, color, rows, cols), predatorRange);
    assertEquals("%dx%d".formatted(predatorY + 1, predatorX), nextGridCellId);
  }

  @Test
  // @Disabled
  void testOneRowRightHorizontal() {
    var xyTopLeft = 100;
    var color = "blue";
    var rows = 1;
    var cols = 30;
    var predatorX = xyTopLeft - 2;
    var predatorY = xyTopLeft;
    var predatorId = "%sx%s".formatted(predatorY, predatorX); // RxC, YxX
    var predatorRange = 50;
    var nextGridCellId = Predator.nextGridCellId(predatorId, createGridCells(xyTopLeft, color, rows, cols), predatorRange);
    assertEquals("%dx%d".formatted(predatorY, predatorX + 1), nextGridCellId);
  }

  @Test
  // @Disabled
  void testNearbySmallClusterDistantLargeCluster() {
    var predatorX = 100;
    var predatorY = 100;
    var predatorId = "%sx%s".formatted(predatorY, predatorX); // RxC, YxX
    var predatorRange = 100;

    var largeClusterRows = 11;
    var largeClusterCols = 11;
    var largeClusterXTopLeft = predatorX - 50;
    var largeClusterYTopLeft = predatorY - Math.round(largeClusterRows / 2);
    var largeClusterColor = "blue";
    var largeClusterPreyCells = createGridCells(largeClusterXTopLeft, largeClusterYTopLeft, largeClusterColor, largeClusterRows, largeClusterCols);

    var smallClusterRows = 5;
    var smallClusterCols = 5;
    var smallClusterXTopLeft = predatorX + 10;
    var smallClusterYTopLeft = predatorY - Math.round(smallClusterCols / 2);
    var smallClusterColor = "red";
    var smallClusterPreyCells = createGridCells(smallClusterXTopLeft, smallClusterYTopLeft, smallClusterColor, smallClusterRows, smallClusterCols);

    var allPreyCells = Stream.concat(largeClusterPreyCells.stream(), smallClusterPreyCells.stream()).toList();

    var nextGridCellId = Predator.nextGridCellId(predatorId, allPreyCells, predatorRange);
    assertEquals("%dx%d".formatted(predatorY, predatorX + 1), nextGridCellId);
  }

  // Create a cluster of prey cells
  List<GridCellView.GridCellRow> createGridCells(int xyTopLeft, String color, int rows, int cols) {
    var gridCells = IntStream.range(xyTopLeft, rows + xyTopLeft)
        .mapToObj(rowY -> IntStream.range(xyTopLeft, cols + xyTopLeft)
            .mapToObj(colX -> {
              var id = rowY + "x" + colX;
              return new GridCellView.GridCellRow(
                  id,
                  color,
                  colX,
                  rowY,
                  Instant.now(),
                  Instant.now(),
                  Instant.now(),
                  Instant.now(),
                  Instant.now(),
                  0,
                  "",
                  "",
                  "");
            })
            .toList())
        .toList();
    return gridCells.stream().flatMap(List::stream).toList();
  }

  // Create a cluster of prey cells
  List<GridCellView.GridCellRow> createGridCells(int xTopLeft, int yTopLeft, String color, int rows, int cols) {
    var gridCells = IntStream.range(yTopLeft, rows + yTopLeft)
        .mapToObj(rowY -> IntStream.range(xTopLeft, cols + xTopLeft)
            .mapToObj(colX -> {
              var id = rowY + "x" + colX;
              return new GridCellView.GridCellRow(
                  id,
                  color,
                  colX,
                  rowY,
                  Instant.now(),
                  Instant.now(),
                  Instant.now(),
                  Instant.now(),
                  Instant.now(),
                  0,
                  "",
                  "",
                  "");
            })
            .toList())
        .toList();
    return gridCells.stream().flatMap(List::stream).toList();
  }
}
