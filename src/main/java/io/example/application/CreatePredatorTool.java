package io.example.application;

import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.client.ComponentClient;
import io.example.application.GridCellView.GridCellRow;
import io.example.domain.AgentStep;
import io.example.domain.GridCell;
import io.example.domain.Predator;
import io.example.domain.ViewPort;

public class CreatePredatorTool {
  static final Logger log = LoggerFactory.getLogger(CreatePredatorTool.class);
  final ComponentClient componentClient;
  final String region;

  public CreatePredatorTool(ComponentClient componentClient, String region) {
    this.componentClient = componentClient;
    this.region = region;
  }

  @FunctionTool(description = """
      Creates a predator entity at the specified location on the grid. Predators are intelligent entities
      that can move around the grid and interact with other cells. They scan the surrounding area within their range
      to find active cells and can move towards them. Predators are useful for creating dynamic, animated elements
      on the grid that can simulate hunting behavior or add movement to the visualization.
      """)
  public void createPredator(
      @Description("The user session id") String sessionId,
      @Description("The viewport information containing top-left, bottom-right, and mouse coordinates") ViewPort viewport,
      @Description("The row coordinate where the predator should be created") int row,
      @Description("The column coordinate where the predator should be created") int col,
      @Description("The sensing range of the predator in grid cells. The predator will scan this area around itself for active cells") int range) {

    log.info("Region: {}, Creating predator at row: {} and col: {} with range: {}", region, row, col, range);

    var x1 = col - range;
    var y1 = row - range;
    var x2 = col + range;
    var y2 = row + range;
    var pageTokenOffset = "";

    var activeGridCells = queryGridCellsInArea(x1, y1, x2, y2, pageTokenOffset);

    var cellId = String.format("%dx%d", row, col);
    var nextGridCellId = Predator.nextGridCellId(cellId, activeGridCells, range);

    var predatorId = Predator.parentId();
    var command = new GridCell.Command.CreatePredator(
        cellId,
        predatorId,
        GridCell.Status.predator,
        Instant.now(),
        Instant.now(),
        range,
        nextGridCellId,
        region);

    componentClient.forEventSourcedEntity(cellId)
        .method(GridCellEntity::createPredator)
        .invoke(command);

    {
      var message = """
          {
            "action": "create_predator",
            "row": %d,
            "col": %d,
            "range": %d
          }
          """.formatted(row, col, range);
      var stepCommand = AgentStep.Command.CreateStep.of(sessionId, message, viewport);

      componentClient.forEventSourcedEntity(stepCommand.id())
          .method(AgentStepEntity::createStep)
          .invoke(stepCommand);
    }
  }

  private List<GridCellRow> queryGridCellsInArea(int x1, int y1, int x2, int y2, String pageTokenOffset) {
    return Stream.generate(new Supplier<GridCellView.PagedGridCells>() {
      String currentPageToken = pageTokenOffset;
      boolean hasMore = true;

      @Override
      public GridCellView.PagedGridCells get() {
        if (!hasMore) {
          return null;
        }

        var pagedGridCells = componentClient.forView()
            .method(GridCellView::queryActiveGridCells)
            .invoke(new GridCellView.PagedGridCellsRequest(x1, y1, x2, y2, currentPageToken));

        currentPageToken = pagedGridCells.nextPageToken();
        hasMore = pagedGridCells.hasMore();

        return pagedGridCells;
      }
    })
        .takeWhile(pagedGridCells -> pagedGridCells != null)
        .flatMap(pagedGridCells -> pagedGridCells.gridCells().stream())
        .toList();
  }
}
