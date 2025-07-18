package io.example.application;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.javasdk.JsonSupport;
import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.client.ComponentClient;
import io.example.domain.AgentStep;
import io.example.domain.GridCell;
import io.example.domain.ViewPort;

public class DrawCircleTool {
  static final Logger log = LoggerFactory.getLogger(DrawCircleTool.class);
  final ComponentClient componentClient;
  final String region;

  public DrawCircleTool(ComponentClient componentClient, String region) {
    this.componentClient = componentClient;
    this.region = region;
  }

  @FunctionTool(description = """
      Draws a circular pattern of cells on the grid with the specified color. This tool creates a filled circle
      centered at the given coordinates with the specified radius. The circle is drawn using a simple distance-based algorithm
      and is useful for creating circular shapes, targets, or decorative elements on the grid.
      Returns the circle shape as a JSON formatted string.
      """)
  public String drawCircle(
      @Description("The user session id") String sessionId,
      @Description("The viewport information containing top-left, bottom-right, and mouse coordinates") ViewPort viewport,
      @Description("The row coordinate of the center of the circle") int row,
      @Description("The column coordinate of the center of the circle") int col,
      @Description("The radius of the circle in grid cells. Maximum effective radius is 30 cells for performance reasons") int radius,
      @Description("The color to apply to the cell. Use hex #RRGGBB or #RRGGBBAA colors") String color) {

    log.info("Region: {}, Drawing circle at row: {} and col: {} with radius: {} and color: {}", region, row, col, radius, color);

    var cellId = String.format("%dx%d", row, col);
    var status = GridCell.Status.custom;
    var shape = GridCell.Shape.ofCircle(row, col, radius);
    var command = new GridCell.Command.DrawShape(
        cellId,
        status,
        GridCell.Color.of(color),
        Instant.now(),
        Instant.now(),
        shape,
        region);

    componentClient.forEventSourcedEntity(cellId)
        .method(GridCellEntity::drawShape)
        .invoke(command);

    {
      var message = """
          {
            "action": "draw_circle",
            "row": %d,
            "col": %d,
            "radius": %d,
            "color": "%s"
          }
          """.formatted(row, col, radius, color);
      var stepCommand = AgentStep.Command.CreateStep.of(sessionId, message, viewport);

      componentClient.forEventSourcedEntity(stepCommand.id())
          .method(AgentStepEntity::createStep)
          .invoke(stepCommand);
    }

    return JsonSupport.encodeToString(shape);
  }
}
