package com.example.boardgamebuddy.service;

import com.example.boardgamebuddy.dto.Answer;
import com.example.boardgamebuddy.dto.Question;

public interface BoardGameService {
  Answer askQuestion(Question question);
}
