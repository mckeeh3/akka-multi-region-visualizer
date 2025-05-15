package io.example.domain;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.example.application.GridCellView;
import io.example.application.GridCellView.GridCellRow;
import io.grpc.netty.shaded.io.netty.util.internal.ThreadLocalRandom;

public class Predator {
  static final Logger log = LoggerFactory.getLogger(Predator.class);

  static public String nextCell(String predatorId, List<GridCellView.GridCellRow> allGridCells, int centerX, int centerY, int range) {
    if (allGridCells.isEmpty()) {
      log.info("Next cell: (empty), predator: {}, No prey cells in range", predatorId);
      return "";
    }

    var gridCellsInCircle = getGridCellsInCircle(allGridCells, centerX, centerY, range);
    log.info("Found {} grid cells in the circle area (filtered from {} in rectangle)",
        gridCellsInCircle.size(), allGridCells.size());

    var preyCells = getScentCells(gridCellsInCircle);
    log.info("Found {} prey cells in range", preyCells.size());
    // preyCells.forEach(cell -> log.debug("Prey cell: {}", cell));

    if (preyCells.isEmpty()) {
      log.info("Next cell: (empty), predator: {}, No prey cells in range", predatorId);
      return "";
    }

    var vectors = getVectors(centerX, centerY, range, preyCells);
    log.info("Created {} vectors with Gaussian decay", vectors.size());
    // vectors.forEach(vector -> log.debug("Vector: {}", vector));

    // Calculate the sum of all vectors
    var sumX = vectors.stream().mapToDouble(ScentVector::x).sum();
    var sumY = vectors.stream().mapToDouble(ScentVector::y).sum();
    var sumIntensity = vectors.stream().mapToDouble(ScentVector::intensity).sum();

    // Create a new vector with the sum of all vectors
    var directionVector = new DirectionVector(sumX, sumY);
    var totalIntensity = sumIntensity;

    log.info("Total intensity: {}", totalIntensity);
    log.info("Direction vector: {}", directionVector);
    log.info("Direction vector normalized: {}", directionVector.normalized());
    log.info("Direction vector radians: {}", directionVector.normalized().radians());
    log.info("Direction vector degrees: {}", directionVector.normalized().degrees());

    // Extract row and column from the current cell ID (format: RxC)
    var parts = predatorId.split("x"); // RxC / YxX
    var currentRow = Integer.parseInt(parts[0]);
    var currentCol = Integer.parseInt(parts[1]);

    // Get the normalized direction vector and its angle
    var normalizedVector = directionVector.normalized();
    var degrees = normalizedVector.degrees();

    // Convert degrees to one of 8 directions (N, NE, E, SE, S, SW, W, NW)
    // Each direction covers a 45-degree arc
    var direction = (int) Math.round(degrees / 45.0) % 8;
    if (direction < 0)
      direction += 8; // Handle negative angles

    // Calculate the next row and column based on the direction
    var nextRow = currentRow;
    var nextCol = currentCol;

    switch (direction) {
      case 0: // East (0 degrees)
        nextCol++;
        break;
      case 1: // Northeast (45 degrees)
        nextRow++; // Inverted: in grid coordinates, positive y is down
        nextCol++;
        break;
      case 2: // North (90 degrees)
        nextRow++; // Inverted: in grid coordinates, positive y is down
        break;
      case 3: // Northwest (135 degrees)
        nextRow++; // Inverted: in grid coordinates, positive y is down
        nextCol--;
        break;
      case 4: // West (180 degrees)
        nextCol--;
        break;
      case 5: // Southwest (225 degrees)
        nextRow--; // Inverted: in grid coordinates, positive y is down
        nextCol--;
        break;
      case 6: // South (270 degrees)
        nextRow--; // Inverted: in grid coordinates, positive y is down
        break;
      case 7: // Southeast (315 degrees)
        nextRow--; // Inverted: in grid coordinates, positive y is down
        nextCol++;
        break;
    }

    // Format the next grid cell ID as "RxC"
    var nextGridCell = nextRow + "x" + nextCol;

    // Search for any scent cells in the neighbors of the current cell
    var neighborIds = GridCell.State.neighborIds(predatorId);
    var neighborPreyCells = preyCells.stream()
        .filter(cell -> neighborIds.contains(cell.id()))
        .toList();
    log.info("Found {} prey cells in the neighbors of predator cell {}", neighborPreyCells.size(), predatorId);
    // neighborPreyCells.forEach(cell -> log.debug("Neighbor prey cell: {}", cell));

    if (neighborPreyCells.isEmpty()) {
      log.info("Next cell: {}, predator cell: {}", nextGridCell, predatorId);
      return nextGridCell;
    }

    // There are neighbor prey cells, check if the next cell is one of them
    var isNextGridCellPreyNeighbor = neighborPreyCells.stream()
        .map(cell -> cell.id())
        .anyMatch(cellId -> cellId.equals(nextGridCell));
    log.info("Next grid cell {} {} a neighbor prey cell", nextGridCell, isNextGridCellPreyNeighbor ? "is" : "is not");
    if (isNextGridCellPreyNeighbor) {
      log.info("Next cell: {}, predator cell: {}", nextGridCell, predatorId);
      return nextGridCell;
    }

    // Next cell is not a neighbor prey cell, continue to the next cell
    var randomNeighborPreyCell = neighborPreyCells.get(ThreadLocalRandom.current().nextInt(neighborPreyCells.size()));
    log.info("Next cell: {}, predator cell: {}", randomNeighborPreyCell.id(), predatorId);
    return randomNeighborPreyCell.id();
  }

