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

public class DrawRectangleTool {
  static final Logger log = LoggerFactory.getLogger(DrawRectangleTool.class);
  final ComponentClient componentClient;
  final String region;

  public DrawRectangleTool(ComponentClient componentClient, String region) {
    this.componentClient = componentClient;
    this.region = region;
  }

  @FunctionTool(description = """
      Draws a rectangular area on the grid with cells of a specific color. This tool creates a filled rectangle
      from the top-left corner to the bottom-right corner, setting all cells within that area to the specified color.
      Useful for creating large shapes, backgrounds, or clearing areas of the grid.
      Returns the rectangle shape as a JSON formatted string.
      """)
  public String drawRectangle(
      @Description("The user session id") String sessionId,
      @Description("The viewport information containing top-left, bottom-right, and mouse coordinates") ViewPort viewport,
      @Description("The row coordinate of the top-left corner of the rectangle") int topLeftRow,
      @Description("The column coordinate of the top-left corner of the rectangle") int topLeftCol,
      @Description("The row coordinate of the bottom-right corner of the rectangle") int bottomRightRow,
      @Description("The column coordinate of the bottom-right corner of the rectangle") int bottomRightCol,
      @Description("The color to apply to the cell. Use hex #RRGGBB or #RRGGBBAA colors") String color) {

    log.info("Region: {}, Drawing rectangle at top left row: {} and col: {} to bottom right row: {} and col: {}, color: {}", region, topLeftRow, topLeftCol, bottomRightRow, bottomRightCol, color);

    var cellId = String.format("%dx%d", topLeftRow, topLeftCol);
    var shape = GridCell.Shape.ofRectangle(topLeftRow, topLeftCol, bottomRightRow, bottomRightCol, GridCell.Color.of(color));
    {
      var status = GridCell.Status.custom;
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
            "topLeftRow": %d,
            "topLeftCol": %d,
            "bottomRightRow": %d,
            "bottomRightCol": %d,
            "color": "%s"
          }
          """.formatted(getClass().getSimpleName(), topLeftRow, topLeftCol, bottomRightRow, bottomRightCol, color);
      var command = AgentStep.Command.CreateStep.of(sessionId, message, viewport);

      componentClient.forEventSourcedEntity(command.id())
          .method(AgentStepEntity::createStep)
          .invoke(command);
    }

    return JsonSupport.encodeToString(shape);
  }
}
