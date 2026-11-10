package com.example.boardgamebuddy.controller;

import com.example.boardgamebuddy.dto.Answer;
import com.example.boardgamebuddy.dto.Question;
import com.example.boardgamebuddy.service.BoardGameService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AskController {

  private final BoardGameService boardGameService;

  @PostMapping(path = "/ask", produces = "application/json")
  public Answer ask(@Valid @RequestBody Question question) {
    return boardGameService.askQuestion(question);
  }

  @GetMapping(path = "/health", produces = "application/json")
  public String health() {
    return "OK";
  }

}
