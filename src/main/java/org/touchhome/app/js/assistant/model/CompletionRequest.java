package org.touchhome.app.js.assistant.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompletionRequest {

  private String line;
  private String scriptEntityID;
  private String allScript;
}