  // Create vectors with intensity that decreases with distance using Gaussian decay
  static List<ScentVector> getVectors(int centerX, int centerY, int range, List<ScentCell> scentCells) {
    return scentCells.stream()
        .map(cell -> {
          // Calculate the vector from center to the cell
          var dx = cell.x() - centerX;
          var dy = cell.y() - centerY;

          // Calculate distance from center
          var distance = Math.sqrt(dx * dx + dy * dy);

          // Calculate unit vector components (normalized direction)
          var unitX = (distance > 0) ? dx / distance : 0;
          var unitY = (distance > 0) ? dy / distance : 0;

          // Apply Gaussian decay to the intensity based on distance
          // Using formula: intensity = maxIntensity * exp(-distance²/(2*sigma²))
          // Where sigma controls the width of the Gaussian
          var sigma = range / 3.0; // Adjust this parameter as needed
          var gaussianFactor = Math.exp(-(distance * distance) / (2 * sigma * sigma));
          var intensity = cell.maxIntensity() * gaussianFactor;

          // Create a vector with the unit direction and then scale by intensity
          // This gives a vector whose direction is normalized and magnitude equals the intensity
          return new ScentVector(unitX * intensity, unitY * intensity, intensity);
        })
        .toList();
  }

  static List<ScentCell> getScentCells(List<GridCellRow> gridCellsInCircle) {
    return gridCellsInCircle.stream()
        .map(cell -> {
          int maxIntensity = switch (cell.status().toLowerCase()) {
            case "red" -> 1;
            case "orange" -> 2;
            case "green" -> 3;
            case "blue" -> 4;
            default -> 0;
          };
          return new ScentCell(cell.id(), cell.x(), cell.y(), maxIntensity);
        })
        .filter(cell -> cell.maxIntensity() > 0)
        .toList();
  }

  // Filter grid cells that are inside the circle
  static List<GridCellRow> getGridCellsInCircle(List<GridCellView.GridCellRow> allGridCells, int centerX, int centerY, int range) {
    return allGridCells.stream()
        .filter(cell -> {
          // Calculate the distance from the cell to the center of the circle
          var distance = Math.sqrt(
              Math.pow(cell.x() - centerX, 2) +
                  Math.pow(cell.y() - centerY, 2));

          // Keep only cells that are inside the circle (distance <= range)
          return distance <= range;
        })
        .toList();
  }
}

record ScentCell(String id, int x, int y, int maxIntensity) {}

record ScentVector(double x, double y, double intensity) {}

record DirectionVector(double x, double y) {
  DirectionVector normalized() {
    double length = Math.sqrt(x * x + y * y);
    return length > 0 ? new DirectionVector(x / length, y / length) : this;
  }

  double radians() {
    return Math.atan2(y, x);
  }

  double degrees() {
    return Math.toDegrees(radians());
  }
}
