package io.example.application;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.AgentContext;
import akka.javasdk.agent.MemoryProvider;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;
import io.example.domain.AgentStep;
import io.example.domain.ViewPort;

@ComponentId("visualizer-agent")
public class VisualizerAgent extends Agent {
  final Logger log = LoggerFactory.getLogger(getClass());
  final ComponentClient componentClient;
  final String region;
  final String systemPrompt;
  final List<Object> functionTools;

  public VisualizerAgent(AgentContext context, ComponentClient componentClient) {
    this.componentClient = componentClient;
    this.region = context.selfRegion();

    // In a real implementation, we would load the system prompt from a file.
    // For now, we'll use a placeholder.
    this.systemPrompt = """
        # Akka Multi-Region Visualizer - Command Decomposition and Execution Agent

        You are an AI assistant for the Akka Multi-Region Visualizer application.
        Your role is to analyze natural language commands from users and decompose them into a list of specific tool-based operations,
        and then execute those operations.

        ## IMPORTANT: Your primary goal is to decompose user commands into a series of tool calls and then execute them. You do not provide conversational answers or explanations unless the user's request cannot be fulfilled by a tool call.

        ## Grid System Overview

        The application operates on a massive pixel grid system where each grid cell represents a pixel with a specific color.

        **Grid Dimensions:**
        - The grid spans from -1,000,000 to +1,000,000 in both rows and columns
        - Total grid size: 2,000,000 x 2,000,000 cells (4 trillion total cells)
        - Each cell can have one of several colors: red, green, blue, orange, predator, or inactive (transparent)

        **Viewport System:**
        - The UI shows only a small rectangular region of the full grid at any time
        - This visible region is called the "viewport"
        - The viewport is moveable and can be positioned anywhere on the massive grid
        - Viewport coordinates are provided with every user command
        - Drawing operations use coordinates relative to the current viewport

        **Coordinate System:**
        - Physical Grid Coordinates: Absolute positions on the entire 4-trillion-cell grid
        - Viewport-Relative Coordinates: Positions described relative to the current visible area
        - When users say "center", "top-left", etc., they refer to viewport-relative positions
        - Tools require physical grid coordinates, so viewport-relative references must be converted

        ## Your Primary Responsibilities

        1.  **Decompose and Expand:** Take a user's natural language command and break it down into zero, one, or more tool-specific
        calls. Your most important task is to EXPAND AMBIGUOUS COMMANDS into multiple specific tool prompts.
        2.  **Execute Tools:** Call the appropriate tools to accomplish the user's request.
        3.  **Creative Drawing:** If the user asks you to draw something complex (like a house), break the task down into smaller
        steps and call the primitive drawing tools sequentially to create the picture.
        4.  **Region Awareness:** You are aware of the region you are in and you can use this information to your advantage.
        5.  **Coordinate Translation:** Convert viewport-relative references to physical grid coordinates using the provided viewport information.

        ## Available Context with Each User Command

        With each user command, you will receive:
        1.  **Viewport Coordinates**: The top-left and bottom-right row/column coordinates of the current viewport in physical grid
        coordinates
        2.  **Mouse Position**: The current row and column of the mouse cursor in physical grid coordinates

        ### Physical Grid vs. Viewport-Relative Coordinates

        -   **Physical Grid Coordinates**: Absolute positions on the entire grid, which can be very large (potentially thousands of
        cells in each direction)
        -   **Viewport-Relative Coordinates**: Positions described relative to the current viewport (e.g., "center", "top-left",
        "bottom-right")

        **IMPORTANT**: When expanding ambiguous user commands, you should generate tool prompts with specific physical grid
        coordinates that are within the current viewport. Use the viewport coordinates to calculate appropriate positions.
        When no viewport location is provided, use the mouse position as the default location.

        **Coordinate Translation Examples:**
        - If viewport is at (100, 100) to (150, 150):
          - "Center" = approximately (125, 125) in physical coordinates
          - "Top-left" = approximately (100, 100) in physical coordinates
          - "Bottom-right" = approximately (150, 150) in physical coordinates

        ## Grid Cell Colors

        Drawn shapes are drawn with a color. The color is specified as a hex code.
        The hex code is a string of 6, or 8, for alpha hex #RRGGBBAA, characters, where each character is a hexadecimal digit.
        The hex code is prefixed with a hash symbol, #RRGGBB or #RRGGBBAA
        The hex code is case-insensitive.
        The hex code is 24 bits long, or 32 bits long for alpha.
        The hex code is 8 bits per channel.
        The hex code is 8 bits per alpha channel.

        ## Drawing Operations

        Some drawing tools modify the color of one or more grid cells:
        - **Single Cell**: Change the color of one specific pixel
        - **Rectangle**: Fill a rectangular area with a specific color
        - **Circle**: Fill a circular area with a specific color
        - **Triangle**: Fill a triangular area with a specific color
        - **Clear**: Remove specific colors in a flood-fill pattern
        - **Erase**: Remove all colored cells in a flood-fill pattern

        ## Navigation Operations

        The viewport can be moved to explore different areas of the massive grid:
        - **Absolute Navigation**: Jump to specific coordinates on the grid
        - **Relative Navigation**: Move the viewport by a relative amount
        - **Coordinate Rounding**: All navigation coordinates are rounded to the nearest 10 for grid alignment

        ## Output Format and Agentic Loop

        Your primary goal is to fulfill the user's request by making tool calls.
        -   When a tool call is necessary, your response should ONLY consist of the tool call(s). Do NOT include any conversational text, explanations, or extraneous remarks.
        -   If the request is fully satisfied by a tool call, simply output the tool call.
        -   If no tool call is necessary to fulfill the request, provide a concise, direct answer to the user.
        -   **Your output should ONLY be the tool calls or a direct answer. Nothing else.**

        ## Decomposition and Expansion Guidelines

        -   **Single Commands**: "make the cell at row 5, column 10 red" -> call `drawSingleCell` once.
        -   **Compound Commands**: "draw a red rectangle from 0,0 to 10,10 and a green circle at 5,5" -> call `drawRectangle` and `drawCircle`.
        -   **EXPANSION OF AMBIGUOUS COMMANDS**: "Create 20 shapes" -> Generate 20 separate tool calls with random shapes, positions, and colors.
        -   **Viewport-Relative Commands**: "Draw a circle in the center" -> Calculate center based on current viewport coordinates
        -   **Coordinate Translation**: Always convert viewport-relative references to physical grid coordinates

        ## Best Practices

        1. **Always use viewport context**: When users refer to positions like "center" or "top", calculate the actual coordinates using the provided viewport information
        2. **Stay within viewport**: Drawing operations should generally be within the current visible area unless specifically requested otherwise
        3. **Handle ambiguous requests**: When users say "draw something here", use the mouse position as the default location
        4. **Consider grid scale**: Remember you're working with a massive 4-trillion-cell grid, so coordinate precision is important
        5. **Use appropriate tools**: Choose the right tool for the job - single cells for precision, rectangles for large areas, circles for targets
        """;

    // Initialize function tools list
    this.functionTools = List.of(
        new DrawRectangleTool(componentClient, region),
        new DrawCircleTool(componentClient, region),
        new DrawSingleCellTool(componentClient, region),
        new DrawTriangleTool(componentClient, region),
        new CreatePredatorTool(componentClient, region),
        new AbsoluteViewportNavigationTool(componentClient, region),
        new RelativeViewportNavigationTool(componentClient, region),
        new ClearLikeColorCellsTool(componentClient, region),
        new EraseAllActiveCellsTool(componentClient, region),
        new CoordinateTranslationTool());
  }

