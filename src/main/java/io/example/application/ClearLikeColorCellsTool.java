package io.example.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.client.ComponentClient;
import io.example.domain.AgentStep;
import io.example.domain.GridCell;
import io.example.domain.ViewPort;

public class ClearLikeColorCellsTool {
  static final Logger log = LoggerFactory.getLogger(ClearLikeColorCellsTool.class);
  final ComponentClient componentClient;
  final String region;

  public ClearLikeColorCellsTool(ComponentClient componentClient, String region) {
    this.componentClient = componentClient;
    this.region = region;
  }

  @FunctionTool(description = """
      Clears all cells of a specific color in a propagating pattern starting from the given coordinates.
      This tool removes all cells of the specified status/color that are connected to the starting point.
      The clearing effect spreads to adjacent cells of the same color, creating a flood-fill effect.
      Useful for removing large areas of the same color or creating 'holes' in colored regions.
      """)
  public void clearLikeColorCells(
      @Description("The user session id") String sessionId,
      @Description("The viewport information containing top-left, bottom-right, and mouse coordinates") ViewPort viewport,
      @Description("The row coordinate where the clearing should start") int row,
      @Description("The column coordinate where the clearing should start") int col,
      @Description("The color of cells to clear") String color) {

    log.info("Region: {}, Clearing cells: row: {}, col: {}, color: {}", region, row, col, color);

    var cellId = String.format("%dx%d", row, col);
    var command = new GridCell.Command.ClearCells(cellId, GridCell.Status.valueOf(color.toLowerCase()));

    componentClient.forEventSourcedEntity(cellId)
        .method(GridCellEntity::updateClearStatus)
        .invoke(command);

    {
      var message = """
          {
            "action": "clear_like_color_cells",
            "row": %d,
            "col": %d,
            "color": "%s"
          }
          """.formatted(row, col, color);
      var stepCommand = AgentStep.Command.CreateStep.of(sessionId, message, viewport);

      componentClient.forEventSourcedEntity(stepCommand.id())
          .method(AgentStepEntity::createStep)
          .invoke(stepCommand);
    }
  }
}
