package org.homio.addon.media;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.homio.api.Context;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.media.HasTextToSpeech;
import org.homio.api.service.TextToSpeechEntityService;
import org.homio.api.state.RawType;
import org.homio.api.workspace.WorkspaceBlock;
import org.homio.api.workspace.scratch.MenuBlock;
import org.homio.api.workspace.scratch.Scratch3ExtensionBlocks;
import org.springframework.stereotype.Component;

@Log4j2
@Getter
@Component
public class Scratch3TextToSpeachBlocks extends Scratch3ExtensionBlocks {

  private final MenuBlock.ServerMenuBlock ttsMenu;

  public Scratch3TextToSpeachBlocks(Context context) {
    super("#2278A3", context, null, "tts");
    setParent(ScratchParent.media);

    this.ttsMenu = menuServerItems("tts", HasTextToSpeech.class, "Select TTS");

    blockReporter(
      10,
      "tts",
      "text [VALUE] to audio [TTS]",
      this::getTextToAudioReporter,
      block -> {
        block.addArgument(VALUE, "Hello world");
        block.addArgument("TTS", this.ttsMenu);
      });
  }

  private RawType getTextToAudioReporter(WorkspaceBlock workspaceBlock) {
    String text = workspaceBlock.getInputString(VALUE);
    if (!text.isEmpty()) {
      BaseEntity entity = workspaceBlock.getMenuValueEntityRequired("TTS", this.ttsMenu);
      if (entity instanceof HasTextToSpeech<?> tts) {
        TextToSpeechEntityService ttsService = tts.getService();
        return new RawType(ttsService.synthesizeSpeech(text, true), "audio/mp3");
      }
    }
    return null;
  }
}
