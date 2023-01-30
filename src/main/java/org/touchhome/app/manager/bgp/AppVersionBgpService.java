package org.touchhome.app.manager.bgp;

import static java.lang.String.format;
import static org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS;

import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.touchhome.app.config.TouchHomeProperties;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.bundle.api.hardware.other.MachineHardwareRepository;
import org.touchhome.bundle.api.model.ActionResponseModel;
import org.touchhome.bundle.api.ui.UI;
import org.touchhome.bundle.api.ui.field.action.v1.UIInputBuilder;
import org.touchhome.bundle.api.util.TouchHomeUtils;
import org.touchhome.common.util.Curl;

@Log4j2
@Service
@RequiredArgsConstructor
public class AppVersionBgpService implements BgpService {

    private final EntityContextImpl entityContext;
    private String latestVersion;

    @Override
    public void startUp() {
        entityContext.bgp().builder("check-app-version").interval(Duration.ofDays(1))
                     .execute(this::fetchReleaseVersion);
    }

    private void fetchReleaseVersion() {
        TouchHomeProperties touchHomeProperties = entityContext.getTouchHomeProperties();
        try {
            log.info("Try fetch latest version from server");
            entityContext.ui().addBellInfoNotification("version", "app", "version: " + touchHomeProperties.getVersion());
            this.latestVersion = Curl.get(touchHomeProperties.getGitHubUrl(), Map.class).get("tag_name").toString();

            if (!String.valueOf(touchHomeProperties.getVersion()).equals(this.latestVersion)) {
                log.info("Found newest version <{}>. Current version: <{}>", this.latestVersion, touchHomeProperties.getVersion());
                String description = "Require update app version from " + touchHomeProperties.getVersion() + " to " + this.latestVersion;
                entityContext.ui().addBellErrorNotification("version", "app", description, buildAction());
                entityContext.event().fireEventIfNotSame("app-release", this.latestVersion);
            }
        } catch (Exception ex) {
            log.warn("Unable to fetch latest version");
        }
    }

    @NotNull
    private Consumer<UIInputBuilder> buildAction() {
        return uiInputBuilder ->
            uiInputBuilder
                .addButton("handle-version", "fas fa-registered", UI.Color.PRIMARY_COLOR,
                    (entityContext, params) -> {
                        String cmd = format("%s/update.%s", TouchHomeUtils.getFilesPath(), IS_OS_WINDOWS ? "sh" : "bat");
                        String result = entityContext.getBean(MachineHardwareRepository.class).execute(cmd, 600);
                        return ActionResponseModel.showInfo(result);
                    })
                .setText("Update");
    }
}
