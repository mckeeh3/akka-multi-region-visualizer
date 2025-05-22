package io.example.api;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.javasdk.client.ComponentClient;
import io.example.application.GridCellEntity;
import io.example.domain.GridCell;

class FillRectangle {
  private static final Logger log = LoggerFactory.getLogger(FillRectangle.class);
  private final ComponentClient componentClient;
  private final Executor virtualThreadExecutor;

  FillRectangle(ComponentClient componentClient) {
    this.componentClient = componentClient;
    virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
  }

  static void fillRectangle(Request request, ComponentClient componentClient) {
    var width = (int) Math.abs(request.x2() - request.x1()) + 1;
    var height = (int) Math.abs(request.y2() - request.y1()) + 1;
    var grid = new Grid(request.x1(), request.y1(), width, height);
    var fillRectangle = new FillRectangle(componentClient);

    fillRectangle.processRectangle(grid, request);
  }

  // Recursively processes a rectangle, either subdividing or executing the task
  void processRectangle(Grid grid, Request request) {
    if (grid.isSingleCell()) {
      virtualThreadExecutor.execute(() -> {
        final int maxRetries = 12;
        final long initialDelayMs = 100;
        final long maxDelayMs = 10000; // 10 seconds max delay

        int retryCount = 0;
        boolean success = false;
        Exception lastException = null;

        var id = "%dx%d".formatted(grid.y, grid.x); // RxC / YxX
        var command = new GridCell.Command.UpdateStatus(
            id,
            request.status(),
            request.clientAt(),
            request.endpointAt(),
            request.region());

        while (!success && retryCount < maxRetries) {
          try {
            if (retryCount > 0) {
              // Calculate exponential backoff delay with jitter
              long delayMs = Math.min(
                  initialDelayMs * (long) Math.pow(2, retryCount - 1) + (long) (Math.random() * 100),
                  maxDelayMs);
              log.info("Retrying cell {} after {}ms (attempt {})", id, delayMs, retryCount + 1);
              Thread.sleep(delayMs);
            }

            componentClient.forEventSourcedEntity(id)
                .method(GridCellEntity::updateStatus)
                .invoke(command);

            success = true;
            if (retryCount > 0) {
              log.info("Successfully processed cell {} after {} retries", id, retryCount);
            }
          } catch (Exception e) {
            lastException = e;
            retryCount++;
            log.warn("Error processing cell {} (attempt {}/{}): {}", id, retryCount, maxRetries, e.getMessage());
          }
        }

        if (!success && lastException != null) {
          log.error("Failed to process cell {} after {} attempts: {}", id, maxRetries, lastException.getMessage());
        } else if (!success) {
          log.error("Failed to process cell {} after {} attempts", id, maxRetries);
        }
      });
    } else {
      var subAreas = subdivideGrid(grid);

      subAreas.forEach(subArea -> {
        virtualThreadExecutor.execute(() -> processRectangle(subArea, request));
      });
    }
  }

  List<Grid> subdivideGrid(Grid grid) {
    var halfWidth = grid.width / 2;
    var halfHeight = grid.height / 2;

    if (halfWidth == 0 && halfHeight == 0) {
      return List.of(grid);
    }
    if (halfWidth == 0) {
      // Split only vertically (width=1, height>1)
      return Stream.of(
          Optional.of(new Grid(grid.x, grid.y, grid.width, halfHeight)),
          grid.height - halfHeight > 0
              ? Optional.of(new Grid(grid.x, grid.y + halfHeight, grid.width, grid.height - halfHeight))
              : Optional.<Grid>empty())
          .flatMap(s -> s.stream())
          .toList();
    }
    if (halfHeight == 0) {
      // Split only horizontally (height=1, width>1)
      return Stream.of(
          Optional.of(new Grid(grid.x, grid.y, halfWidth, grid.height)),
          grid.width - halfWidth > 0
              ? Optional.of(new Grid(grid.x + halfWidth, grid.y, grid.width - halfWidth, grid.height))
              : Optional.<Grid>empty())
          .flatMap(s -> s.stream())
          .toList();
    }
    // Split both ways (quad split) - creates 4 sub-areas
    return Stream.of(
        // Top-left
        Optional.of(new Grid(grid.x, grid.y, halfWidth, halfHeight)),
        // Top-right
        grid.width - halfWidth > 0
            ? Optional.of(new Grid(grid.x + halfWidth, grid.y, grid.width - halfWidth, halfHeight))
            : Optional.<Grid>empty(),
        // Bottom-left
        grid.height - halfHeight > 0
            ? Optional.of(new Grid(grid.x, grid.y + halfHeight, halfWidth, grid.height - halfHeight))
            : Optional.<Grid>empty(),
        // Bottom-right
        grid.width - halfWidth > 0 && grid.height - halfHeight > 0
            ? Optional.of(new Grid(grid.x + halfWidth, grid.y + halfHeight,
                grid.width - halfWidth, grid.height - halfHeight))
            : Optional.<Grid>empty())
        .flatMap(s -> s.stream())
        .toList();
  }

  record Request(int x1, int y1, int x2, int y2, String region, Instant clientAt, Instant endpointAt, GridCell.Status status) {}

  record Grid(int x, int y, int width, int height) {
    boolean isSingleCell() {
      return width == 1 && height == 1;
    }
  }
}
