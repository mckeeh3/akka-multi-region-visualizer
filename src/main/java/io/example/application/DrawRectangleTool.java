package io.example.application;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.client.ComponentClient;
import io.example.domain.GridCell;

public class DrawRectangleTool {
  static final Logger log = LoggerFactory.getLogger(DrawRectangleTool.class);
  final ComponentClient componentClient;
  final String region;

  public DrawRectangleTool(ComponentClient componentClient, String region) {
    this.componentClient = componentClient;
    this.region = region;
  }

  @FunctionTool(description = """
      Draws a rectangular area on the grid with cells of a specific status. This tool creates a filled rectangle
      from the top-left corner to the bottom-right corner, setting all cells within that area to the specified status.
      Useful for creating large shapes, backgrounds, or clearing areas of the grid.
      """)
  public void drawRectangle(
      @Description("The row coordinate of the top-left corner of the rectangle") int topLeftRow,
      @Description("The column coordinate of the top-left corner of the rectangle") int topLeftCol,
      @Description("The row coordinate of the bottom-right corner of the rectangle") int bottomRightRow,
      @Description("The column coordinate of the bottom-right corner of the rectangle") int bottomRightCol,
      @Description("The status/color to apply to all cells in the rectangle. Valid values: 'red', 'green', 'blue', 'orange', 'predator', 'inactive'") String status) {

    log.info("Drawing rectangle at top left row {} and column {} to bottom right row {} and column {}", topLeftRow, topLeftCol, bottomRightRow, bottomRightCol, status);

    var cellId = String.format("%dx%d", topLeftRow, topLeftCol);
    var shape = GridCell.Shape.ofRectangle(topLeftCol, topLeftRow, bottomRightCol, bottomRightRow);
    var command = new GridCell.Command.CreateShape(
        cellId,
        GridCell.Status.valueOf(status.toLowerCase()),
        Instant.now(),
        Instant.now(),
        shape,
        region);

    componentClient.forEventSourcedEntity(cellId)
        .method(GridCellEntity::createShape)
        .invoke(command);
  }
}
