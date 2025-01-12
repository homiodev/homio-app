package org.homio.app.utils;

import lombok.extern.log4j.Log4j2;
import org.homio.api.Context;
import org.homio.api.model.Icon;
import org.homio.api.repository.GitHubProject;
import org.homio.api.ui.UI;
import org.homio.api.util.CommonUtils;
import org.homio.api.util.FlowMap;
import org.homio.api.util.Lang;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.setting.system.SystemLogoutButtonSetting;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Log4j2
public final class NotificationUtils {

  private static final long START_TIME = System.currentTimeMillis();
  private static final GitHubProject appGitHub = GitHubProject.of("homiodev", "homio-app", CommonUtils.getInstallPath().resolve("homio"))
    .setInstalledVersionResolver((context, gitHubProject) -> context.setting().getApplicationVersion());

  public static void addAppNotifications(ContextImpl context) {
    context.bgp().builder("app-version").interval(Duration.ofDays(1)).delay(Duration.ofSeconds(1))
      .execute(() -> updateAppNotificationBlock(context));
    context.event().runOnceOnInternetUp("app-version", () -> updateAppNotificationBlock(context));
    context.event().onInternetStatusChanged("app", ignore -> updateAppNotificationBlock(context));
    updateAppNotificationBlock(context);
  }

  public static void addUserNotificationBlock(Context context, String entityID, String email, boolean replace) {
    String key = "user-" + entityID;
    if (!replace && context.ui().notification().isHasBlock(key)) {
      return;
    }
    context.ui().notification().addBlock(key, email, new Icon("fas fa-user", "#AAAC2C"), builder ->
      builder.visibleForUser(email)
        .linkToEntity(context.db().getRequire(entityID))
        .setBorderColor("#AAAC2C")
        .addInfo(key, null, "")
        .setRightButton(new Icon("fas fa-right-from-bracket"), "W.INFO.LOGOUT", (ignore, params) -> {
          context.setting().setValue(SystemLogoutButtonSetting.class, new JSONObject());
          return null;
        }).setConfirmMessage("W.CONFIRM.LOGOUT"));
  }

  private static void updateAppNotificationBlock(ContextImpl context) {
    context.ui().notification().addBlock("app", "App", new Icon("fas fa-house", "#E65100"), builder -> {
      builder.setBorderColor("#FF4400");
      String installedVersion = appGitHub.getInstalledVersion(context);
      builder.setUpdating(appGitHub.isUpdating());
      builder.setVersion(installedVersion);
      String latestVersion = appGitHub.getLastReleaseVersion();
      if (!Objects.equals(installedVersion, latestVersion)) {
        builder.setUpdatable(
          (progressBar, version) -> appGitHub.updateProject("homio", progressBar, false, projectUpdate -> {
            Path jarLocation = Paths.get(context.setting().getEnvRequire("appPath", String.class, CommonUtils.getRootPath().toString(), true));
            Path archiveAppPath = jarLocation.resolve("homio-app.zip");
            Files.deleteIfExists(archiveAppPath);
            try {
              projectUpdate.downloadReleaseFile(version, archiveAppPath.getFileName().toString(), archiveAppPath);
              context.ui().dialog().reloadWindow("Finish update", 200);
              log.info("Exit app to restart it after update");
            } catch (Exception ex) {
              log.error("Unable to download homio app", ex);
              Files.deleteIfExists(archiveAppPath);
              return null;
            }
            HardwareUtils.exitApplication(context.getApplicationContext(), 221);
            return null;
          }, null),
          appGitHub.getReleasesSince(installedVersion, false));
      }
      builder.fireOnFetch(() -> {
        long runDuration = TimeUnit.MILLISECONDS.toHours(System.currentTimeMillis() - START_TIME);
        String time = runDuration + "h";
        if (runDuration == 0) {
          runDuration = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - START_TIME);
          time = runDuration + "m";
        }
        String serverMsg = Lang.getServerMessage("SERVER_STARTED", FlowMap.of("VALUE",
          new SimpleDateFormat("MM/dd HH:mm").format(new Date(START_TIME)),
          "TIME", time));
        builder.addInfo("time", new Icon("fas fa-clock"), serverMsg);

        String wifiMsg = Lang.getServerMessage("INTERNET_STATUS_" + (context.event().isInternetUp() ? "UP" : "DOWN"));
        Icon wifiIcon = new Icon("fas fa-wifi", context.event().isInternetUp() ? UI.Color.GREEN : UI.Color.RED);
        builder.addInfo("net", wifiIcon, wifiMsg);
      });
    });
  }
}
