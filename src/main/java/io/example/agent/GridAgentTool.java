package io.example.agent;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.javasdk.client.ComponentClient;
import io.example.agent.LLMResponseParser.Command;
import io.example.application.AgentStepEntity;
import io.example.application.GridCellEntity;
import io.example.application.GridCellView;
import io.example.application.GridCellView.GridCellRow;
import io.example.domain.AgentStep;
import io.example.domain.AgentStep.ViewPort;
import io.example.domain.GridCell;
import io.example.domain.Predator;

/**
 * The GridAgentTool class is a central component in the Akka Multi-Region Visualizer application that serves as an
 * interface between natural language commands and grid manipulation operations. It processes tool commands that have
 * been generated from user inputs (likely from voice commands that were transcribed to text) and executes the
 * appropriate grid operations.
 *
 * <h2>Core Functionality</h2>
 * <ol>
 * <li><b>Command Processing</b>: The class receives text commands, sends them to a Large Language Model (LLM), and then
 * processes the structured commands returned by the LLM to manipulate the grid visualization.</li>
 * <li><b>Grid Manipulation</b>: It provides a comprehensive set of operations to modify the grid state, including:
 * <ul>
 * <li>Drawing single cells with specific statuses</li>
 * <li>Drawing rectangles and circles</li>
 * <li>Clearing cells with similar colors</li>
 * <li>Erasing all active cells</li>
 * <li>Creating predator entities</li>
 * <li>Navigating the viewport (both absolute and relative navigation)</li>
 * </ul>
 * </li>
 * <li><b>State Management</b>: The class maintains the current viewport state and can update it based on navigation
 * commands, ensuring the UI reflects the user's desired view.</li>
 * <li><b>Event Sourcing Integration</b>: It interacts with Akka's event sourcing system to persist changes to the grid
 * state and update the agent steps in the processing pipeline.</li>
 * </ol>
 *
 * <h2>Technical Details</h2>
 * <ul>
 * <li>Uses an OpenAiClient with a specific system prompt (/grid-agent-tool-system-prompt.txt) to interpret user
 * commands</li>
 * <li>Parses JSON commands from the LLM response using the LLMResponseParser</li>
 * <li>Executes different grid operations based on the command type</li>
 * <li>Updates the agent step state to mark it as processed after command execution</li>
 * <li>Handles errors and exceptions during command processing</li>
 * </ul>
 *
 * <h2>Integration Points</h2>
 * <ul>
 * <li>Works with the ComponentClient to interact with Akka entities</li>
 * <li>Integrates with the AgentStep domain model for processing steps</li>
 * <li>Uses GridCellEntity and other grid-related components to modify the grid state</li>
 * <li>Communicates with the AgentStepEntity to update the processing status</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <p>
 * This class is a key part of the application's voice command processing pipeline, serving as the execution engine for
 * grid manipulation commands that have been interpreted from user voice inputs.
 * </p>
 */
public class GridAgentTool {
  static final Logger log = LoggerFactory.getLogger(GridAgentTool.class);
  final ComponentClient componentClient;
  final String region;
  ViewPort viewport;

  public GridAgentTool(ComponentClient componentClient, String region) {
    this.componentClient = componentClient;
    this.region = region;
    this.viewport = null;
  }

