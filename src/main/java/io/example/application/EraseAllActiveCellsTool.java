package io.example.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.client.ComponentClient;
import io.example.domain.GridCell;

public class EraseAllActiveCellsTool {
  static final Logger log = LoggerFactory.getLogger(EraseAllActiveCellsTool.class);
  final ComponentClient componentClient;
  final String region;

  public EraseAllActiveCellsTool(ComponentClient componentClient, String region) {
    this.componentClient = componentClient;
    this.region = region;
  }

  @FunctionTool(description = """
      Erases all colored cells in a propagating pattern starting from the given coordinates.
      This tool removes all active cells (any color except 'inactive') that are connected to the starting point.
      The erasing effect spreads to adjacent active cells, creating a flood-fill effect that clears large areas.
      Useful for clearing the entire grid or removing large sections of colored cells at once.
      """)
  public void eraseAllActiveCells(
      @Description("The row coordinate where the erasing should start") int row,
      @Description("The column coordinate where the erasing should start") int col) {

    log.info("Erasing all active cells at row {} and column {}", row, col);

    var cellId = String.format("%dx%d", row, col);
    var command = new GridCell.Command.EraseCells(cellId);

    componentClient.forEventSourcedEntity(cellId)
        .method(GridCellEntity::updateEraseStatus)
        .invoke(command);
  }
}
