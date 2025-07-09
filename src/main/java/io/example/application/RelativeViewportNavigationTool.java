package io.example.application;

import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.annotations.Description;
import akka.javasdk.client.ComponentClient;
import io.example.domain.AgentStep.ViewPort;

public class RelativeViewportNavigationTool {

    public RelativeViewportNavigationTool(ComponentClient componentClient, String region) {
        // componentClient and region are not used in this tool, but are kept for
        // consistency
    }

    @FunctionTool(description = """
            Moves the viewport by a relative amount in the specified direction. This tool shifts the current
            viewport position by the given amount of grid cells, allowing for smooth navigation around the grid.
            The movement is relative to the current viewport position, making it useful for exploring nearby areas
            or making small adjustments to the current view. Amounts are automatically rounded to the nearest 10 for grid alignment.
            """)
    public ViewPort relativeViewportNavigation(
            @Description("The direction to move the viewport. Valid values: 'left', 'right', 'up', 'down'") String direction,
            @Description("The number of grid cells to move in the specified direction") int amount,
            @Description("The current viewport information containing dimensions and mouse position") ViewPort viewport) {
        amount = Math.round(amount / 10.0f) * 10;

        int viewportWidth = viewport.bottomRight().col() - viewport.topLeft().col();
        int viewportHeight = viewport.bottomRight().row() - viewport.topLeft().row();

        int deltaCol = 0;
        int deltaRow = 0;

        switch (direction.toLowerCase()) {
            case "left" -> deltaCol = -amount;
            case "right" -> deltaCol = amount;
            case "up" -> deltaRow = -amount;
            case "down" -> deltaRow = amount;
        }

        int newTopLeftCol = viewport.topLeft().col() + deltaCol;
        int newTopLeftRow = viewport.topLeft().row() + deltaRow;
        int newBottomRightCol = newTopLeftCol + viewportWidth;
        int newBottomRightRow = newTopLeftRow + viewportHeight;

        return ViewPort.of(
                newTopLeftRow,
                newTopLeftCol,
                newBottomRightRow,
                newBottomRightCol,
                viewport.mouse().row(),
                viewport.mouse().col());
    }
}
