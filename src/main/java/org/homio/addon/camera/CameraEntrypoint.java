package org.homio.addon.camera;

import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.camera.entity.BaseCameraEntity;
import org.homio.addon.camera.scanner.OnvifCameraHttpScanner;
import org.homio.api.AddonEntrypoint;
import org.homio.api.Context;
import org.homio.api.ContextUI.NotificationInfoLineBuilder;
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

    private final Context context;

    @SneakyThrows
    public void init() {
        context.event().runOnceOnInternetUp("scan-cameras", () -> {
            // fire rescan whole possible items to see if ip address has been changed
            context.getBean(OnvifCameraHttpScanner.class).executeScan(context, null, null);
        });
    }

    public static void updateCamera(
        @NotNull Context context,
            @NotNull BaseCameraEntity<?, ?> entity,
            @Nullable Supplier<String> titleSupplier,
            @NotNull Icon icon,
            @Nullable Consumer<UILayoutBuilder> settingsBuilder) {
        context.ui().notification().addOrUpdateBlock("CAMERA", "CAMERA", new Icon("fas fa-video", "#367387"),
                builder -> {
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
                                ec.db().save(entity.setStart(true));
                                return ActionResponseModel.fired();
                            });
                        }
                    } else {
                        info.setRightSettingsButton(settingsBuilder);
                    }
                });
    }
}
