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

public class DrawSingleCellTool {
  static final Logger log = LoggerFactory.getLogger(DrawSingleCellTool.class);
  final ComponentClient componentClient;
  final String region;

  public DrawSingleCellTool(ComponentClient componentClient, String region) {
    this.componentClient = componentClient;
    this.region = region;
  }

  @FunctionTool(description = """
      Draws a single cell on the grid with the specified color. This tool sets the color of
      one individual cell at the given coordinates. It's the most basic drawing operation and is useful for creating
      detailed patterns, making small adjustments, or placing individual elements on the grid.
      Returns the single cell shape as a JSON formatted string.
      """)
  public String drawSingleCell(
      @Description("The user session id") String sessionId,
      @Description("The viewport information containing top-left, bottom-right, and mouse coordinates") ViewPort viewport,
      @Description("The row coordinate of the cell to draw") int row,
      @Description("The column coordinate of the cell to draw") int col,
      @Description("The color to apply to the cell. Use hex #RRGGBB or #RRGGBBAA colors") String color) {

    log.info("Region: {}, Drawing single cell at row: {} and col: {} with color: {}", region, row, col, color);

    var cellId = String.format("%dx%d", row, col);
    var status = GridCell.Status.custom;
    var shape = GridCell.Shape.ofSingleCell(GridCell.Color.of(color));
    {
      var command = new GridCell.Command.DrawShape(
          cellId,
          status,
          Instant.now(),
          Instant.now(),
          shape,
          region);

      componentClient.forEventSourcedEntity(cellId)
          .method(GridCellEntity::drawShape)
          .invoke(command);
    }

    {
      var message = """
          {
            "tool": "%s",
            "row": %d,
            "col": %d,
            "color": "%s"
          }
          """.formatted(getClass().getSimpleName(), row, col, color);
      var command = AgentStep.Command.CreateStep.of(sessionId, message, viewport);

      componentClient.forEventSourcedEntity(command.id())
          .method(AgentStepEntity::createStep)
          .invoke(command);
    }

    return JsonSupport.encodeToString(shape);
  }
}
