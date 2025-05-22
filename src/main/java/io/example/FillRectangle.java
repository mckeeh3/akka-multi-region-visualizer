package io.example;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.List;
import java.util.ArrayList;

class FillRectangle {

  record Grid(int x, int y, int width, int height) {
    boolean isSingleCell() {
      return width == 1 && height == 1;
    }
  }

  private final Executor virtualThreadExecutor;

  FillRectangle() {
    this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
  }

  /**
   * Starts the recursive grid subdivision process
   *
   * @param initialRect The starting rectangle
   * @param task        The task string to execute on single cells
   */
  void subdivideGrid(Grid initialRect, String task) {
    processRectangle(initialRect, task);
  }

  // Recursively processes a rectangle, either subdividing or executing the task
  void processRectangle(Grid rect, String task) {
    if (rect.isSingleCell()) {
      virtualThreadExecutor.execute(() -> {
        System.out.println("Processing cell at (" + rect.x + ", " + rect.y +
            ") with task: '" + task + "' on thread: " +
            Thread.currentThread().getName());
        try {
          Thread.sleep(50); // Simulate work
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        System.out.println("Completed task for cell (" + rect.x + ", " + rect.y + ")");
      });
    } else {
      var subAreas = subdivideGrid(rect);

      System.out.println("Subdividing " + rect + " into " + subAreas.size() + " sub-areas");
      subAreas.forEach(subArea -> {
        virtualThreadExecutor.execute(() -> processRectangle(subArea, task));
      });
    }
  }

  // Subdivides a rectangle into 1, 2, or 4 sub-areas using integer division
  List<Grid> subdivideGrid(Grid rect) {
    var subAreas = new ArrayList<Grid>();

    var halfWidth = rect.width / 2;
    var halfHeight = rect.height / 2;

    if (halfWidth == 0 && halfHeight == 0) {
      subAreas.add(rect);
    } else if (halfWidth == 0) {
      // Split only vertically (width=1, height>1)
      subAreas.add(new Grid(rect.x, rect.y, rect.width, halfHeight));
      if (rect.height - halfHeight > 0) {
        subAreas.add(new Grid(rect.x, rect.y + halfHeight, rect.width, rect.height - halfHeight));
      }
    } else if (halfHeight == 0) {
      // Split only horizontally (height=1, width>1)
      subAreas.add(new Grid(rect.x, rect.y, halfWidth, rect.height));
      if (rect.width - halfWidth > 0) {
        subAreas.add(new Grid(rect.x + halfWidth, rect.y, rect.width - halfWidth, rect.height));
      }
    } else {
      // Split both ways (quad split) - creates 4 sub-areas
      // Top-left
      subAreas.add(new Grid(rect.x, rect.y, halfWidth, halfHeight));

      // Top-right
      if (rect.width - halfWidth > 0) {
        subAreas.add(new Grid(rect.x + halfWidth, rect.y, rect.width - halfWidth, halfHeight));
      }

      // Bottom-left
      if (rect.height - halfHeight > 0) {
        subAreas.add(new Grid(rect.x, rect.y + halfHeight, halfWidth, rect.height - halfHeight));
      }

      // Bottom-right
      if (rect.width - halfWidth > 0 && rect.height - halfHeight > 0) {
        subAreas.add(new Grid(rect.x + halfWidth, rect.y + halfHeight,
            rect.width - halfWidth, rect.height - halfHeight));
      }
    }

    return subAreas;
  }

  /**
   * Shuts down the executor
   */
  void shutdown() {
    if (virtualThreadExecutor instanceof AutoCloseable) {
      try {
        ((AutoCloseable) virtualThreadExecutor).close();
      } catch (Exception e) {
        System.err.println("Error shutting down executor: " + e.getMessage());
      }
    }
  }

  // Example usage and demonstration
  public static void main(String[] args) {
    var subdivider = new FillRectangle();

    // Test with your example: w=3, h=2
    var testRect = new Grid(0, 0, 3, 2);
    var task = "ProcessGridCell";

    System.out.println("Starting grid subdivision for " + testRect);
    System.out.println("Expected sub-areas: w=1,h=1 + w=2,h=1 + w=1,h=1 + w=2,h=1 = 4 sub-areas");

    subdivider.subdivideGrid(testRect, task);

    // Give time for virtual threads to complete before shutdown
    try {
      Thread.sleep(2000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    System.out.println("\n--- Testing with larger grid ---");
    var largerRect = new Grid(0, 0, 8, 5);
    subdivider.subdivideGrid(largerRect, "ProcessLargeGrid");

    // Give time for processing
    try {
      Thread.sleep(3000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    // Clean shutdown
    subdivider.shutdown();
  }
}
