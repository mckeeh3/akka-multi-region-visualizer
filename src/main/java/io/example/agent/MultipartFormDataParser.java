package io.example.agent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MultipartFormDataParser {
  static final Logger log = LoggerFactory.getLogger(MultipartFormDataParser.class);
  final String boundary;
  final InputStream input;
  final Map<String, byte[]> parts = new HashMap<>();

  public MultipartFormDataParser(String contentType, InputStream input) {
    // Extract boundary from content type
    log.debug("Content type: {}", contentType);
    var parts = contentType.split("boundary=");
    if (parts.length != 2) {
      throw new IllegalArgumentException("Invalid content type: " + contentType);
    }
    // Remove any quotes and whitespace from boundary
    this.boundary = "--" + parts[1].trim().replace("\"", "");
    this.input = input;
    log.debug("Boundary: {}", this.boundary);
  }

  public void parse() throws IOException {
    log.debug("Starting to parse multipart data with boundary: {}", boundary);

    // Skip to the first boundary
    var line = "";
    var foundFirstBoundary = false;

    // Read lines until we find the first boundary
    while ((line = readLine(input)) != null) {
      if (line.startsWith(boundary)) {
        foundFirstBoundary = true;
        log.debug("Found first boundary: {}", line);
        break;
      }
    }

    if (!foundFirstBoundary) {
      log.warn("Could not find first boundary, parsing will fail");
      return;
    }

    // Process parts
    var partCount = 0;
    var done = false;

    while (!done) {
      // Read headers
      Map<String, String> headers = new HashMap<>();
      var headerLine = "";
      var currentHeaderName = "";
      var headerValue = new StringBuilder();

      // Read headers until empty line
      while ((headerLine = readLine(input)) != null && !headerLine.isEmpty()) {
        log.debug("Header line: {}", headerLine);

        if (headerLine.startsWith(" ") || headerLine.startsWith("\t")) {
          // Continuation of previous header
          if (currentHeaderName != null) {
            headerValue.append(" ").append(headerLine.trim());
            headers.put(currentHeaderName, headerValue.toString());
          }
        } else {
          // New header
          int colonPos = headerLine.indexOf(':');
          if (colonPos > 0) {
            currentHeaderName = headerLine.substring(0, colonPos).trim().toLowerCase();
            headerValue = new StringBuilder(headerLine.substring(colonPos + 1).trim());
            headers.put(currentHeaderName, headerValue.toString());
          }
        }
      }

      if (headers.isEmpty()) {
        log.warn("No headers found for part");
        break;
      }

      // Parse Content-Disposition header to get field name
      var contentDisposition = headers.get("content-disposition");
      var fieldName = "";
      var isFile = false;

      if (contentDisposition != null) {
        log.debug("Content-Disposition: {}", contentDisposition);

        // Extract field name
        int nameStart = contentDisposition.indexOf("name=\"");
        if (nameStart >= 0) {
          nameStart += 6; // Skip past name="
          int nameEnd = contentDisposition.indexOf('"', nameStart);
          if (nameEnd > nameStart) {
            fieldName = contentDisposition.substring(nameStart, nameEnd);
          }
        }

        // Check if this is a file
        isFile = contentDisposition.contains("filename=\"");
      }

      if (fieldName == "") {
        log.warn("Could not determine field name from headers");
        fieldName = isFile ? "file" : "unknown";
      }

      log.debug("Processing part with name: {}", fieldName + (isFile ? " (file)" : ""));

      // Read part data until next boundary
      var partData = new ByteArrayOutputStream();
      var buffer = new byte[8192];
      var foundBoundary = false;
      var lineBuffer = new StringBuilder();

      // Read data until we find a boundary
      while (input.read(buffer, 0, 1) > 0) {
        byte b = buffer[0];

        // Build up line to check for boundary
        if (b == '\r' || b == '\n') {
          lineBuffer.append((char) b);

          // Check if we have a complete line
          if ((b == '\n') || (lineBuffer.length() >= 2 && lineBuffer.charAt(lineBuffer.length() - 2) == '\r')) {
            var potentialBoundary = lineBuffer.toString().trim();

            // Check if this is a boundary
            if (potentialBoundary.equals(boundary) || potentialBoundary.equals(boundary + "--")) {
              foundBoundary = true;
              done = potentialBoundary.equals(boundary + "--"); // End boundary

              if (done) {
                log.debug("Found end boundary");
              } else {
                log.debug("Found part boundary");
              }

              // Remove the boundary line from the part data
              byte[] fullData = partData.toByteArray();
              int dataLength = fullData.length;

              // Find where the boundary starts (CRLF before boundary)
              int boundaryStart = dataLength - lineBuffer.length();
              if (boundaryStart >= 0) {
                // Trim the boundary from the data
                partData = new ByteArrayOutputStream();
                partData.write(fullData, 0, boundaryStart);
              }

              break;
            }

            // Not a boundary, reset line buffer
            lineBuffer = new StringBuilder();
          }
        } else {
          // Regular character, add to line buffer and part data
          lineBuffer.append((char) b);
        }

        // Add byte to part data
        partData.write(b);
      }

      if (!foundBoundary) {
        log.warn("Reached end of input without finding boundary");
        break;
      }

      // Store the part
      var partBytes = partData.toByteArray();

      // Trim any trailing CRLF
      while (partBytes.length > 0 && (partBytes[partBytes.length - 1] == '\n' || partBytes[partBytes.length - 1] == '\r')) {
        partBytes = java.util.Arrays.copyOf(partBytes, partBytes.length - 1);
      }

      parts.put(fieldName, partBytes);
      log.debug("Saved part with name: {}, size: {} bytes", fieldName, partBytes.length);
      partCount++;

      if (done) {
        break;
      }
    }

    // Log summary of parsing results
    log.debug("Multipart parsing completed. Found {}, Parts map contains {} entries", partCount, parts.size());
    for (String key : parts.keySet()) {
      log.debug("Part found: {}, size: {} bytes", key, parts.get(key).length);
    }
  }

  String readLine(InputStream input) throws IOException {
    var line = new StringBuilder();
    var b = 0;

    while ((b = input.read()) != -1) {
      line.append((char) b);

      // Check for end of line
      if (b == '\n') {
        break;
      }
    }

    return line.length() > 0 ? line.toString().trim() : null;
  }

  public byte[] getFile() {
    // Try to get the audio file with various possible field names
    var possibleNames = new String[] { "audio", "file", "audioFile", "recording", "voice" };

    for (String name : possibleNames) {
      var data = parts.get(name);
      if (data != null && data.length > 0) {
        log.debug("Found audio file with field name: {}, size: {} bytes", name, data.length);
        return data;
      }
    }

    // If no specific named field is found, try to find any field that might be the audio file
    // (typically the largest binary field)
    if (!parts.isEmpty()) {
      var largestKey = "";
      var maxSize = 0;

      for (Map.Entry<String, byte[]> entry : parts.entrySet()) {
        if (entry.getValue().length > maxSize) {
          maxSize = entry.getValue().length;
          largestKey = entry.getKey();
        }
      }

      if (largestKey != "" && maxSize > 0) {
        log.debug("Using largest field as audio file: {}, size: {} bytes", largestKey, maxSize);
        return parts.get(largestKey);
      }
    }

    log.warn("No audio file found in any part of the multipart request");
    return null;
  }
}
