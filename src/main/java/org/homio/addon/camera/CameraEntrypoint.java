package org.homio.addon.camera;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.camera.entity.BaseVideoEntity;
import org.homio.addon.camera.scanner.OnvifCameraHttpScanner;
import org.homio.addon.camera.setting.CameraAutorunIntervalSetting;
import org.homio.api.AddonEntrypoint;
import org.homio.api.EntityContext;
import org.homio.api.EntityContextUI.NotificationInfoLineBuilder;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.Icon;
import org.homio.api.ui.UI.Color;
import org.homio.api.ui.field.action.v1.layout.UILayoutBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class CameraEntrypoint implements AddonEntrypoint {

    private final EntityContext entityContext;

    @SneakyThrows
    public void init() {
        entityContext.ui().addNotificationBlockOptional("CAMERA", "CAMERA", new Icon("fas fa-video", "#367387"));

        entityContext.event().runOnceOnInternetUp("scan-cameras", () -> {
            // fire rescan whole possible items to see if ip address has been changed
            entityContext.getBean(OnvifCameraHttpScanner.class).executeScan(entityContext, null, null, true);
        });

       /* TODO: for (BaseVideoEntity cameraEntity : entityContext.findAll(BaseVideoEntity.class)) {
            cameraEntity.getService().startOrStopService(cameraEntity);
        }*/

        entityContext.setting().listenValueAndGet(CameraAutorunIntervalSetting.class, "cam-autorun", interval -> {
            entityContext.bgp().builder("camera-autorun").cancelOnError(false)
                         .interval(Duration.ofMinutes(interval))
                         .execute(this::fireStartCamera);
        });
    }

    public static void updateCamera(
        @NotNull EntityContext entityContext, BaseVideoEntity entity,
        @Nullable Supplier<String> titleSupplier,
        @NotNull Icon icon,
        @Nullable Consumer<UILayoutBuilder> settingsBuilder) {
        entityContext.ui().updateNotificationBlock("CAMERA", builder -> {
            String text = titleSupplier == null ? entity.getTitle() : titleSupplier.get();
            NotificationInfoLineBuilder info = builder.addInfo(text, icon);
            if (!entity.isStart()) {
                info.setTextColor(Color.WARNING);
            } else if (entity.getStatus().isOnline()) {
                info.setTextColor(Color.GREEN);
            }
            info.setStatus(entity).setAsLink(entity);
            if (!entity.isStart() || settingsBuilder == null) {
                if (!entity.isStart()) {
                    info.setRightButton(new Icon("fas fa-play"), "START", null, (ec, params) -> {
                        ec.save(entity.setStart(true));
                        return ActionResponseModel.fired();
                    });
                }
            } else {
                info.setRightSettingsButton(settingsBuilder);
            }
        });
    }

    private void fireStartCamera() {
        for (BaseVideoEntity cameraEntity : entityContext.findAll(BaseVideoEntity.class)) {
            if (!cameraEntity.isStart() && cameraEntity.isAutoStart()) {
                entityContext.save(cameraEntity.setStart(true)); // start=true is a trigger to start camera
            }
        }
    }
}
