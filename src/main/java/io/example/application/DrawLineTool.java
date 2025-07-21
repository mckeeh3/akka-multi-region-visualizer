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

public class DrawLineTool {
  static final Logger log = LoggerFactory.getLogger(DrawLineTool.class);
  final ComponentClient componentClient;
  final String region;

  public DrawLineTool(ComponentClient componentClient, String region) {
    this.componentClient = componentClient;
    this.region = region;
  }

  @FunctionTool(description = """
      Draws a line on the grid with cells of a specific color. This tool creates a line starting from the given coordinates,
      extending at the specified angle for the given length with the specified width. The line is drawn as a rectangular shape
      oriented along the line direction. Useful for creating straight lines, arrows, or connecting elements on the grid.
      Returns the line shape as a JSON formatted string.
      """)
  public String drawLine(
      @Description("The user session id") String sessionId,
      @Description("The viewport information containing top-left, bottom-right, and mouse coordinates") ViewPort viewport,
      @Description("The row coordinate of the start point of the line") int startRow,
      @Description("The column coordinate of the start point of the line") int startCol,
      @Description("The angle in degrees (0° = right, 90° = down, 180° = left, 270° = up)") double angleDegrees,
      @Description("The length of the line in grid cells") int length,
      @Description("The width of the line in grid cells") int width,
      @Description("The color to apply to the cell. Use hex #RRGGBB or #RRGGBBAA colors") String color) {

    log.info("Region: {}, Drawing line at start row: {} and col: {} with angle: {}°, length: {}, width: {} and color: {}", region, startRow, startCol, angleDegrees, length, width, color);

    var cellId = String.format("%dx%d", startRow, startCol);
    var status = GridCell.Status.custom;
    var shape = GridCell.Shape.ofLine(startRow, startCol, angleDegrees, length, width, GridCell.Color.of(color));
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

    {
      var message = """
          {
            "tool": "%s",
            "startRow": %d,
            "startCol": %d,
            "angleDegrees": %.1f,
            "length": %d,
            "width": %d,
            "color": "%s"
          }
          """.formatted(getClass().getSimpleName(), startRow, startCol, angleDegrees, length, width, color);
      var stepCommand = AgentStep.Command.CreateStep.of(sessionId, message, viewport);

      componentClient.forEventSourcedEntity(stepCommand.id())
          .method(AgentStepEntity::createStep)
          .invoke(stepCommand);
    }

    return JsonSupport.encodeToString(shape);
  }
}