package com.example.boardgamebuddy;

import com.example.boardgamebuddy.dto.Answer;
import com.example.boardgamebuddy.dto.Question;
import com.example.boardgamebuddy.service.BoardGameService;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.ai.ollama.chat.model=test-model")
@ExtendWith(OutputCaptureExtension.class)
class LogbookIntegrationTests {

  private static final String ANSWER = "Draw a card at the start of your turn.";
  private static final String QUESTION = "What happens at the start of my turn?";
  private static final String REQUEST_BODY = """
      {"gameTitle":"Burger Battle","question":"%s"}
      """.formatted(QUESTION);
  private static final StubResponse SUCCESS = new StubResponse(200, """
      {
        "model":"test-model",
        "created_at":"2026-01-01T00:00:00Z",
        "message":{"role":"assistant","content":"%s"},
        "done":true,
        "prompt_eval_count":10,
        "eval_count":10
      }
      """.formatted(ANSWER));
  private static final AtomicReference<StubResponse> OLLAMA_RESPONSE =
      new AtomicReference<>(SUCCESS);
  private static final BlockingQueue<String> OLLAMA_REQUESTS = new LinkedBlockingQueue<>();
  private static final HttpServer OLLAMA = startOllama();

  @LocalServerPort
  private int port;

  @Autowired
  private JsonMapper jsonMapper;

  @Autowired
  private BoardGameService boardGameService;

  @DynamicPropertySource
  static void ollamaProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.ai.ollama.base-url",
        () -> "http://127.0.0.1:" + OLLAMA.getAddress().getPort());
  }

  @BeforeEach
  void resetOllama() {
    OLLAMA_RESPONSE.set(SUCCESS);
    OLLAMA_REQUESTS.clear();
  }

  @AfterAll
  static void stopOllama() {
    OLLAMA.stop(0);
  }

  @Test
  void logsBothDirectionsOnceAndPreservesBodies(CapturedOutput output) throws Exception {
    var response = ask(REQUEST_BODY);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(jsonMapper.readValue(response.body(), Answer.class))
        .isEqualTo(new Answer("Burger Battle", ANSWER));
    assertThat(OLLAMA_REQUESTS).hasSize(1);
    var ollamaRequest = jsonMapper.readTree(OLLAMA_REQUESTS.element());
    assertThat(ollamaRequest.path("model").asString()).isEqualTo("test-model");
    assertThat(ollamaRequest.path("stream").asBoolean()).isFalse();
    assertThat(ollamaRequest.path("messages").get(0).path("content").asString())
        .contains(QUESTION);

    await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
      assertThat(output.getOut())
          .containsOnlyOnce("Incoming Request:")
          .containsOnlyOnce("Outgoing Request:")
          .containsOnlyOnce("Incoming Response:")
          .containsOnlyOnce("Outgoing Response:")
          .containsSubsequence("Incoming Request:",
              "POST http://127.0.0.1:" + port + "/ask HTTP/1.1", QUESTION,
              "Outgoing Request:", "/api/chat HTTP/1.1", QUESTION,
              "Incoming Response:", "HTTP/1.1 200", ANSWER,
              "Outgoing Response:", "HTTP/1.1 200", ANSWER)
          .doesNotContain("Unable to log");
    });
  }

  @Test
  void logsIncomingValidationErrorAndPreservesErrorBody(CapturedOutput output) throws Exception {
    var response = ask("""
        {"gameTitle":"Burger Battle","question":""}
        """);

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.body()).contains("Validation failed", "Question is required");
    assertThat(OLLAMA_REQUESTS).isEmpty();

    await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
      assertThat(output.getOut())
          .containsOnlyOnce("Incoming Request:")
          .containsOnlyOnce("Outgoing Response:")
          .contains("HTTP/1.1 400", "Validation failed", "Question is required")
          .doesNotContain("Outgoing Request:", "Incoming Response:", "Unable to log");
    });
  }

  @Test
  void logsOutgoingOllamaErrorAndPreservesErrorBody(CapturedOutput output) {
    OLLAMA_RESPONSE.set(new StubResponse(400, "{\"error\":\"Unknown model\"}"));

    assertThatThrownBy(() -> boardGameService.askQuestion(new Question("Burger Battle", QUESTION)))
        .isInstanceOf(NonTransientAiException.class)
        .hasMessageContaining("400")
        .hasMessageContaining("Unknown model");
    assertThat(OLLAMA_REQUESTS).hasSize(1);
    assertThat(output.getOut())
        .containsOnlyOnce("Outgoing Request:")
        .containsOnlyOnce("Incoming Response:")
        .contains("/api/chat HTTP/1.1", "HTTP/1.1 400", "{\"error\":\"Unknown model\"}")
        .doesNotContain("Incoming Request:", "Outgoing Response:", "Unable to log");
  }

  private HttpResponse<String> ask(String body) throws IOException, InterruptedException {
    try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
      var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/ask"))
          .timeout(Duration.ofSeconds(10))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(body))
          .build();
      return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
  }

  private static HttpServer startOllama() {
    try {
      var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/api/chat", exchange -> {
        try (exchange) {
          OLLAMA_REQUESTS.add(new String(exchange.getRequestBody().readAllBytes(), UTF_8));
          var response = OLLAMA_RESPONSE.get();
          var body = response.body().getBytes(UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(response.status(), body.length);
          exchange.getResponseBody().write(body);
        }
      });
      server.start();
      return server;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private record StubResponse(int status, String body) {
  }
}
