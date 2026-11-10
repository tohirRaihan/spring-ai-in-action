package com.example.boardgamebuddy.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.Charset;

@Service
public class GameRulesService {

  private static final Logger LOG = LoggerFactory.getLogger(GameRulesService.class);

  public String getRulesFor(String gameName) {
    try {
      var filename = String.format("classpath:/gameRules/%s.txt",
          gameName.toLowerCase().replace(" ", "_"));

      return new DefaultResourceLoader().getResource(filename)
          .getContentAsString(Charset.defaultCharset());
    } catch (IOException e) {
      LOG.info("No rules found for game: " + gameName);
      return "";
    }
  }

}
