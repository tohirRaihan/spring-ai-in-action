package com.example.vectorstore_loader.config;

import com.example.vectorstore_loader.VectorStoreLoaderApplication;
import com.example.vectorstore_loader.dto.GameTitle;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@Configuration
public class Config {

  private static final Logger LOGGER = LoggerFactory.getLogger(VectorStoreLoaderApplication.class);

  @Value("classpath:/promptTemplates/nameOfTheGame.st")
  Resource nameOfTheGameTemplateResource;

  @Bean
  Function<Flux<byte[]>, Flux<Document>> documentReader() {
    return resourceFlux ->
        resourceFlux
            .map(fileBytes ->
                new TikaDocumentReader(new ByteArrayResource(fileBytes)).get().getFirst())
            .subscribeOn(Schedulers.boundedElastic());
  }

  @Bean
  Function<Flux<Document>, Flux<List<Document>>> splitter() {
    TextSplitter splitter =
        TokenTextSplitter.builder()
            .withChunkSize(800)
            .withMinChunkSizeChars(350)
            .withMinChunkLengthToEmbed(5)
            .withKeepSeparator(true)
            .build();
    return documentFlux ->
        documentFlux
            .map(incoming -> splitter.apply(List.of(incoming)))
            .subscribeOn(Schedulers.boundedElastic());
  }

  @Bean
  Function<Flux<List<Document>>, Flux<List<Document>>> titleDeterminer(
      ChatClient.Builder chatClientBuilder) {

    var chatClient = chatClientBuilder.build();

    return documentListFlux ->
        documentListFlux.map(documents -> {
          if (!documents.isEmpty()) {
            var firstDocument = documents.getFirst();

            var gameTitle =
                chatClient
                    .prompt()
                    .user(userSpec ->
                        userSpec
                            .text(nameOfTheGameTemplateResource)
                            .param("document", firstDocument.getText()))
                    .call()
                    .entity(GameTitle.class);

            if (Objects.requireNonNull(gameTitle).title().equals("UNKNOWN")) {
              LOGGER.warn(
                  "Unable to determine the name of a game; " + "not adding to vector store.");
              documents = Collections.emptyList();
              return documents;
            }

            LOGGER.info("Determined game title to be {}", gameTitle.title());
            documents =
                documents.stream()
                    .peek(document -> {
                      document.getMetadata().put("gameTitle", gameTitle.getNormalizedTitle());
                    })
                    .toList();
          }

          return documents;
        });
  }

  @Bean
  Consumer<Flux<List<Document>>> vectorStoreConsumer(VectorStore vectorStore) {
    return documentFlux ->
        documentFlux
            .doOnNext(documents -> {
              if (!documents.isEmpty()) {
                var docCount = documents.size();
                LOGGER.info("Writing {} documents to vector store.", docCount);

                vectorStore.accept(documents);

                LOGGER.info("{} documents have been written to vector store.", docCount);
              }
            })
            .subscribe();
  }

  @Bean
  ApplicationRunner go(FunctionCatalog catalog) {
    Runnable composedFunction = catalog.lookup(null);
    return args -> {
      composedFunction.run();
    };
  }
}
