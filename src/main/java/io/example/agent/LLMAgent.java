package io.example.agent;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.javasdk.client.ComponentClient;
import io.example.agent.LLMResponseParser.Command;
import io.example.application.GridCellEntity;
import io.example.application.GridCellView;
import io.example.application.GridCellView.GridCellRow;
import io.example.domain.GridCell;
import io.example.domain.Predator;
import io.example.api.FillRectangle;

public class LLMAgent {
  final Logger log = LoggerFactory.getLogger(LLMAgent.class);
  final ComponentClient componentClient;
  ViewPort viewport;
  final String region;

  public LLMAgent(ComponentClient componentClient, ViewPort viewport, String region) {
    this.componentClient = componentClient;
    this.viewport = viewport;
    this.region = region;
  }

  public String chat(String contentType, InputStream input) throws LLMException {
    // Parse the HTTP multipart content data and extract the audio data
    var parser = new MultipartFormDataParser(contentType, input);
    try {
      parser.parse();
    } catch (IOException e) {
      log.error("Failed to parse multipart form data", e);
      throw new LLMException("Failed to parse multipart form data");
    }

    var audioData = parser.getFile();
    if (audioData == null) {
      log.error("No audio data found");
      throw new LLMException("No audio data found");
    }
    log.info("Audio data length: {}", audioData.length);

    // Transcribe the audio to text
    var audioToText = "";
    try {
      audioToText = new AudioToTextTranscription().transcribeAudio(audioData);
      log.info("Transcription: {}", audioToText);
    } catch (IOException | InterruptedException e) {
      log.error("Failed to transcribe audio", e);
      throw new LLMException("Failed to transcribe audio", e);
    }

    // Send the transcribed audio to the LLM
    var llmClient = new LLMClient();
    try {
      var userPrompt = "%s\nCurrent UI view port location: top left x %d, y %d, bottom right x %d, y %d"
          .formatted(audioToText, viewport.topLeftX(), viewport.topLeftY(), viewport.bottomRightX(), viewport.bottomRightY());
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

      return jsonCommands.toString();
    } catch (IOException | InterruptedException e) {
      log.error("Voice command: Failed to get LLM response", e);
      throw new LLMException("Failed to get LLM response", e);
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
    var cellCommand = new GridCell.Command.UpdateStatus(
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

    var request = new FillRectangle.Request(
        col1,
        row1,
        col2,
        row2,
        region,
        Instant.now(),
        Instant.now(),
        GridCell.Status.valueOf(status.toLowerCase()));
    FillRectangle.fillRectangle(request, componentClient);
  }

  void drawCircle(Command command) {
    var parameters = command.getParameters();
    var row = parameters.get("row").asInt();
    var col = parameters.get("col").asInt();
    var status = parameters.get("status").asText();
    var radius = parameters.get("radius").asInt();
    log.info("Draw circle at row {} and column {} with status {} and radius {}", row, col, status, radius);

    var cellId = String.format("%dx%d", row, col);
    var cellActive = false;
    try {
      var cellState = componentClient.forEventSourcedEntity(cellId)
          .method(GridCellEntity::get)
          .invoke();
      cellActive = !cellState.status().equals(GridCell.Status.inactive) && !cellState.status().equals(GridCell.Status.predator);
    } catch (Exception ignore) {
      cellActive = false;
    }

    log.info("Cell {} is active: {}", cellId, cellActive);

    if (cellActive) {
      var spanCommand = new GridCell.Command.SpanStatus(
          cellId,
          GridCell.Status.valueOf(status.toLowerCase()),
          Instant.now(),
          Instant.now(),
          col,
          row,
          Math.min(30, radius),
          region);
      componentClient.forEventSourcedEntity(cellId)
          .method(GridCellEntity::updateSpanStatus)
          .invoke(spanCommand);
    } else {
      var fillCommand = new GridCell.Command.FillStatus(
          cellId,
          GridCell.Status.valueOf(status.toLowerCase()),
          Instant.now(),
          Instant.now(),
          col,
          row,
          Math.min(30, radius),
          region);
      componentClient.forEventSourcedEntity(cellId)
          .method(GridCellEntity::updateFillStatus)
          .invoke(fillCommand);
    }
  }

  void clearLikeColorCells(Command command) {
    var parameters = command.getParameters();
    var row = parameters.get("row").asInt();
    var col = parameters.get("col").asInt();
    var status = parameters.get("status").asText();
    log.info("Clear like color cells at row {} and column {} with status {}", row, col, status);

    var cellId = String.format("%dx%d", row, col);
    var clearCommand = new GridCell.Command.ClearStatus(cellId, GridCell.Status.valueOf(status.toLowerCase()));
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
    var eraseCommand = new GridCell.Command.EraseStatus(cellId);
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

    var allGridCells = queryGridCellsInArea(x1, y1, x2, y2, pageTokenOffset);
    log.info("Found {} grid cells in the rectangle area", allGridCells.size());

    var cellId = String.format("%dx%d", row, col);
    String nextGridCellId = Predator.nextGridCellId(cellId, allGridCells, range);
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

    // Check if x and y parameters exist
    boolean hasX = parameters.has("x");
    boolean hasY = parameters.has("y");

    int viewportWidth = viewport.bottomRightX() - viewport.topLeftX();
    int viewportHeight = viewport.bottomRightY() - viewport.topLeftY();

    int newX = hasX ? parameters.get("x").asInt() : viewport.topLeftX();
    newX = Math.round(newX / 10.0f) * 10;
    int newY = hasY ? parameters.get("y").asInt() : viewport.topLeftY();
    newY = Math.round(newY / 10.0f) * 10;

    boolean xChanged = hasX && newX != viewport.topLeftX();
    boolean yChanged = hasY && newY != viewport.topLeftY();

    if (xChanged || yChanged) {
      int updatedTopLeftX = xChanged ? newX : viewport.topLeftX();
      int updatedTopLeftY = yChanged ? newY : viewport.topLeftY();
      int updatedBottomRightX = updatedTopLeftX + viewportWidth;
      int updatedBottomRightY = updatedTopLeftY + viewportHeight;

      var updatedViewport = new ViewPort(updatedTopLeftX, updatedTopLeftY, updatedBottomRightX, updatedBottomRightY);

      log.info("Viewport moved \n_from {} \n_to   {}", viewport, updatedViewport);
      this.viewport = updatedViewport;
    }
  }

  void relativeViewportNavigation(Command command) {
    var parameters = command.getParameters();
    var direction = parameters.get("direction").asText();
    var amount = parameters.get("amount").asInt();
    amount = Math.round(amount / 10.0f) * 10;

    int viewportWidth = viewport.bottomRightX() - viewport.topLeftX();
    int viewportHeight = viewport.bottomRightY() - viewport.topLeftY();

    // Calculate delta changes based on direction
    int deltaX = 0;
    int deltaY = 0;

    switch (direction.toLowerCase()) {
      case "left" -> deltaX = -amount;
      case "right" -> deltaX = amount;
      case "up" -> deltaY = -amount;
      case "down" -> deltaY = amount;
      default -> {
        log.warn("Unknown direction: {}", direction);
        return;
      }
    }

    if (deltaX != 0 || deltaY != 0) {
      int newTopLeftX = viewport.topLeftX() + deltaX;
      int newTopLeftY = viewport.topLeftY() + deltaY;
      int newBottomRightX = newTopLeftX + viewportWidth;
      int newBottomRightY = newTopLeftY + viewportHeight;

      var updatedViewport = new ViewPort(newTopLeftX, newTopLeftY, newBottomRightX, newBottomRightY);
      log.info("Viewport moved \n_from {} \n_to   {}", viewport, updatedViewport);
      this.viewport = updatedViewport;
    }
  }

  void showCellDetails(Command command) {
    var parameters = command.getParameters();
    var x = parameters.get("x").asInt();
    var y = parameters.get("y").asInt();
    log.info("Show cell details at x {} and y {}", x, y);
  }

  void showTimingOverlay(Command command) {
    var parameters = command.getParameters();
    var x = parameters.get("x").asInt();
    var y = parameters.get("y").asInt();
    log.info("Show timing overlay at x {} and y {}", x, y);
  }

  void ambiguousTool(Command command) {
    var parameters = command.getParameters();
    var message = parameters.get("message").asText();
    log.info("Ambiguous tool: {}", message);
  }

  public class LLMException extends RuntimeException {
    public LLMException(String message) {
      super(message);
    }

    public LLMException(String message, Throwable cause) {
      super(message, cause);
    }
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

  public record ViewPort(int topLeftX, int topLeftY, int bottomRightX, int bottomRightY) {}
}
