package io.example.application;

import akka.javasdk.annotations.FunctionTool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.javasdk.annotations.Description;
import io.example.domain.AgentStep.ViewPort;

public class CoordinateTranslationTool {
  static final Logger log = LoggerFactory.getLogger(CoordinateTranslationTool.class);
  static final int GRID_MIN = -1_000_000;
  static final int GRID_MAX = 1_000_000;

  public CoordinateTranslationTool() {
    // No dependencies needed for this tool
  }

  @FunctionTool(description = """
      Translates viewport-relative coordinates to absolute grid coordinates with boundary checking.
      This tool converts coordinates that are relative to the current viewport (like "center", "top-left",
      or specific offsets) into absolute coordinates on the massive grid. The translation includes
      boundary checking to ensure coordinates never exceed the grid limits of -1,000,000 to +1,000,000.
      Out-of-bounds coordinates are truncated to the nearest valid boundary.
      """)
  public TranslationResult translateViewportToAbsolute(
      @Description("The viewport-relative row coordinate to translate") int viewportRow,
      @Description("The viewport-relative column coordinate to translate") int viewportCol,
      @Description("The current viewport information containing top-left and bottom-right coordinates") ViewPort viewport) {

    log.info("Translating viewport position: {} with viewport: {}", viewportRow, viewportCol, viewport);

    // Calculate absolute coordinates by adding viewport offset
    var absoluteRow = viewport.topLeft().row() + viewportRow;
    var absoluteCol = viewport.topLeft().col() + viewportCol;

    // Apply boundary checking and truncation
    var boundedRow = Math.max(GRID_MIN, Math.min(GRID_MAX, absoluteRow));
    var boundedCol = Math.max(GRID_MIN, Math.min(GRID_MAX, absoluteCol));

    // Check if truncation was needed
    var rowTruncated = absoluteRow != boundedRow;
    var colTruncated = absoluteCol != boundedCol;

    return new TranslationResult(
        viewportRow, viewportCol,
        absoluteRow, absoluteCol,
        boundedRow, boundedCol,
        rowTruncated, colTruncated);
  }

  @FunctionTool(description = """
      Translates common viewport-relative positions to absolute grid coordinates.
      This tool handles common viewport references like "center", "top-left", "bottom-right", etc.
      and converts them to absolute coordinates with boundary checking.
      """)
  public TranslationResult translateViewportPosition(
      @Description("The viewport position to translate. Valid values: 'center', 'top-left', 'top-right', 'bottom-left', 'bottom-right', 'top-center', 'bottom-center', 'left-center', 'right-center'") String position,
      @Description("The current viewport information containing top-left and bottom-right coordinates") ViewPort viewport) {

    log.info("Translating viewport position: {} with viewport: {}", position, viewport);

    int viewportRow, viewportCol;

    // Calculate viewport dimensions
    var viewportHeight = viewport.bottomRight().row() - viewport.topLeft().row();
    var viewportWidth = viewport.bottomRight().col() - viewport.topLeft().col();

    // Map position strings to relative coordinates
    switch (position.toLowerCase()) {
      case "center" -> {
        viewportRow = viewportHeight / 2;
        viewportCol = viewportWidth / 2;
      }
      case "top-left" -> {
        viewportRow = 0;
        viewportCol = 0;
      }
      case "top-right" -> {
        viewportRow = 0;
        viewportCol = viewportWidth;
      }
      case "bottom-left" -> {
        viewportRow = viewportHeight;
        viewportCol = 0;
      }
      case "bottom-right" -> {
        viewportRow = viewportHeight;
        viewportCol = viewportWidth;
      }
      case "top-center" -> {
        viewportRow = 0;
        viewportCol = viewportWidth / 2;
      }
      case "bottom-center" -> {
        viewportRow = viewportHeight;
        viewportCol = viewportWidth / 2;
      }
      case "left-center" -> {
        viewportRow = viewportHeight / 2;
        viewportCol = 0;
      }
      case "right-center" -> {
        viewportRow = viewportHeight / 2;
        viewportCol = viewportWidth;
      }
      default -> {
        // Default to center if unknown position
        viewportRow = viewportHeight / 2;
        viewportCol = viewportWidth / 2;
      }
    }

    // Use the existing translation method
    return translateViewportToAbsolute(viewportRow, viewportCol, viewport);
  }

  public record TranslationResult(
      int viewportRow,
      int viewportCol,
      int absoluteRow,
      int absoluteCol,
      int boundedRow,
      int boundedCol,
      boolean rowTruncated,
      boolean colTruncated) {

    public boolean wasTruncated() {
      return rowTruncated || colTruncated;
    }

    public String getTruncationMessage() {
      if (!wasTruncated()) {
        return "No truncation needed";
      }

      StringBuilder message = new StringBuilder("Coordinates truncated: ");
      if (rowTruncated) {
        message.append(String.format("row %d -> %d, ", absoluteRow, boundedRow));
      }
      if (colTruncated) {
        message.append(String.format("col %d -> %d", absoluteCol, boundedCol));
      }
      return message.toString();
    }
  }
}