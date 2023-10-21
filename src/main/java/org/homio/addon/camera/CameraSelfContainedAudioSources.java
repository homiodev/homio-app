package org.homio.addon.camera;

import java.util.ArrayList;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.camera.entity.BaseCameraEntity;
import org.homio.addon.camera.entity.IpCameraEntity;
import org.homio.addon.camera.service.IpCameraService;
import org.homio.api.Context;
import org.homio.api.audio.SelfContainedAudioSourceContainer;
import org.homio.api.model.OptionModel;
import org.onvif.ver10.schema.AudioSource;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class CameraSelfContainedAudioSources implements SelfContainedAudioSourceContainer {

    private final Context context;

    @Override
    public Collection<OptionModel> getAudioSource() {
        Collection<OptionModel> models = new ArrayList<>();
        for (BaseCameraEntity cameraEntity : context.db().findAll(BaseCameraEntity.class)) {
            // get sources from onvif audio streams
            if (cameraEntity.isStart() && cameraEntity instanceof IpCameraEntity) {
                IpCameraService service = (IpCameraService) cameraEntity.getService();
                for (AudioSource audioSource : service.getOnvifDeviceState().getMediaDevices().getAudioSources()) {
                    models.add(OptionModel.of(audioSource.getToken()));
                }
            }
        }
        return models;
    }

    @Override
    public String getLabel() {
        return "CAMERA_SOURCES";
    }

    @Override
    public void play(String entityID) {

    }
}