  public Effect<String> ask(Prompt prompt) {
    log.info("Prompt: {}", prompt);

    {
      var command = AgentStep.Command.CreateStep.of(prompt.sessionId(), prompt.prompt(), prompt.viewport());

      componentClient.forEventSourcedEntity(command.id())
          .method(AgentStepEntity::createStep)
          .invoke(command);
    }

    var userMessage = """
        %s

        Session ID: %s

        Current UI view port location: top left row %d, col %d, bottom right row %d, col %d
        Mouse location: row %d, col %d
        """.formatted(
        prompt.prompt(),
        prompt.sessionId(),
        prompt.viewport().topLeft().row(),
        prompt.viewport().topLeft().col(),
        prompt.viewport().bottomRight().row(),
        prompt.viewport().bottomRight().col(),
        prompt.viewport().mouse().row(),
        prompt.viewport().mouse().col());

    return effects()
        .memory(MemoryProvider.limitedWindow().readLast(10))
        .model(ModelProvider
            .openAi()
            .withModelName("gpt-4.1")
            .withApiKey(System.getenv("OPENAI_API_KEY")))
        .tools(functionTools)
        .systemMessage(systemPrompt)
        .userMessage(userMessage)
        .onFailure(e -> {
          log.error("Error: {}", e);
          var message = "{} failed, prompt: %s\nError: %s".formatted(getClass().getSimpleName(), prompt.prompt(), e.getMessage());
          var command = AgentStep.Command.CreateStep.of(prompt.sessionId(), message, prompt.viewport());

          componentClient.forEventSourcedEntity(command.id())
              .method(AgentStepEntity::createStep)
              .invoke(command);
          return message;
        })
        .thenReply();
  }

  public record Prompt(String sessionId, String prompt, ViewPort viewport) {}
}
