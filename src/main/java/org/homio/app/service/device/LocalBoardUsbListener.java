package org.homio.app.service.device;

import lombok.*;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.homio.api.Context;
import org.homio.api.ContextUI;
import org.homio.api.model.Icon;
import org.homio.api.ui.field.action.ActionInputParameter;
import org.homio.api.util.Lang;
import org.homio.app.console.FileManagerConsolePlugin;
import org.homio.app.model.entity.LocalBoardEntity;
import org.homio.app.setting.system.SystemAutoMountUsbSetting;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;

import static org.homio.api.util.JsonUtils.OBJECT_MAPPER;

@Log4j2
// BAD CODE
@RequiredArgsConstructor
public class LocalBoardUsbListener {
    private final @NotNull Context context;

    @Getter
    private final Set<DiskInfo> usbDevices = new HashSet<>();

    public void listenUsbDevices() {
        if (SystemUtils.IS_OS_LINUX) {
            context.bgp().builder("mount-usb")
                    .delay(Duration.ofSeconds(30))
                    .interval(Duration.ofSeconds(30))
                    .execute(() -> {
                        List<String> infoList = context.hardware().executeNoErrorThrowList(
                                CHECK_USB, 10, (progress, message, error) -> {
                                });
                        List<DiskInfo> fetchedUsbDevices = fetchDevices(infoList).stream().filter(d -> !d.devices.isEmpty()).toList();

                        for (DiskInfo usbDevice : fetchedUsbDevices) {
                            DiskInfo finalUsbDevice = usbDevices
                                    .stream()
                                    .filter(u -> u.devicePath.equals(usbDevice.devicePath))
                                    .findAny()
                                    .orElse(usbDevice);
                            boolean showDialog = finalUsbDevice.uuid == null;
                            if (finalUsbDevice.uuid == null) {
                                findAndFillUsbDeviceUUID(finalUsbDevice);
                            }
                            usbDevices.add(finalUsbDevice);
                            if (finalUsbDevice.icon == null) {
                                findAndFillIconColor(finalUsbDevice);
                            }
                            if (finalUsbDevice.mount.isEmpty() && showDialog) {
                                handleUsbDeviceAdded(finalUsbDevice);
                            }
                        }
                        for (Iterator<DiskInfo> iterator = usbDevices.iterator(); iterator.hasNext(); ) {
                            DiskInfo usbDevice = iterator.next();
                            if (!fetchedUsbDevices.contains(usbDevice)) {
                                if (StringUtils.isNotEmpty(usbDevice.mount)) {
                                    context.hardware().executeNoErrorThrow("umount " + usbDevice.mount, 60, null);
                                }
                                iterator.remove();
                                context.ui().toastr().warn("Usb device was removed: " + usbDevice.devicePath);
                                context.ui().dialog().removeDialogRequest("mount_usb_" + usbDevice.uuid);
                                context.ui().console().refreshPluginContent(FileManagerConsolePlugin.NAME);
                            }
                        }
                    });
        }
    }

    private void findAndFillUsbDeviceUUID(DiskInfo usbDevice) {
        String info = context.hardware().execute("sudo blkid | grep '^%s'".formatted(usbDevice.devicePath));
        String[] items = info.split("\\s");
        for (String item : items) {
            if (item.startsWith("UUID")) {
                usbDevice.uuid = item.substring(6, item.length() - 1);
                usbDevice.alias = Math.abs(usbDevice.uuid.hashCode());
            } else if (item.startsWith("LABEL")) {
                usbDevice.label = item.substring(7, item.length() - 1);
            } else if (item.startsWith("TYPE")) {
                usbDevice.type = item.substring(6, item.length() - 1);
            }
        }
    }

    public void handleUsbDeviceAdded(DiskInfo usbDevice) {
        String path = usbDevice.getSourcePath();
        String mountTo = "/media" + path.substring(path.lastIndexOf("/"));
        if (context.setting().getValue(SystemAutoMountUsbSetting.class)) {
            mountUsb(mountTo, true, "fab fa-usb", "#5571E0", usbDevice);
            return;
        }

        context.ui().dialog().sendDialogRequest("mount_usb_" + usbDevice.uuid, "TITLE.MOUNT_USB",
                (responseType, pressedButton, parameters) -> {
                    if (responseType == ContextUI.DialogResponseType.Accepted) {
                        mountUsb(parameters.get("path").asText(),
                                parameters.get("save").asBoolean(),
                                parameters.get("icon").asText(),
                                parameters.get("color").asText(),
                                usbDevice);
                    }
                },
                dialogModel -> {
                    dialogModel.appearance(new Icon("fab fa-usb"), "#313A5B");
                    List<ActionInputParameter> inputs = new ArrayList<>();
                    inputs.add(ActionInputParameter.message("UUID: " + usbDevice.uuid));
                    inputs.add(ActionInputParameter.message("Path: " + usbDevice.devicePath));
                    inputs.add(ActionInputParameter.message("Size: " + usbDevice.devices.get(0).size));
                    inputs.add(ActionInputParameter.message("Label: " + usbDevice.label));
                    inputs.add(ActionInputParameter.message("Model: " + usbDevice.model));
                    inputs.add(ActionInputParameter.message("File system type: " + usbDevice.type));
                    inputs.add(ActionInputParameter.icon("icon", "fab fa-usb"));
                    inputs.add(ActionInputParameter.icon("color", "#5571E0"));

                    inputs.add(ActionInputParameter.bool("save", true));
                    String mountToPath = "/media" + usbDevice.devicePath.substring(usbDevice.devicePath.lastIndexOf("/"));
                    inputs.add(ActionInputParameter.textRequired("path", mountToPath, 3, 64));
                    dialogModel.submitButton("Mount", button -> {
                    }).group("General", inputs);
                });
    }

