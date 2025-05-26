package io.example.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LLMResponseParser {
  private static final ObjectMapper objectMapper = new ObjectMapper();

  // Pattern to match JSON code blocks
  private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("```json\\s*\\n([\\s\\S]*?)\\n```", Pattern.MULTILINE);

  // Pattern to match raw JSON objects/arrays outside code blocks
  private static final Pattern RAW_JSON_PATTERN = Pattern.compile("(\\{[^{}]*(?:\\{[^{}]*\\}[^{}]*)*\\}|\\[[^\\[\\]]*(?:\\[[^\\[\\]]*\\][^\\[\\]]*)*\\])",
      Pattern.MULTILINE);

  /**
   * Extracts all JSON commands from the LLM response text. Handles single objects, arrays, and multiple separate JSON blocks.
   *
   * @param llmResponse The raw response text from the LLM
   * @return List of JSON command strings
   */
  public static List<String> extractJsonCommands(String llmResponse) {
    List<String> commands = new ArrayList<>();

    // First, try to extract from JSON code blocks
    Matcher codeBlockMatcher = JSON_BLOCK_PATTERN.matcher(llmResponse);

    while (codeBlockMatcher.find()) {
      String jsonContent = codeBlockMatcher.group(1).trim();
      processJsonContent(jsonContent, commands);
    }

    // If no code blocks found, try to extract raw JSON
    if (commands.isEmpty()) {
      Matcher rawJsonMatcher = RAW_JSON_PATTERN.matcher(llmResponse);
      while (rawJsonMatcher.find()) {
        String jsonContent = rawJsonMatcher.group(0).trim();
        processJsonContent(jsonContent, commands);
      }
    }

    return commands;
  }

  /**
   * Processes JSON content and adds individual commands to the list. Handles both single objects and arrays.
   */
  private static void processJsonContent(String jsonContent, List<String> commands) {
    try {
      JsonNode node = objectMapper.readTree(jsonContent);

      if (node.isArray()) {
        // It's an array of commands
        for (JsonNode command : node) {
          commands.add(command.toString());
        }
      } else if (node.isObject()) {
        // It's a single command
        commands.add(node.toString());
      }
    } catch (Exception e) {
      // Log error or handle invalid JSON
      System.err.println("Failed to parse JSON: " + e.getMessage());
    }
  }

  /**
   * Parses the extracted JSON commands into Command objects.
   *
   * @param jsonCommands List of JSON command strings
   * @return List of Command objects
   */
  public static List<Command> parseCommands(List<String> jsonCommands) {
    List<Command> commands = new ArrayList<>();

    for (String jsonCommand : jsonCommands) {
      try {
        Command command = objectMapper.readValue(jsonCommand, Command.class);
        commands.add(command);
      } catch (Exception e) {
        System.err.println("Failed to parse command: " + e.getMessage());
      }
    }

    return commands;
  }

  // Command class to represent the JSON structure
  public static class Command {
    private String tool;
    private JsonNode parameters;
    private String message;

    // Getters and setters
    public String getTool() {
      return tool;
    }

    public void setTool(String tool) {
      this.tool = tool;
    }

    public JsonNode getParameters() {
      return parameters;
    }

    public void setParameters(JsonNode parameters) {
      this.parameters = parameters;
    }

    public String getMessage() {
      return message;
    }

    public void setMessage(String message) {
      this.message = message;
    }

    @Override
    public String toString() {
      return "Command{tool='" + tool + "', parameters=" + parameters + ", message='" + message + "'}";
    }
  }

  // Example usage
  public static void main(String[] args) {
    {
      String llmResponse = """
          Now, I'll perform these actions in sequence:
          ```json
          {
            "tool": "moveViewport",
            "parameters": {
              "direction": "left",
              "amount": 100
            },
            "message": "Moving the viewport left by 100 units."
          }
          ```
          ```json
          {
            "tool": "drawCircle",
            "parameters": {
              "x": -44,
              "y": 30,
              "status": "green",
              "radius": 10
            },
            "message": "Drawing a green circle with a radius of 10 at the center of the screen."
          }
          ```
          """;

      // Extract JSON strings
      var jsonCommands = extractJsonCommands(llmResponse);
      System.out.println("Found " + jsonCommands.size() + " commands");

      // Parse into Command objects
      var commands = parseCommands(jsonCommands);
      commands.forEach(cmd -> System.out.println(cmd));
    }

    {
      String llmResponse = """
          Now, I'll perform these actions in sequence:
          json
          {
            "tool": "moveViewport",
            "parameters": {
              "direction": "left",
              "amount": 100
            },
            "message": "Moving the viewport left by 100 units."
          }

          json
          {
            "tool": "drawCircle",
            "parameters": {
              "x": -44,
              "y": 30,
              "status": "green",
              "radius": 10
            },
            "message": "Drawing a green circle with a radius of 10 at the center of the screen."
          }

          json
          {
            "tool": "createPredator",
            "parameters": {
              "x": 10,
              "y": 50
            },
            "message": "Creating a predator at coordinates (10, 50)."
          }
          """;

      // Extract JSON strings
      var jsonCommands = extractJsonCommands(llmResponse);
      System.out.println("Found " + jsonCommands.size() + " commands");
      System.out.println("Commands: " + jsonCommands);

      // Parse into Command objects
      var commands = parseCommands(jsonCommands);
      commands.forEach(cmd -> System.out.println(cmd));
    }
  }
}
