package io.example.domain;

import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.example.application.GridCellView;
import io.example.application.GridCellView.GridCellRow;

public class Predator {
  static final Logger log = LoggerFactory.getLogger(Predator.class);

  // Try to find the next grid cell nearby, progressively increasing the range
  static public String nextGridCellId(String predatorId, List<GridCellView.GridCellRow> allGridCells, int predatorRange) {
    var xy = predatorId.split("x");
    var predatorX = Integer.parseInt(xy[1]);
    var predatorY = Integer.parseInt(xy[0]);

    log.info("Hunting prey: predator: {}, predatorRange: {}, predatorX: {}, predatorY: {}", predatorId, predatorRange, predatorX, predatorY);

    if (allGridCells.isEmpty()) {
      log.info("Next cell: (empty), predator: {}, No prey cells in predatorRange {}", predatorId, predatorRange);
      return "";
    }

    {
      var nextGridCellId = nextGridCellIdShortRange(predatorX, predatorY, allGridCells, predatorRange);
      if (!nextGridCellId.isEmpty()) {
        log.info("Next cell (short range): {}, predator: {}", nextGridCellId, predatorId);
        return nextGridCellId;
      }
    }

    var nextGridCellId = nextGridCellIdLongRange(predatorX, predatorY, allGridCells, predatorRange);
    log.info("Next cell (long range): {}, predator: {}", nextGridCellId.isEmpty() ? "(empty)" : nextGridCellId, predatorId);
    return nextGridCellId;
  }

  // ==================================================
  // Short range
  // ==================================================
  static public String nextGridCellIdShortRange(int predatorX, int predatorY, List<GridCellView.GridCellRow> allGridCells, int predatorRange) {
    var range = Math.min(predatorRange, 10);
    var gridCellsInCircle = getGridCellsInCircle(allGridCells, predatorX, predatorY, range);
    log.info("Found {} grid cells in the circle radius {} (filtered from {} in rectangle)", gridCellsInCircle.size(), range, allGridCells.size());

    var preyCells = getPreyCells(gridCellsInCircle).stream()
        .map(cell -> new PreyGridCellDistance(cell.id(), cell.x(), cell.y(), cell.maxIntensity(),
            Math.sqrt(Math.pow(cell.x() - predatorX, 2) + Math.pow(cell.y() - predatorY, 2))))
        .filter(cell -> cell.maxIntensity() > 0) // Only prey cells have maxIntensity > 0
        .sorted(Comparator
            .comparing(PreyGridCellDistance::maxIntensity, Comparator.reverseOrder())
            .thenComparing(PreyGridCellDistance::distance))
        .toList();

    var nearestPreyCell = preyCells.stream().findFirst().map(PreyGridCellDistance::id).orElse("");
    if (nearestPreyCell.isEmpty()) {
      return "";
    }
    var nearestXy = nearestPreyCell.split("x");
    var nearestX = Integer.parseInt(nearestXy[1]);
    var nearestY = Integer.parseInt(nearestXy[0]);
    var directionVector = new DirectionVector(nearestX - predatorX, nearestY - predatorY);
    log.info("Direction vector: {}", directionVector);

    var nextGridCellId = nextGridCellId(predatorX, predatorY, directionVector);
    log.info("Next cell: {}, predator: {}x{}", nextGridCellId, predatorY, predatorX);

    return nextGridCellId;
  }

