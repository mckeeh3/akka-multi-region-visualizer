package io.example.application;

import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.annotations.Description;
import akka.javasdk.client.ComponentClient;
import io.example.domain.AgentStep.ViewPort;

public class AbsoluteViewportNavigationTool {

    public AbsoluteViewportNavigationTool(ComponentClient componentClient, String region) {
        // componentClient and region are not used in this tool, but are kept for
        // consistency
    }

    @FunctionTool(description = """
            Moves the viewport to a specific absolute coordinate on the grid. This tool sets the top-left corner
            of the viewport to the specified row and column coordinates, effectively 'jumping' to that location on the grid.
            The viewport dimensions remain the same, but the visible area changes to center around the new coordinates.
            Coordinates are automatically rounded to the nearest 10 for grid alignment.
            """)
    public ViewPort absoluteViewportNavigation(
            @Description("The target row coordinate for the top-left corner of the viewport") int row,
            @Description("The target column coordinate for the top-left corner of the viewport") int col,
            @Description("The current viewport information containing dimensions and mouse position") ViewPort viewport) {
        int viewportWidth = viewport.bottomRight().col() - viewport.topLeft().col();
        int viewportHeight = viewport.bottomRight().row() - viewport.topLeft().row();

        int newRow = Math.round(row / 10.0f) * 10;
        int newCol = Math.round(col / 10.0f) * 10;

        int updatedTopLeftRow = newRow;
        int updatedTopLeftCol = newCol;
        int updatedBottomRightRow = updatedTopLeftRow + viewportHeight;
        int updatedBottomRightCol = updatedTopLeftCol + viewportWidth;

        return ViewPort.of(
                updatedTopLeftRow,
                updatedTopLeftCol,
                updatedBottomRightRow,
                updatedBottomRightCol,
                viewport.mouse().row(),
                viewport.mouse().col());
    }
}
