package com.example.vectorstore_loader.dto;

public record GameTitle(String title) {

  public String getNormalizedTitle() {
    return title.toLowerCase().replace(" ", "_");
  }
}