  public void chat(String toolCommand, String sequenceId, int stepNumber, String userSessionId, ViewPort viewport) {
    var llmClient = new OpenAiClient("/grid-agent-tool-system-prompt.txt", "o3-mini");

    this.viewport = viewport; // the viewport may be updated by a command

    try {
      var userPrompt = "%s\nCurrent UI view port location: top left row %d, col %d, bottom right row %d, col %d\nMouse location: row %d, col %d"
          .formatted(
              toolCommand,
              viewport.topLeft().row(),
              viewport.topLeft().col(),
              viewport.bottomRight().row(),
              viewport.bottomRight().col(),
              viewport.mouse().row(),
              viewport.mouse().col());
      log.info("User prompt: {}", userPrompt);

      var response = llmClient.chat(userPrompt);
      log.info("LLM response: {}", response);

      var jsonCommands = LLMResponseParser.extractJsonCommands(response);
      var commands = LLMResponseParser.parseCommands(jsonCommands);
      commands.forEach(command -> {
        log.info("LLM response command: {}", command);
        switch (command.getTool()) {
          case "drawSingleCell" -> drawSingleCell(command);
          case "drawRectangle" -> drawRectangle(command);
          case "drawCircle" -> drawCircle(command);
          case "clearLikeColorCells" -> clearLikeColorCells(command);
          case "eraseAllActiveCells" -> eraseAllActiveCells(command);
          case "createPredator" -> createPredator(command);
          case "absoluteViewportNavigation" -> absoluteViewportNavigation(command);
          case "relativeViewportNavigation" -> relativeViewportNavigation(command);
          case "ambiguousTool" -> ambiguousTool(command);
          default -> log.warn("Voice command: Unknown command: {}", command);
        }
      });

      var llmResponse = jsonCommands.toString();
      log.info("JSON commands: {}\n_LLM response: {}", jsonCommands, llmResponse);
      var command = AgentStep.Command.ProcessedStep.of(sequenceId, stepNumber, llmResponse, viewport);
      componentClient.forEventSourcedEntity(command.id())
          .method(AgentStepEntity::processedStep)
          .invoke(command);
    } catch (IOException | InterruptedException e) {
      log.error("Voice command: Failed to get LLM response", e);
      throw new GridAgentToolException("Failed to get LLM response", e);
    }
  }

  void drawSingleCell(Command command) {
    var parameters = command.getParameters();
    var row = parameters.get("row").asInt();
    var col = parameters.get("col").asInt();
    var status = parameters.get("status").asText();
    log.info("Draw single cell at row {} and column {} with status {}", row, col, status);

    var cellId = String.format("%dx%d", row, col);
    var cellStatus = GridCell.Status.valueOf(status.toLowerCase());
    var cellCommand = new GridCell.Command.UpdateCell(
        cellId,
        cellStatus,
        Instant.now(),
        Instant.now(),
        region);

    componentClient.forEventSourcedEntity(cellId)
        .method(GridCellEntity::updateStatus)
        .invoke(cellCommand);
  }

  void drawRectangle(Command command) {
    var parameters = command.getParameters();
    var row1 = parameters.get("row1").asInt();
    var col1 = parameters.get("col1").asInt();
    var row2 = parameters.get("row2").asInt();
    var col2 = parameters.get("col2").asInt();
    var status = parameters.get("status").asText();
    log.info("Draw rectangle from row {} and column {} to row {} and column {} with status {}", row1, col1, row2, col2, status);

    var cellId = String.format("%dx%d", row1, col1);
    var shape = GridCell.Shape.ofRectangle(col1, row1, col2, row2);
    var createShapeCommand = new GridCell.Command.CreateShape(
        cellId,
        GridCell.Status.valueOf(status.toLowerCase()),
        Instant.now(),
        Instant.now(),
        shape,
        region);

    componentClient.forEventSourcedEntity(cellId)
        .method(GridCellEntity::createShape)
        .invoke(createShapeCommand);
  }

  void drawCircle(Command command) {
    var parameters = command.getParameters();
    var row = parameters.get("row").asInt();
    var col = parameters.get("col").asInt();
    var status = parameters.get("status").asText();
    var radius = parameters.get("radius").asInt();
    log.info("Draw circle at row {} and column {} with status {} and radius {}", row, col, status, radius);

    var cellId = String.format("%dx%d", row, col);
    var shape = GridCell.Shape.ofCircle(col, row, Math.min(30, radius));
    var createShapeCommand = new GridCell.Command.CreateShape(
        cellId,
        GridCell.Status.valueOf(status.toLowerCase()),
        Instant.now(),
        Instant.now(),
        shape,
        region);

    componentClient.forEventSourcedEntity(cellId)
        .method(GridCellEntity::createShape)
        .invoke(createShapeCommand);
  }

