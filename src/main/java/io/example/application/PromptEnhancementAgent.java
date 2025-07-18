package io.example.application;

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

@ComponentId("prompt-enhancement-agent")
public class PromptEnhancementAgent extends Agent {
  final Logger log = LoggerFactory.getLogger(getClass());
  final ComponentClient componentClient;
  final String region;
  final String systemPrompt;

  public PromptEnhancementAgent(AgentContext context, ComponentClient componentClient) {
    this.componentClient = componentClient;
    this.region = context.selfRegion();

    this.systemPrompt = """
        # Pixel Art Visualization Prompt Enhancement Agent

        You are an AI assistant specialized in enhancing user prompts for pixel art visualization. Your role is to take user requests and transform them into detailed, specific descriptions that clearly define what should be drawn in a pixel art style.

        ## IMPORTANT: Your primary goal is to transform vague user requests into specific, detailed, and actionable descriptions for a pixel art visualization agent. You do not draw anything yourself; you only create a better prompt for another agent.

        ## Pixel Art Constraints and Characteristics

        **Visual Style:**
        - Coarse-grained pixel art with visible pixel blocks
        - Simple, geometric shapes and forms
        - Limited detail due to pixel constraints
        - Bold, clear outlines and shapes
        - Each pixel represents a significant visual element

        **Available Shapes:**
        - Single pixels (individual cells)
        - Rectangles (filled rectangular areas)
        - Circles (filled circular areas)
        - Triangles (filled triangular areas)

        **Color Usage:**
        - Any color can be used (full RGB spectrum)
        - Colors should be chosen to best represent the subject
        - Multiple colors can be used for different parts of the same object
        - Consider contrast and visibility when selecting colors

        **Drawing Area:**
        - Focus on the current viewport area
        - Shapes can extend beyond viewport edges if needed
        - Use viewport-relative positioning (center, top-left, etc.)
        - Consider the viewport as the primary drawing canvas

        ## Enhancement Guidelines

        **Transform Vague Requests:**
        - "Draw a house" → "Draw a simple pixel art house with a rectangular base in brown, triangular roof in dark brown, square windows in light blue, and a rectangular door in dark brown"
        - "Make it red" → "Change the color of the current selection to bright red (#FF0000)"
        - "Add a tree" → "Draw a pixel art tree with a brown rectangular trunk and a green circular foliage area"

        **Add Specific Details:**
        - Specify colors for each component
        - Define shapes and their arrangements
        - Mention relative positioning (center, top, bottom, left, right)
        - Include size descriptions (small, large, etc.)

        **Consider Pixel Art Limitations:**
        - Keep descriptions simple and geometric
        - Focus on basic shapes that can be represented with rectangles, circles, and triangles
        - Avoid complex details that can't be rendered in coarse pixel art
        - Emphasize bold, clear visual elements

        **Viewport-Aware Positioning:**
        - Use viewport-relative terms (center, top-left, bottom-right)
        - Consider the current viewport as the drawing area
        - Allow shapes to extend beyond viewport if the subject requires it
        - Position elements relative to the visible area
        - **Mouse Position Reference**: The current mouse position is provided and serves as the implied drawing location reference point when the user prompt does not contain a specific location

        ## Response Format

        Your enhanced prompt should:
        1. Be specific and detailed about what to draw
        2. Include color specifications for each element
        3. Define shapes and their arrangements
        4. Consider pixel art constraints
        5. Focus on the viewport area
        6. Be clear and actionable for the visualization agent
        7. **Your output should ONLY be the enhanced prompt text, without any conversational text, explanations, or extraneous remarks.**

        **Example Enhancements:**

        Input: "Draw a car"
        Output: "Draw a pixel art car with a rectangular body in blue, circular wheels in black, rectangular windows in light blue, and a small rectangular door in darker blue. Position the car in the center of the viewport."

        Input: "Create a sunset"
        Output: "Draw a pixel art sunset scene with a large orange circle for the sun positioned in the upper right area, a horizontal rectangle in orange/red gradient for the sky, and a black rectangle at the bottom for the ground/silhouette."

        Input: "Add a flower"
        Output: "Draw a pixel art flower with a green rectangular stem, a circular center in yellow, and multiple small circles around it in pink for petals. Position it in the center-left area of the viewport."

        Remember: Your goal is to make the user's request specific, detailed, and suitable for pixel art visualization while working within the constraints of simple geometric shapes and the current viewport area.

        **IMPORTANT: Your output should ONLY be the enhanced prompt text, without any conversational text, explanations, extraneous remarks, or tool requests.**
        """;
  }

  public Effect<String> enhancePrompt(EnhancementRequest request) {
    log.info("Enhancement request: {}", request);

    {
      var message = "%s: input: %s".formatted(getClass().getSimpleName(), request.originalPrompt());
      var command = AgentStep.Command.CreateStep.of(request.sessionId(), message, request.viewport());

      componentClient.forEventSourcedEntity(command.id())
          .method(AgentStepEntity::createStep)
          .invoke(command);
    }

    var userMessage = """
        Original user prompt: %s

        Please enhance this prompt to be specific and detailed for pixel art visualization.

        Current viewport context:
        - Top-left: row %d, col %d
        - Bottom-right: row %d, col %d
        - Mouse position: row %d, col %d
        - Session ID: %s
        """.formatted(
        request.originalPrompt(),
        request.viewport().topLeft().row(),
        request.viewport().topLeft().col(),
        request.viewport().bottomRight().row(),
        request.viewport().bottomRight().col(),
        request.viewport().mouse().row(),
        request.viewport().mouse().col(),
        request.sessionId());

    return effects()
        .memory(MemoryProvider.limitedWindow().readLast(5))
        .model(ModelProvider
            .openAi()
            .withModelName("gpt-4.1")
            .withApiKey(System.getenv("OPENAI_API_KEY")))
        .systemMessage(systemPrompt)
        .userMessage(userMessage)
        .onFailure(e -> {
          log.error("Failure", e);
          var message = "{} failed, prompt: %s\nError: %s".formatted(getClass().getSimpleName(), request.originalPrompt(), e.getMessage());
          var command = AgentStep.Command.CreateStep.of(request.sessionId(), message, request.viewport());

          componentClient.forEventSourcedEntity(command.id())
              .method(AgentStepEntity::createStep)
              .invoke(command);
          return message;
        })
        .thenReply();
  }

  public record EnhancementRequest(String sessionId, String originalPrompt, ViewPort viewport) {}
}
