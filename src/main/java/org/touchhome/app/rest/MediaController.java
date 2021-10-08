package org.touchhome.app.rest;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.rossillo.spring.web.mvc.CacheControl;
import net.rossillo.spring.web.mvc.CachePolicy;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.touchhome.app.audio.AudioService;
import org.touchhome.app.manager.ImageService;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.audio.AudioSink;
import org.touchhome.bundle.api.audio.SelfContainedAudioSourceContainer;
import org.touchhome.bundle.api.entity.ImageEntity;
import org.touchhome.bundle.api.model.OptionModel;
import org.touchhome.bundle.api.setting.SettingPluginOptionsFileExplorer;
import org.touchhome.bundle.api.util.TouchHomeUtils;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

@Log4j2
@Controller
@RequestMapping("/rest/media")
@RequiredArgsConstructor
public class MediaController {

    private final ImageService imageService;
    private final EntityContext entityContext;
    private final AudioService audioService;

    @GetMapping("/audio/{streamId}/play")
    public void playAudioFile(@PathVariable String streamId, HttpServletResponse resp) throws IOException {
        audioService.playRequested(streamId, resp);
    }

    @GetMapping("/image/{imagePath:.+}")
    @CacheControl(maxAge = 3600, policy = CachePolicy.PUBLIC)
    @ResponseBody
    public ResponseEntity<InputStreamResource> getImage(@PathVariable String imagePath) {
        ImageEntity imageEntity = entityContext.getEntity(imagePath);
        if (imageEntity != null) {
            return getImage(imageEntity.toPath().toString());
        } else {
            return imageService.getImage(imagePath);
        }
    }

    @GetMapping("/audio")
    @ResponseBody
    public Collection<OptionModel> getAudioFiles() {
        return SettingPluginOptionsFileExplorer.getFilePath(TouchHomeUtils.getAudioPath(), 7, true,
                false, false, null, null,
                null, (path, basicFileAttributes) -> path.endsWith(".mp3"), null,
                path -> path.getFileName() == null ? path.toString() : path.getFileName().toString());
    }

    @GetMapping("/audioSource")
    @ResponseBody
    public Collection<OptionModel> audioSource() {
        Collection<OptionModel> optionModels = new ArrayList<>();
        for (SelfContainedAudioSourceContainer audioSourceContainer : audioService.getAudioSourceContainers()) {
            String label = audioSourceContainer.getLabel();
            if (label == null) {
                throw new IllegalStateException("SelfContainedAudioSource must return not null label");
            }
            OptionModel optionModel = OptionModel.key(label);
            for (OptionModel source : audioSourceContainer.getAudioSource()) {
                optionModel.addChild(source);
            }

            optionModels.add(optionModel);
        }

        return optionModels;
    }

    @GetMapping("/sink")
    @ResponseBody
    public Collection<OptionModel> getAudioSink() {
        Collection<OptionModel> models = new ArrayList<>();
        for (AudioSink audioSink : audioService.getAudioSinks().values()) {
            for (Map.Entry<String, String> entry : audioSink.getSources().entrySet()) {
                models.add(OptionModel.of(entry.getKey(), entry.getValue()));
            }
        }
        return models;
    }
}