  void clearLikeColorCells(Command command) {
    var parameters = command.getParameters();
    var row = parameters.get("row").asInt();
    var col = parameters.get("col").asInt();
    var status = parameters.get("status").asText();
    log.info("Clear like color cells at row {} and column {} with status {}", row, col, status);

    var cellId = String.format("%dx%d", row, col);
    var clearCommand = new GridCell.Command.ClearCells(cellId, GridCell.Status.valueOf(status.toLowerCase()));
    componentClient.forEventSourcedEntity(cellId)
        .method(GridCellEntity::updateClearStatus)
        .invoke(clearCommand);
  }

  void eraseAllActiveCells(Command command) {
    var parameters = command.getParameters();
    var row = parameters.get("row").asInt();
    var col = parameters.get("col").asInt();
    log.info("Erase all active cells at row {} and column {}", row, col);

    var cellId = String.format("%dx%d", row, col);
    var eraseCommand = new GridCell.Command.EraseCells(cellId);
    componentClient.forEventSourcedEntity(cellId)
        .method(GridCellEntity::updateEraseStatus)
        .invoke(eraseCommand);
  }

  void createPredator(Command command) {
    var parameters = command.getParameters();
    var row = parameters.get("row").asInt();
    var col = parameters.get("col").asInt();
    var range = parameters.get("range").asInt();
    log.info("Create predator at row {} and column {} with range {}", row, col, range);

    var x1 = col - range;
    var y1 = row - range;
    var x2 = col + range;
    var y2 = row + range;
    var pageTokenOffset = "";

    var activeGridCells = queryGridCellsInArea(x1, y1, x2, y2, pageTokenOffset);
    log.info("Found {} grid cells in the rectangle area", activeGridCells.size());

    var cellId = String.format("%dx%d", row, col);
    var nextGridCellId = Predator.nextGridCellId(cellId, activeGridCells, range);
    log.info("Predator cell: {}, Next cell: {}", cellId, nextGridCellId);

    var predatorId = Predator.parentId();
    var predatorCommand = new GridCell.Command.CreatePredator(
        cellId,
        predatorId,
        GridCell.Status.predator,
        Instant.now(),
        Instant.now(),
        range,
        nextGridCellId,
        region);

    componentClient.forEventSourcedEntity(cellId)
        .method(GridCellEntity::createPredator)
        .invoke(predatorCommand);
  }

  void absoluteViewportNavigation(Command command) {
    var parameters = command.getParameters();

    // Check if col and row parameters exist
    boolean hasRow = parameters.has("row");
    boolean hasCol = parameters.has("col");

    int viewportWidth = viewport.bottomRight().col() - viewport.topLeft().col();
    int viewportHeight = viewport.bottomRight().row() - viewport.topLeft().row();

    int newRow = hasRow ? parameters.get("row").asInt() : viewport.topLeft().row();
    newRow = Math.round(newRow / 10.0f) * 10;
    int newCol = hasCol ? parameters.get("col").asInt() : viewport.topLeft().col();
    newCol = Math.round(newCol / 10.0f) * 10;

    boolean rowChanged = hasRow && newRow != viewport.topLeft().row();
    boolean colChanged = hasCol && newCol != viewport.topLeft().col();

    if (rowChanged || colChanged) {
      int updatedTopLeftRow = rowChanged ? newRow : viewport.topLeft().row();
      int updatedTopLeftCol = colChanged ? newCol : viewport.topLeft().col();
      int updatedBottomRightRow = updatedTopLeftRow + viewportHeight;
      int updatedBottomRightCol = updatedTopLeftCol + viewportWidth;

      var updatedViewport = ViewPort.of(
          updatedTopLeftRow,
          updatedTopLeftCol,
          updatedBottomRightRow,
          updatedBottomRightCol,
          viewport.mouse().row(),
          viewport.mouse().col());

      log.info("Viewport moved \n_from {} \n_to   {}", viewport, updatedViewport);
      this.viewport = updatedViewport;
    }
  }

