package io.example.application;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.client.ComponentClient;
import io.example.domain.GridCell;

public class DrawSingleCellTool {
  static final Logger log = LoggerFactory.getLogger(DrawSingleCellTool.class);
  final ComponentClient componentClient;
  final String region;

  public DrawSingleCellTool(ComponentClient componentClient, String region) {
    this.componentClient = componentClient;
    this.region = region;
  }

  @FunctionTool(description = """
      Draws a single cell on the grid with the specified status. This tool sets the color or status of
      one individual cell at the given coordinates. It's the most basic drawing operation and is useful for creating
      detailed patterns, making small adjustments, or placing individual elements on the grid.
      """)
  public void drawSingleCell(
      @Description("The row coordinate of the cell to draw") int row,
      @Description("The column coordinate of the cell to draw") int col,
      @Description("The status/color to apply to the cell. Valid values: 'red', 'green', 'blue', 'orange', 'predator', 'inactive'") String status) {

    log.info("Drawing single cell at row {} and column {} with status {}", row, col, status);

    var cellId = String.format("%dx%d", row, col);
    var cellStatus = GridCell.Status.valueOf(status.toLowerCase());
    var command = new GridCell.Command.UpdateCell(
        cellId,
        cellStatus,
        Instant.now(),
        Instant.now(),
        region);

    componentClient.forEventSourcedEntity(cellId)
        .method(GridCellEntity::updateStatus)
        .invoke(command);
  }
}
