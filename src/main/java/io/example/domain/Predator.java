package io.example.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.example.application.GridCellView;
import io.example.application.GridCellView.GridCellRow;

/**
 * The Predator class implements the hunting behavior for predator entities in the Akka Multi-Region Visualizer grid.
 * Predators move across the grid seeking out and consuming prey cells (active cells) according to a set of
 * sophisticated hunting algorithms that simulate natural predator-prey dynamics.
 *
 * <h2>Core Functionality</h2>
 * <ol>
 * <li><b>Target Selection</b>: Identifies the most attractive prey cells based on their intensity and proximity</li>
 * <li><b>Path Finding</b>: Determines the optimal next move toward selected prey using short and long-range strategies</li>
 * <li><b>Movement Logic</b>: Implements both deterministic and non-deterministic movement patterns to create
 * realistic hunting behavior</li>
 * <li><b>Range-Based Hunting</b>: Adapts hunting strategies based on the predator's detection range</li>
 * </ol>
 *
 * <h2>Hunting Strategies</h2>
 * <ul>
 * <li><b>Short Range</b>: When prey is nearby, predators move directly toward the highest-intensity prey,
 * with preference for immediate neighbors</li>
 * <li><b>Long Range</b>: When no prey is in short range, predators use a more sophisticated algorithm that
 * considers the collective influence of all prey cells in range</li>
 * <li><b>Random Selection</b>: To avoid deterministic behavior, predators may randomly select among equally
 * attractive prey cells</li>
 * </ul>
 *
 * <h2>Technical Details</h2>
 * <ul>
 * <li>Uses vector mathematics to determine movement direction toward prey</li>
 * <li>Implements distance calculations to prioritize prey based on proximity</li>
 * <li>Applies intensity weighting to prefer higher-value prey cells</li>
 * <li>Provides logging for debugging and visualization of predator decision-making</li>
 * </ul>
 *
 * <h2>Integration Points</h2>
 * <ul>
 * <li>Works with the GridCellView to access the current state of the grid</li>
 * <li>Interacts with the Point and DirectionVector utility classes for spatial calculations</li>
 * <li>Integrates with the grid visualization system to display predator movement</li>
 * </ul>
 *
 * This class is central to the emergent behavior observed in the grid visualization, creating dynamic
 * patterns as predators chase and consume prey cells across the multi-region environment.
 */
public class Predator {
  static final Logger log = LoggerFactory.getLogger(Predator.class);

  // Try to find the next grid cell nearby, progressively increasing the range
  static public String nextGridCellId(String predatorGridCellId, List<GridCellView.GridCellRow> allGridCells, int predatorRange) {
    var predatorGridCellXy = Point.fromId(predatorGridCellId);

    log.info("Hunting prey: predator: {}, predatorRange: {}, predatorX: {}, predatorY: {}",
        predatorGridCellId, predatorRange, predatorGridCellXy.x(), predatorGridCellXy.y());

    if (allGridCells.isEmpty()) {
      log.info("Next cell: (empty), predator: {}, No prey cells in predatorRange {}", predatorGridCellXy.id(), predatorRange);
      return "";
    }

    {
      var nextGridCellId = nextGridCellIdShortRange(predatorGridCellXy, allGridCells, predatorRange);
      if (!nextGridCellId.isEmpty()) {
        log.info("Next cell (short range): {}, predator: {}", nextGridCellId, predatorGridCellXy.id());
        return nextGridCellId;
      }
    }

    var nextGridCellId = nextGridCellIdLongRange(predatorGridCellXy, allGridCells, predatorRange);
    log.info("Next cell (long range): {}, predator: {}", nextGridCellId.isEmpty() ? "(empty)" : nextGridCellId, predatorGridCellXy.id());
    return nextGridCellId;
  }

  // ==================================================
  // Short range
  // ==================================================
  static public String nextGridCellIdShortRange(Point predatorGridCellXy, List<GridCellView.GridCellRow> allGridCells, int predatorRange) {
    var range = Math.min(predatorRange, 10);
    var gridCellsInCircle = getGridCellsInCircle(allGridCells, predatorGridCellXy.x(), predatorGridCellXy.y(), range);
    log.info("Found {} grid cells in the circle range {} (filtered from {} in rectangle)", gridCellsInCircle.size(), range, allGridCells.size());

    var preyCells = getPreyCells(gridCellsInCircle).stream()
        .map(cell -> new PreyGridCellDistance(cell.id(), cell.x(), cell.y(), cell.maxIntensity(),
            Math.sqrt(Math.pow(cell.x() - predatorGridCellXy.x(), 2) + Math.pow(cell.y() - predatorGridCellXy.y(), 2))))
        .filter(cell -> cell.maxIntensity() > 0) // Only prey cells have maxIntensity > 0
        .sorted(Comparator
            .comparing(PreyGridCellDistance::maxIntensity, Comparator.reverseOrder())
            .thenComparing(PreyGridCellDistance::distance))
        .toList();

    if (preyCells.isEmpty()) {
      return "";
    }

    // Select neighbors with the same maxIntensity as the nearest prey cell
    // Randomly select one of the neighbors to move more non-deterministically
    var neighbors = preyCells.stream()
        .filter(cell -> Point.from(cell).isNeighborOf(predatorGridCellXy))
        .filter(cell -> cell.maxIntensity() == preyCells.get(0).maxIntensity())
        .toList();
    if (!neighbors.isEmpty()) {
      return neighbors.stream()
          .skip((new Random()).nextInt(neighbors.size()))
          .findFirst()
          .get()
          .id();
    }

    var nearestPoint = preyCells.get(0);
    var directionVector = new DirectionVector(nearestPoint.x() - predatorGridCellXy.x(), nearestPoint.y() - predatorGridCellXy.y());
    log.info("Direction vector: {}", directionVector);

    var nextGridCellId = nextGridCellId(predatorGridCellXy, directionVector);
    log.info("Next cell: {}, predator: {}", nextGridCellId, predatorGridCellXy.id());

    return nextGridCellId;
  }

