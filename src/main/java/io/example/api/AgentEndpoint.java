package io.example.api;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.Done;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.annotations.http.Put;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpException;
import akka.javasdk.http.HttpResponses;
import akka.stream.Materializer;
import io.example.application.AgentStepEntity;
import io.example.application.AgentStepView;
import io.example.application.VisualizerWorkflow;
import io.example.domain.AgentStep;
import io.example.domain.ViewPort;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/agent")
public class AgentEndpoint extends AbstractHttpEndpoint {
  final Logger log = LoggerFactory.getLogger(getClass());
  final ComponentClient componentClient;
  final Materializer materializer;

  public AgentEndpoint(ComponentClient componentClient, Materializer materializer) {
    this.componentClient = componentClient;
    this.materializer = materializer;
  }

  @Post("/voice-command")
  public CompletionStage<String> voiceCommandNext(HttpRequest request) {
    var contentType = request.entity().getContentType().toString();
    log.info("Voice command: Content-type: {}", contentType);

    if (contentType == null
        || !contentType.startsWith("multipart/form-data")
        || !(contentType.split("boundary=").length == 2)) {
      log.error("Voice command: Content-type is null or not multipart/form-data");
      throw HttpException.badRequest("Content-type must be multipart/form-data");
    }

    var viewport = viewportFromHttpHeaders(request);
    log.info("Voice command: Viewport: {}", viewport);

    var sessionId = request.getHeader("X-User-Session-Id").map(header -> header.value()).orElse("unknown");
    log.info("Voice command: User session ID: {}", sessionId);

    return request.entity().toStrict(Duration.ofSeconds(10).toMillis(), materializer)
        .thenCompose(strict -> {
          log.info("Voice command: Audio request size: {}", strict.getData().size());
          var bytes = strict.getData().toArray();
          var workflowRequest = new VisualizerWorkflow.VoiceCommandRequest(sessionId, contentType, bytes, viewport);
          var workflowId = randomWorkflowId();

          componentClient.forWorkflow(workflowId)
              .method(VisualizerWorkflow::start)
              .invoke(workflowRequest);

          return CompletableFuture.completedFuture("Processing audio command, workflow ID: " + workflowId);
        });
  }

  @Put("/agent-step-consumed")
  public Done agentStepConsumed(AgentStep.Command.ConsumedStep command) {
    log.info("Agent step consumed: {}", command);

    return componentClient.forEventSourcedEntity(command.id())
        .method(AgentStepEntity::consumeStep)
        .invoke(command);
  }

  @Get("/agent-steps-stream/{userSessionId}")
  public HttpResponse getAgentStepsStream(String userSessionId) {
    return HttpResponses.serverSentEvents(
        componentClient.forView()
            .stream(AgentStepView::getActiveAgentSteps)
            .source(userSessionId));
  }

  @Get("/agent-steps/{sequenceId}")
  public AgentStepView.AgentSteps getSequenceAgentSteps(String sequenceId) {
    return componentClient.forView()
        .method(AgentStepView::getSequenceAgentSteps)
        .invoke(sequenceId);
  }

  static ViewPort viewportFromHttpHeaders(HttpRequest request) {
    var viewportTopLeftRow = request.getHeader("X-Viewport-Top-Left-Row").map(header -> Integer.parseInt(header.value())).orElse(0);
    var viewportTopLeftCol = request.getHeader("X-Viewport-Top-Left-Col").map(header -> Integer.parseInt(header.value())).orElse(0);
    var viewportBottomRightRow = request.getHeader("X-Viewport-Bottom-Right-Row").map(header -> Integer.parseInt(header.value())).orElse(0);
    var viewportBottomRightCol = request.getHeader("X-Viewport-Bottom-Right-Col").map(header -> Integer.parseInt(header.value())).orElse(0);
    var mouseRow = request.getHeader("X-Mouse-Row").map(header -> Integer.parseInt(header.value())).orElse(0);
    var mouseCol = request.getHeader("X-Mouse-Col").map(header -> Integer.parseInt(header.value())).orElse(0);

    var topLeft = new ViewPort.Location(viewportTopLeftRow, viewportTopLeftCol);
    var bottomRight = new ViewPort.Location(viewportBottomRightRow, viewportBottomRightCol);
    var mouse = new ViewPort.Location(mouseRow, mouseCol);

    return new ViewPort(topLeft, bottomRight, mouse);
  }

  static String randomWorkflowId() {
    return new Random().ints(6, 0, 36)
        .mapToObj(i -> i < 10 ? String.valueOf(i) : String.valueOf((char) ('a' + i - 10)))
        .collect(Collectors.joining());
  }
}
