package io.example.application;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.client.ComponentClient;
import io.example.domain.AgentStep;
import io.example.domain.GridCell;

public class DrawCircleTool {
  static final Logger log = LoggerFactory.getLogger(DrawCircleTool.class);
  final ComponentClient componentClient;
  final String region;

  public DrawCircleTool(ComponentClient componentClient, String region) {
    this.componentClient = componentClient;
    this.region = region;
  }

  @FunctionTool(description = """
      Draws a circular pattern of cells on the grid with the specified status. This tool creates a filled circle
      centered at the given coordinates with the specified radius. The circle is drawn using a simple distance-based algorithm
      and is useful for creating circular shapes, targets, or decorative elements on the grid.
      """)
  public void drawCircle(
      @Description("The user session id") String sessionId,
      @Description("The viewport") AgentStep.ViewPort viewport,
      @Description("The row coordinate of the center of the circle") int row,
      @Description("The column coordinate of the center of the circle") int col,
      @Description("The status/color to apply to all cells in the circle. Valid values: 'red', 'green', 'blue', 'orange'") String status,
      @Description("The radius of the circle in grid cells. Maximum effective radius is 30 cells for performance reasons") int radius) {

    log.info("Region: {}, Drawing circle at row: {} and col: {} with status: {} and radius: {}", region, row, col, status, radius);

    var cellId = String.format("%dx%d", row, col);
    var shape = GridCell.Shape.ofCircle(row, col, radius);
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

    {
      var message = """
          {
            "action": "draw_circle",
            "parameters": {
              "userSessionId": "%s",
              "viewport": {
                "topLeft": {"row": %d, "col": %d},
                "bottomRight": {"row": %d, "col": %d},
                "mouse": {"row": %d, "col": %d}
              },
              "row": %d,
              "col": %d,
              "status": "%s",
              "radius": %d
            }
          }
          """.formatted(sessionId,
          viewport.topLeft().row(), viewport.topLeft().col(),
          viewport.bottomRight().row(), viewport.bottomRight().col(),
          viewport.mouse().row(), viewport.mouse().col(),
          row, col, status, radius);
      var stepCommand = AgentStep.Command.CreateStep.of(sessionId, message, viewport);

      componentClient.forEventSourcedEntity(stepCommand.id())
          .method(AgentStepEntity::createStep)
          .invoke(stepCommand);
    }
  }
}
