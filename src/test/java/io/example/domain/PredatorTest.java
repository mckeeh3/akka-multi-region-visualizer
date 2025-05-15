package io.example.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import io.example.application.GridCellView;

public class PredatorTest {

  @Test
  void testNextCellNorth() {
    var centerX = 10;
    var centerY = 5;
    var id = "%sx%s".formatted(centerY, centerX); // RxC, YxX
    var range = 50;
    var result = Predator.nextCell(id, createGridCells(5, 5), centerX, centerY, range);
    assertEquals("6x10", result);
  }

  @Test
  void testNextCellNorthEast() {
    var centerX = 15;
    var centerY = 5;
    var id = "%sx%s".formatted(centerY, centerX); // RxC, YxX
    var range = 50;
    var result = Predator.nextCell(id, createGridCells(5, 5), centerX, centerY, range);
    assertEquals("6x14", result);
  }

  @Test
  void testNextCellEast() {
    var centerX = 15;
    var centerY = 10;
    var id = "%sx%s".formatted(centerY, centerX); // RxC, YxX
    var range = 50;
    var result = Predator.nextCell(id, createGridCells(5, 5), centerX, centerY, range);
    assertEquals("10x14", result);
  }

  @Test
  void testNextCellSouthEast() {
    var centerX = 15;
    var centerY = 15;
    var id = "%sx%s".formatted(centerY, centerX); // RxC, YxX
    var range = 50;
    var result = Predator.nextCell(id, createGridCells(5, 5), centerX, centerY, range);
    assertEquals("14x14", result);
  }

  @Test
  void testNextCellSouth() {
    var centerX = 10;
    var centerY = 15;
    var id = "%sx%s".formatted(centerY, centerX); // RxC, YxX
    var range = 50;
    var result = Predator.nextCell(id, createGridCells(5, 5), centerX, centerY, range);
    assertEquals("14x10", result);
  }

  @Test
  void testNextCellSouthWest() {
    var centerX = 5;
    var centerY = 15;
    var id = "%sx%s".formatted(centerY, centerX); // RxC, YxX
    var range = 50;
    var result = Predator.nextCell(id, createGridCells(5, 5), centerX, centerY, range);
    assertEquals("14x6", result);
  }

  @Test
  void testNextCellWest() {
    var centerX = 5;
    var centerY = 10;
    var id = "%sx%s".formatted(centerY, centerX); // RxC, YxX
    var range = 50;
    var result = Predator.nextCell(id, createGridCells(5, 5), centerX, centerY, range);
    assertEquals("10x6", result);
  }

  @Test
  void testNextCellNorthWest() {
    var centerX = 5;
    var centerY = 5;
    var id = "%sx%s".formatted(centerY, centerX); // RxC, YxX
    var range = 50;
    var result = Predator.nextCell(id, createGridCells(5, 5), centerX, centerY, range);
    assertEquals("6x6", result);
  }

  @Test
  void testNextCellCenter() {
    var centerX = 10;
    var centerY = 10;
    var id = "%sx%s".formatted(centerY, centerX); // RxC, YxX
    var range = 50;
    var neighborIds = GridCell.State.neighborIds(id);
    var removeIds = neighborIds.subList(1, neighborIds.size());
    var preyCells = createGridCells(5, 5).stream()
        .filter(cell -> !removeIds.contains(cell.id()))
        .toList();
    var result = Predator.nextCell(id, preyCells, centerX, centerY, range);
    assertEquals(neighborIds.get(0), result);
  }

  // Create a group of prey (blue cells), 5x5 grid, top left at (8x8), center at (10x10), bottom right at (12x12)
  List<GridCellView.GridCellRow> createGridCells(int rows, int cols) {
    var xyTopLeft = 8;
    var gridCells = IntStream.range(xyTopLeft, rows + xyTopLeft)
        .mapToObj(row -> IntStream.range(xyTopLeft, cols + xyTopLeft)
            .mapToObj(col -> {
              var id = row + "x" + col;
              return new GridCellView.GridCellRow(
                  id,
                  "blue",
                  row,
                  col,
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