  void relativeViewportNavigation(Command command) {
    var parameters = command.getParameters();
    var direction = parameters.get("direction").asText();
    var amount = parameters.get("amount").asInt();
    amount = Math.round(amount / 10.0f) * 10;

    int viewportWidth = viewport.bottomRight().col() - viewport.topLeft().col();
    int viewportHeight = viewport.bottomRight().row() - viewport.topLeft().row();

    // Calculate delta changes based on direction
    int deltaCol = 0;
    int deltaRow = 0;

    switch (direction.toLowerCase()) {
      case "left" -> deltaCol = -amount;
      case "right" -> deltaCol = amount;
      case "up" -> deltaRow = -amount;
      case "down" -> deltaRow = amount;
      default -> {
        log.warn("Unknown direction: {}", direction);
        return;
      }
    }

    if (deltaCol != 0 || deltaRow != 0) {
      int newTopLeftCol = viewport.topLeft().col() + deltaCol;
      int newTopLeftRow = viewport.topLeft().row() + deltaRow;
      int newBottomRightCol = newTopLeftCol + viewportWidth;
      int newBottomRightRow = newTopLeftRow + viewportHeight;

      var updatedViewport = ViewPort.of(
          newTopLeftRow,
          newTopLeftCol,
          newBottomRightRow,
          newBottomRightCol,
          viewport.mouse().row(),
          viewport.mouse().col());
      log.info("Viewport moved \n_from {} \n_to   {}", viewport, updatedViewport);
      this.viewport = updatedViewport;
    }
  }

  void ambiguousTool(Command command) {
    var parameters = command.getParameters();
    var message = parameters.get("message").asText();
    log.info("Ambiguous tool: {}", message);
  }

  List<GridCellRow> queryGridCellsInArea(int x1, int y1, int x2, int y2, String pageTokenOffset) {
    return Stream.generate(new Supplier<GridCellView.PagedGridCells>() {
      String currentPageToken = pageTokenOffset;
      boolean hasMore = true;

      @Override
      public GridCellView.PagedGridCells get() {
        if (!hasMore) {
          return null;
        }

        var pagedGridCells = componentClient.forView()
            .method(GridCellView::queryActiveGridCells)
            .invoke(new GridCellView.PagedGridCellsRequest(x1, y1, x2, y2, currentPageToken));

        currentPageToken = pagedGridCells.nextPageToken();
        hasMore = pagedGridCells.hasMore();

        return pagedGridCells;
      }
    })
        .takeWhile(pagedGridCells -> pagedGridCells != null)
        .flatMap(pagedGridCells -> pagedGridCells.gridCells().stream())
        .toList();
  }

  public static class GridAgentToolException extends RuntimeException {
    public GridAgentToolException(String message) {
      super(message);
    }

    public GridAgentToolException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  /**
   * Runs the agent in a virtual thread to process audio transcription asynchronously.
   *
   * This method processes agent steps, which are tool commands prompts.
   *
   * <ol>
   * <li>Creating a virtual thread to handle the tool commands</li>
   * <li>Processing the tool commands to extract text content</li>
   * <li>Updates an AgentStep entity setting the step status to 'processed'</li>
   * </ol>
   *
   * @param userPrompt      The tool commands prompt
   * @param sequenceId      The unique identifier for the processing flow
   * @param userSessionId   The user's web app session ID
   * @param viewport        The current viewport information for context
   * @param componentClient The Akka component client for entity interactions
   * @param region          The region where the component client is running
   * @return A CompletionStage that completes with the sequence ID when processing starts
   */

  public static CompletionStage<Void> chat(
      String userPrompt,
      String sequenceId,
      int stepNumber,
      String userSessionId,
      AgentStep.ViewPort viewport,
      ComponentClient componentClient,
      String region) {

    var future = new CompletableFuture<Void>();

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      executor.submit(() -> {
        try {
          var agent = new GridAgentTool(componentClient, region);
          agent.chat(userPrompt, sequenceId, stepNumber, userSessionId, viewport);
          future.complete(null);
        } catch (Exception e) {
          log.error("Error processing tool commands in virtual thread", e);
          future.completeExceptionally(e);
        }
        return null;
      });
    }
    return future;
  }
}
