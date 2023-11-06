package org.homio.addon.firmata.arduino;

import com.fazecast.jSerialComm.SerialPort;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.homio.addon.firmata.arduino.packages.BoardPort;
import org.homio.addon.firmata.arduino.packages.Uploader;
import org.homio.addon.firmata.arduino.setting.ConsoleArduinoBoardPasswordSetting;
import org.homio.addon.firmata.arduino.setting.ConsoleArduinoProgrammerSetting;
import org.homio.addon.firmata.arduino.setting.header.ConsoleHeaderArduinoIncludeLibrarySetting;
import org.homio.addon.firmata.arduino.setting.header.ConsoleHeaderArduinoPortSetting;
import org.homio.addon.firmata.arduino.setting.header.ConsoleHeaderGetBoardsDynamicSetting;
import org.homio.addon.firmata.platform.BaseNoGui;
import org.homio.addon.firmata.platform.PreferencesData;
import org.homio.addon.firmata.platform.Sketch;
import org.homio.addon.firmata.platform.debug.RunnerException;
import org.homio.addon.firmata.platform.debug.TargetBoard;
import org.homio.addon.firmata.platform.debug.TargetPackage;
import org.homio.addon.firmata.platform.debug.TargetPlatform;
import org.homio.addon.firmata.platform.helpers.PreferencesMap;
import org.homio.addon.firmata.platform.helpers.PreferencesMapException;
import org.homio.addon.firmata.platform.packages.UserLibrary;
import org.homio.api.Context;
import org.homio.api.console.InlineLogsConsolePlugin;
import org.homio.api.exception.ServerException;
import org.homio.api.model.FileModel;
import org.homio.api.model.Icon;
import org.homio.api.model.OptionModel;
import org.homio.api.setting.SettingPluginOptions;
import org.homio.api.setting.console.header.dynamic.DynamicConsoleHeaderSettingPlugin;
import org.homio.hquery.ProgressBar;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ArduinoSketchService {

  private final Context context;
  private final InlineLogsConsolePlugin inlineLogsConsolePlugin;

  @Setter
  private Sketch sketch;

  public String build() {
    if (BaseNoGui.packages == null) {
      return null;
    }
    ProgressBar progressBar = context.ui().progress().createProgressBar("avr-build", false, null);
    return inlineLogsConsolePlugin.consoleLogUsingStdout(() -> compileSketch(progressBar), progressBar::done);
  }

  public void upload(boolean usingProgrammer) {
    if (BaseNoGui.packages == null) {
      return;
    }
    ProgressBar progressBar = context.ui().progress().createProgressBar("avr-upload", false, null);
    if (usingProgrammer) {
      PreferencesData.set("programmer", context.setting().getValue(ConsoleArduinoProgrammerSetting.class));
    }

    UploaderUtils uploaderInstance = new UploaderUtils();
    Uploader uploader = uploaderInstance.getUploaderByPreferences(false);

    if (uploader.requiresAuthorization() && !PreferencesData.has(uploader.getAuthorizationKey())) {
      String boardPassword = context.setting().getValue(ConsoleArduinoBoardPasswordSetting.class).optString("PASSWORD");
      PreferencesData.set(uploader.getAuthorizationKey(), boardPassword);
    }

    List<String> warningsAccumulator = new LinkedList<>();
    boolean success = false;
    try {
      if (!PreferencesData.has("serial.port")) {
        throw new ServerException("NO_PORT_SELECTED");
      }
      success = inlineLogsConsolePlugin.consoleLogUsingStdout(
          () -> {
            String fileName = compileSketch(progressBar);
            System.out.println("Uploading sketch...");
            progressBar.progress(20, "Uploading sketch...");
            boolean uploaded = uploaderInstance.upload(sketch, uploader, fileName, usingProgrammer, false, warningsAccumulator);
            if (uploaded) {
              System.out.println("Done uploading.");
            }
            return uploaded;
          },
          progressBar::done);
    } finally {
      if (uploader.requiresAuthorization() && !success) {
        PreferencesData.remove(uploader.getAuthorizationKey());
      }
    }
  }

  public void getBoardInfo() {
    SerialPort serialPort = context.setting().getValue(ConsoleHeaderArduinoPortSetting.class);
    if (serialPort != null) {
      List<BoardPort> ports = BaseNoGui.getDiscoveryManager().discovery();
        ports.stream().filter(p -> p.getAddress().equals(serialPort.getSystemPortName())).findAny()
             .ifPresent(boardPort -> context.ui().sendJsonMessage("BOARD_INFO", boardPort));
    } else {
      context.ui().toastr().error("NO_PORT_SELECTED");
    }
  }

  public void selectBoard(String board) {
    if (StringUtils.isNotEmpty(board)) {
      String[] values = board.split("~~~");
      TargetPackage targetPackage = BaseNoGui.packages.values().stream().filter(p -> p.getId().equals(values[0])).findAny()
          .orElseThrow(() -> new RuntimeException("NO_BOARD_SELECTED"));
      TargetPlatform targetPlatform = targetPackage.platforms().stream().filter(p -> p.getId().equals(values[1])).findAny()
          .orElseThrow(() -> new RuntimeException("NO_BOARD_SELECTED"));
      TargetBoard targetBoard = targetPlatform.getBoards().values().stream().filter(b -> b.getId().equals(values[2])).findAny()
          .orElseThrow(() -> new RuntimeException("NO_BOARD_SELECTED"));

      BaseNoGui.selectBoard(targetBoard);
      BaseNoGui.onBoardOrPortChange();

      List<DynamicConsoleHeaderSettingPlugin<?>> dynamicSettings = new ArrayList<>();
      if (!targetBoard.getMenuIds().isEmpty()) {
        for (Map.Entry<String, String> customMenuEntry : targetPlatform.getCustomMenus().entrySet()) {
          if (targetBoard.getMenuIds().contains(customMenuEntry.getKey())) {
            PreferencesMap preferencesMap = targetBoard.getMenuLabels(customMenuEntry.getKey());
            if (!preferencesMap.isEmpty()) {
              dynamicSettings.add(new BoardDynamicSettings(customMenuEntry.getKey(), customMenuEntry.getValue(), preferencesMap));
            }
          }
        }
      }
      context.setting().reloadSettings(ConsoleHeaderGetBoardsDynamicSetting.class, dynamicSettings);
      context.setting().reloadSettings(ConsoleArduinoProgrammerSetting.class);
      context.setting().reloadSettings(ConsoleHeaderArduinoIncludeLibrarySetting.class);
    }
  }

  @SneakyThrows
  public void importLibrary(UserLibrary lib, FileModel content) {
    List<String> list = lib.getIncludes();
    if (list == null) {
      File srcFolder = lib.getSrcFolder();
      String[] headers = BaseNoGui.headerListFromIncludePath(srcFolder);
      list = Arrays.asList(headers);
    }
    if (list.isEmpty()) {
      return;
    }

    StringBuilder buffer = new StringBuilder();
    for (String aList : list) {
      buffer.append("#include <");
      buffer.append(aList);
      buffer.append(">\n");
    }
    buffer.append('\n');
    buffer.append(content.getContent());
    content.setContent(buffer.toString());
  }

  private String compileSketch(ProgressBar progressBar) throws RunnerException, PreferencesMapException, IOException {
    String compileMsg = "Compiling sketch...";
    progressBar.progress(20, compileMsg);
    System.out.println(compileMsg);
    String result;
    try {
      result = new Compiler(sketch).build(value -> progressBar.progress(value, compileMsg), true);
    } catch (Exception ex) {
      System.err.println("Error while compiling sketch: " + ex.getMessage());
      throw ex;
    }
    if (result != null) {
      System.out.println("Done compiling.");
    }
    return result;
  }

  @RequiredArgsConstructor
  private static class BoardDynamicSettings implements DynamicConsoleHeaderSettingPlugin<String>, SettingPluginOptions<String> {

    private final String key;
    private final String title;
    private final PreferencesMap preferencesMap;

    @Override
    public String getKey() {
      return key;
    }

    @Override
    public Icon getIcon() {
      return new Icon(getStringIcon());
    }

    @NotNull
    private String getStringIcon() {
        return switch (key) {
            case "cpu" -> "fas fa-microchip";
            case "baud" -> "fas fa-tachometer-alt";
            case "xtal", "CrystalFreq" -> "fas fa-wave-square";
            case "eesz" -> "fas fa-flask";
            case "ResetMethod" -> "fas fa-trash-restore-alt";
            case "dbg" -> "fab fa-hubspot";
            case "lvl" -> "fas fa-level-up-alt";
            case "ip" -> "fas fa-superscript";
            case "vt" -> "fas fa-table";
            case "exception" -> "fas fa-exclamation-circle";
            case "wipe" -> "fas fa-eraser";
            case "ssl" -> "fab fa-expeditedssl";
            default -> "fas fa-wrench";
        };
    }

    @Override
    public String getTitle() {
      return title;
    }

    @Override
    public @NotNull Class<String> getType() {
      return String.class;
    }

    @Override
    public @NotNull String getDefaultValue() {
      return preferencesMap.keySet().iterator().next();
    }

    @Override
    public @NotNull Collection<OptionModel> getOptions(Context context, JSONObject params) {
      return OptionModel.list(preferencesMap);
    }

    @Override
    public int order() {
      return 0;
    }
  }
}
