package io.example.application;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.JsonSupport;
import io.example.domain.AgentStep;
import io.example.domain.GridCell;

public class DrawTriangleTool {
  static final Logger log = LoggerFactory.getLogger(DrawTriangleTool.class);
  final ComponentClient componentClient;
  final String region;

  public DrawTriangleTool(ComponentClient componentClient, String region) {
    this.componentClient = componentClient;
    this.region = region;
  }

  @FunctionTool(description = """
      Draws a triangular shape on the grid with cells of a specific status. This tool creates a filled triangle
      defined by three coordinate points. All cells within the triangle's boundaries will be set to the specified status.
      Useful for creating custom geometric shapes or complex patterns.
      Returns the triangle shape as a JSON formatted string.
      """)
  public String drawTriangle(
      @Description("The user session id") String sessionId,
      @Description("The viewport") AgentStep.ViewPort viewport,
      @Description("The row coordinate of the first vertex of the triangle") int row1,
      @Description("The column coordinate of the first vertex of the triangle") int col1,
      @Description("The row coordinate of the second vertex of the triangle") int row2,
      @Description("The column coordinate of the second vertex of the triangle") int col2,
      @Description("The row coordinate of the third vertex of the triangle") int row3,
      @Description("The column coordinate of the third vertex of the triangle") int col3,
      @Description("The color to apply to the cell. Use hex #RRGGBB or #RRGGBBAA colors") String color) {

    log.info("Region: {}, Drawing triangle with points ({},{}) ({},{}) ({},{}) with color: {}", region, row1, col1, row2, col2, row3, col3, color);

    var cellId = String.format("%dx%d", row1, col1);
    var shape = GridCell.Shape.ofTriangle(row1, col1, row2, col2, row3, col3);
    {
      var status = GridCell.Status.custom;
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
    }

    {
      var message = """
          {
            "action": "draw_triangle",
            "row1": %d, "col1": %d,
            "row2": %d, "col2": %d,
            "row3": %d, "col3": %d,
            "color": "%s"
          }
          """.formatted(row1, col1, row2, col2, row3, col3, color);
      var command = AgentStep.Command.CreateStep.of(sessionId, message, viewport);

      componentClient.forEventSourcedEntity(command.id())
          .method(AgentStepEntity::createStep)
          .invoke(command);
    }

    return JsonSupport.encodeToString(shape);
  }
}