    @SneakyThrows
    private void mountUsb(String mountTo, boolean save, String icon, String color, DiskInfo usbDevice) {
        Files.createDirectories(Paths.get(mountTo));
        mountUsbDevice(usbDevice, mountTo);
        String text = "MOUNT.SUCCESS" + (save ? "_P" : "");
        context.ui().toastr().success(Lang.getServerMessage(text, mountTo));

        if (save) {
            LocalBoardEntity entity = LocalBoardEntity.getEntity(context);
            List<String> usbList = entity.getJsonDataList("usb");
            usbList.add(OBJECT_MAPPER.writeValueAsString(new UsbEntity(icon, color, usbDevice.getUuid(), mountTo)));
            context.db().updateDelayed(entity, e -> e.setJsonDataList("usb", usbList));
        }
    }

    private void findAndFillIconColor(DiskInfo usbDevice) {
        try {
            UsbEntity usbEntity = getSavedUsbInfo().get(usbDevice.uuid);
            if (usbEntity != null) {
                usbDevice.icon = usbEntity.icon;
                usbDevice.color = usbEntity.color;
                mountUsbDevice(usbDevice, usbEntity.mountTo);
            }
        } finally {
            if (usbDevice.icon == null) {
                usbDevice.icon = "fab fa-usb";
                usbDevice.color = "#215A6A";
            }
        }
    }

    private void mountUsbDevice(DiskInfo usbDevice, String mountTo) {
        try {
            context.hardware().execute("mount -o iocharset=utf8 " + usbDevice.getSourcePath() + " " + mountTo);
            usbDevice.mount = mountTo;
            context.ui().console().refreshPluginContent(FileManagerConsolePlugin.NAME);
        } catch (Exception ex) {
            log.warn("Unable to mount device: {}", usbDevice, ex);
        }
    }

    @SneakyThrows
    private @NotNull Map<String, UsbEntity> getSavedUsbInfo() {
        LocalBoardEntity entity = LocalBoardEntity.getEntity(context);
        List<String> usbList = entity.getJsonDataList("usb");
        Map<String, UsbEntity> usbEntities = new HashMap<>();
        for (String usbInfo : usbList) {
            UsbEntity usbEntity = OBJECT_MAPPER.readValue(usbInfo, UsbEntity.class);
            usbEntities.put(usbEntity.uuid, usbEntity);
        }
        return usbEntities;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class DiskInfo {

        public int alias;
        public String model;
        private String uuid;
        private String label;
        private String devicePath;
        private String type;
        private String mount = "";
        private String icon;
        private String color;
        private String id;

        private List<DeviceInfo> devices = new ArrayList<>();

        public String getSourcePath() {
            return devices.get(0).path;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            DiskInfo that = (DiskInfo) o;

            return devicePath.equals(that.devicePath);
        }

        @Override
        public int hashCode() {
            return devicePath.hashCode();
        }

        private static class DeviceInfo {
            private String path;
            private String size;
        }
    }

    private static List<DiskInfo> fetchDevices(List<String> diskInfoList) {
        List<DiskInfo> devices = new ArrayList<>();
        for (int i = 0; i < diskInfoList.size(); i++) {
            String line = diskInfoList.get(i);
            if (line.startsWith("Disk /dev/s")) {
                i++;
                DiskInfo device = new DiskInfo();
                devices.add(device);
                device.devicePath = line.substring("Disk".length(), line.indexOf(":")).trim();
                while (!diskInfoList.get(i).isEmpty()) {
                    if (diskInfoList.get(i).startsWith("Disk model:")) {
                        device.model = diskInfoList.get(i).substring("Disk model:".length()).trim();
                    } else if (diskInfoList.get(i).startsWith("Disk identifier:")) {
                        device.id = diskInfoList.get(i).substring("Disk identifier:".length()).trim();
                    }
                    i++;
                }
                if (diskInfoList.get(i + 1).startsWith("Device")) {
                    i++;
                    String[] headers = diskInfoList.get(i).split("\\s+");
                    List<String> lines = new ArrayList<>();
                    while (!diskInfoList.get(i).isEmpty()) {
                        lines.add(diskInfoList.get(++i));
                    }
                    lines.stream().filter(l -> !l.isEmpty()).forEach(deviceLine -> {
                        DiskInfo.DeviceInfo info = new DiskInfo.DeviceInfo();
                        device.devices.add(info);
                        String[] lineInfo = deviceLine.split("\\s+");
                        for (int j = 0; j < headers.length; j++) {
                            if (headers[j].startsWith("Size")) {
                                info.size = lineInfo[j];
                            } else if (headers[j].startsWith("Device")) {
                                info.path = lineInfo[j];
                            }
                        }
                    });
                }
            } else if (line.startsWith("-----")) {
                for (int d = i + 1; d < diskInfoList.size(); d++) {
                    String dh = diskInfoList.get(d);
                    if (dh.startsWith("/dev/s")) {
                        DiskInfo deviceInfo = devices.stream().filter(dev -> dh.startsWith(dev.devicePath)).findAny().orElse(null);
                        if (deviceInfo != null) {
                            deviceInfo.mount = dh.split("\\s+")[5];
                        }
                    }
                }
                break;
            }
        }
        return devices;
    }

    private static final String CHECK_USB = "sudo fdisk --list && printf \"\n--------\n\" &&  df -h";

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UsbEntity {
        private String icon;
        private String color;
        private String uuid;
        private String mountTo;
    }
}