  // ==================================================
  // Long range
  // ==================================================
  static public String nextGridCellIdLongRange(int predatorX, int predatorY, List<GridCellView.GridCellRow> allGridCells, int predatorRange) {
    // Large sigma means more influence from distant cells
    var sigma = 20.0;

    var gridCellsInCircle = getGridCellsInCircle(allGridCells, predatorX, predatorY, predatorRange);
    log.info("Found {} grid cells in the circle radius {} (filtered from {} in rectangle)", gridCellsInCircle.size(), predatorRange, allGridCells.size());

    var preyCells = getPreyCells(gridCellsInCircle);
    // preyCells.forEach(cell -> log.debug("Prey cell: {}", cell));
    log.info("Found {} prey cells in radius {}", preyCells.size(), predatorRange);

    if (preyCells.isEmpty()) {
      log.info("Next cell: (empty), predator: {}x{}, No prey cells in radius {}", predatorY, predatorX, predatorRange);
      return "";
    }

    var preyVectors = getPreyVectors(sigma, predatorX, predatorY, predatorRange, preyCells);
    preyVectors.forEach(vector -> log.debug("Vector: {}", vector));
    log.info("Computed Gaussian decay vectors (sigma: {}) for {} prey cells", sigma, preyVectors.size());

    if (preyVectors.isEmpty()) {
      log.info("Next cell: (empty), predator: {}x{}, No prey vectors in predatorRange {}", predatorY, predatorX, predatorRange);
      return "";
    }

    // Calculate the sum of all vectors
    var sumX = preyVectors.stream().mapToDouble(PreyVector::x).sum();
    var sumY = preyVectors.stream().mapToDouble(PreyVector::y).sum();
    var directionVector = new DirectionVector(sumX, sumY);
    log.info("Direction vector: {}", directionVector);

    var totalIntensity = preyVectors.stream().mapToDouble(PreyVector::intensity).sum();
    log.info("Total intensity: {}", totalIntensity);

    log.info("Direction vector normalized: {}", directionVector.normalized());
    log.info("Direction vector radians: {}", directionVector.normalized().radians());
    log.info("Direction vector degrees: {}", directionVector.normalized().degrees());

    var nextGridCell = nextGridCellId(predatorX, predatorY, directionVector);

    return nextGridCell;
  }

  // Create vectors with intensity that decreases with distance using Gaussian decay
  static List<PreyVector> getPreyVectors(double sigma, int predatorX, int predatorY, int predatorRange, List<PreyGridCell> preyCells) {
    return preyCells.stream()
        .map(cell -> {
          // Calculate the vector from center to the cell
          var dx = cell.x() - predatorX;
          var dy = cell.y() - predatorY;

          // Calculate distance from center
          var distance = Math.sqrt(dx * dx + dy * dy);

          // Calculate unit vector components (normalized direction)
          var unitX = (distance > 0) ? dx / distance : 0;
          var unitY = (distance > 0) ? dy / distance : 0;

          // Apply Gaussian decay to the intensity based on distance
          // Using formula: intensity = maxIntensity * exp(-distance²/(2*sigma²))
          // Where sigma controls the width of the Gaussian
          var gaussianFactor = Math.exp(-(distance * distance) / (2 * sigma * sigma));
          var intensity = cell.maxIntensity() * gaussianFactor;

          // Create a vector with the unit direction and then scale by intensity
          // This gives a vector whose direction is normalized and magnitude adjusted by the intensity
          return new PreyVector(unitX * intensity, unitY * intensity, distance, intensity);
        })
        .filter(vector -> vector.intensity() > 0.000001) // Filter out vectors with very low intensity
        .toList();
  }

  static List<PreyGridCell> getPreyCells(List<GridCellRow> gridCellsInCircle) {
    return gridCellsInCircle.stream()
        .map(cell -> {
          int maxIntensity = switch (cell.status().toLowerCase()) {
            case "red" -> 1;
            case "orange" -> 2;
            case "green" -> 3;
            case "blue" -> 4;
            default -> 0;
          };
          return new PreyGridCell(cell.id(), cell.x(), cell.y(), maxIntensity);
        })
        .filter(cell -> cell.maxIntensity() > 0)
        .toList();
  }

  // Filter grid cells that are inside the circle
  static List<GridCellRow> getGridCellsInCircle(List<GridCellView.GridCellRow> allGridCells, int predatorX, int predatorY, int predatorRange) {
    return allGridCells.stream()
        .filter(cell -> {
          // Calculate the distance from the cell to the center of the circle
          var distance = Math.sqrt(
              Math.pow(cell.x() - predatorX, 2) +
                  Math.pow(cell.y() - predatorY, 2));

          // Keep only cells that are inside the circle (distance <= predatorRange)
          return distance <= predatorRange;
        })
        .toList();
  }

  static String nextGridCellId(int predatorX, int predatorY, DirectionVector directionVector) {
    // Extract row and column from the current cell ID (format: RxC)
    var currentRow = predatorY;
    var currentCol = predatorX;

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

    return nextGridCell;
  }
}

record PreyGridCellDistance(String id, int x, int y, int maxIntensity, double distance) {}

record PreyGridCell(String id, int x, int y, int maxIntensity) {}

record PreyVector(double x, double y, double distance, double intensity) {}

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
