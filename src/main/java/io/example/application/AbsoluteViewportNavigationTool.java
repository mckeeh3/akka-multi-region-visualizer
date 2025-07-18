package io.example.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.client.ComponentClient;
import io.example.domain.AgentStep;
import io.example.domain.ViewPort;

public class AbsoluteViewportNavigationTool {
  static final Logger log = LoggerFactory.getLogger(AbsoluteViewportNavigationTool.class);

  final ComponentClient componentClient;
  final String region;

  public AbsoluteViewportNavigationTool(ComponentClient componentClient, String region) {
    this.componentClient = componentClient;
    this.region = region;
  }

  @FunctionTool(description = """
      Moves the viewport to a specific absolute coordinate on the grid. This tool sets the top-left corner
      of the viewport to the specified row and column coordinates, effectively 'jumping' to that location on the grid.
      The viewport dimensions remain the same, but the visible area changes to center around the new coordinates.
      Coordinates are automatically rounded to the nearest 10 for grid alignment.
      """)
  public ViewPort absoluteViewportNavigation(
      @Description("The user session id") String sessionId,
      @Description("The target row coordinate for the top-left corner of the viewport") int row,
      @Description("The target column coordinate for the top-left corner of the viewport") int col,
      @Description("The current viewport information containing dimensions and mouse position") ViewPort viewport) {
    log.info("Region: {}, Absolute viewport navigation: sessionId={}, row={}, col={}, viewport={}", region, sessionId, row, col, viewport);
    int viewportWidth = viewport.bottomRight().col() - viewport.topLeft().col();
    int viewportHeight = viewport.bottomRight().row() - viewport.topLeft().row();

    int newRow = Math.round(row / 10.0f) * 10;
    int newCol = Math.round(col / 10.0f) * 10;

    int updatedTopLeftRow = newRow;
    int updatedTopLeftCol = newCol;
    int updatedBottomRightRow = updatedTopLeftRow + viewportHeight;
    int updatedBottomRightCol = updatedTopLeftCol + viewportWidth;

    {
      var message = """
          {
            "action": "absolute_viewport_navigation",
            "row": %d,
            "col": %d
          }
          """.formatted(row, col);
      var command = AgentStep.Command.CreateStep.of(sessionId, message, viewport);

      componentClient.forEventSourcedEntity(command.id())
          .method(AgentStepEntity::createStep)
          .invoke(command);
    }

    return ViewPort.of(
        updatedTopLeftRow,
        updatedTopLeftCol,
        updatedBottomRightRow,
        updatedBottomRightCol,
        viewport.mouse().row(),
        viewport.mouse().col());
  }
}