  // ==================================================
  // Long range
  // ==================================================
  static public String nextGridCellIdLongRange(Point predatorXy, List<GridCellView.GridCellRow> allGridCells, int predatorRange) {
    // Large sigma means more influence from distant cells
    var sigma = 20.0;

    var gridCellsInCircle = getGridCellsInCircle(allGridCells, predatorXy.x(), predatorXy.y(), predatorRange);
    log.info("Found {} grid cells in the circle range {} (filtered from {} in rectangle)", gridCellsInCircle.size(), predatorRange, allGridCells.size());

    var preyCells = getPreyCells(gridCellsInCircle);
    // preyCells.forEach(cell -> log.debug("Prey cell: {}", cell));
    log.info("Found {} prey cells in range {}", preyCells.size(), predatorRange);

    if (preyCells.isEmpty()) {
      log.info("Next cell: (empty), predator: {}, No prey cells in range {}", predatorXy.id(), predatorRange);
      return "";
    }

    var preyVectors = getPreyVectors(sigma, predatorXy, predatorRange, preyCells);
    preyVectors.forEach(vector -> log.debug("Vector: {}", vector));
    log.info("Computed Gaussian decay vectors (sigma: {}) for {} prey cells", sigma, preyVectors.size());

    if (preyVectors.isEmpty()) {
      log.info("Next cell: (empty), predator: {}, No prey vectors in predatorRange {}", predatorXy.id(), predatorRange);
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

    var nextGridCell = nextGridCellId(predatorXy, directionVector);

    return nextGridCell;
  }

  // Create vectors with intensity that decreases with distance using Gaussian decay
  static List<PreyVector> getPreyVectors(double sigma, Point predatorXy, int predatorRange, List<PreyGridCell> preyCells) {
    return preyCells.stream()
        .map(cell -> {
          // Calculate the vector from center to the cell
          var dx = cell.x() - predatorXy.x();
          var dy = cell.y() - predatorXy.y();

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

  static String nextGridCellId(Point predatorXy, DirectionVector directionVector) {
    // Get the normalized direction vector and its angle
    var normalizedVector = directionVector.normalized();
    var degrees = normalizedVector.degrees();

    // Convert degrees to one of 8 directions (N, NE, E, SE, S, SW, W, NW)
    // Each direction covers a 45-degree arc
    var direction = (int) Math.round(degrees / 45.0) % 8;
    if (direction < 0)
      direction += 8; // Handle negative angles

    // Calculate the next row and column based on the direction
    var nextRow = predatorXy.row();
    var nextCol = predatorXy.col();

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

  public static String parentId() {
    return "p-" + Integer.toString(Math.abs((int) System.currentTimeMillis() % 1000), 36);
  }

  public static String childId(String parentId) {
    return parentId + "-c-" + Integer.toString(Math.abs((int) System.currentTimeMillis() % 1000), 36);
  }
}

record PreyGridCellDistance(String id, int x, int y, int maxIntensity, double distance) {}

record PreyGridCell(String id, int x, int y, int maxIntensity) {}

record PreyVector(double x, double y, double distance, double intensity) {}

record Point(int x, int y) {
  public static Point fromId(String id) {
    var rc = id.split("x"); // RxC, YxX
    return new Point(Integer.parseInt(rc[1]), Integer.parseInt(rc[0]));
  }

  public static Point fromRowCol(int rowY, int colX) {
    return new Point(colX, rowY); // RxC, YxX
  }

  public static Point fromXy(int x, int y) {
    return new Point(x, y);
  }

  public static Point from(PreyGridCellDistance cell) {
    return new Point(cell.x(), cell.y());
  }

  public String id() {
    return "%dx%d".formatted(y, x);
  }

  public int row() {
    return y;
  }

  public int col() {
    return x;
  }

  public boolean isNeighborOf(Point other) {
    return !this.equals(other) && Math.abs(x - other.x) <= 1 && Math.abs(y - other.y) <= 1;
  }
}

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
