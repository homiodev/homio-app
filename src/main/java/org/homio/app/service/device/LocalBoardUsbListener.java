package org.homio.app.service.device;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.SystemUtils;
import org.homio.api.Context;
import org.homio.api.model.Icon;
import org.homio.api.ui.UI;
import org.homio.api.ui.field.action.ActionInputParameter;
import org.homio.api.util.Lang;
import org.jetbrains.annotations.NotNull;

@Log4j2
@RequiredArgsConstructor
public class LocalBoardUsbListener {
    private final @NotNull Context context;

    @Getter
    private final Set<UsbDeviceInfo> usbDevices = new HashSet<>();
    private boolean firstBoot = true;

    public void listenUsbDevices() {
        if (SystemUtils.IS_OS_LINUX) {
            context.bgp().builder("mount-usb")
                   .interval(Duration.ofSeconds(30))
                   .execute(() -> {
                       String response = context.hardware().execute(CHECK_USB);
                       if(response.isEmpty()) {
                           return;
                       }
                       List<String> devices = List.of(response.split("\n"));
                       Set<UsbDeviceInfo> fetchedUsbDevices = new HashSet<>();

                       for (String device : devices) {
                           fetchedUsbDevices.add(new UsbDeviceInfo(device));
                       }
                       for (UsbDeviceInfo usbDevice : fetchedUsbDevices) {
                           UsbDeviceInfo finalUsbDevice = usbDevices
                               .stream()
                               .filter(u -> u.devicePath.equals(usbDevice.devicePath))
                               .findAny()
                               .orElse(usbDevice);
                           usbDevices.add(finalUsbDevice);
                           if (finalUsbDevice.uuid == null) {
                               findAndFillUsbDeviceUUID(finalUsbDevice);
                           }
                           if (finalUsbDevice.icon == null) {
                               findAndFillIconColor(finalUsbDevice);
                           }
                           if (finalUsbDevice.mount.isEmpty() && !firstBoot) {
                               handleUsbDeviceAdded(finalUsbDevice);
                           }
                       }
                       firstBoot = false;
                       for (Iterator<UsbDeviceInfo> iterator = usbDevices.iterator(); iterator.hasNext(); ) {
                           UsbDeviceInfo usbDevice = iterator.next();
                           if (!fetchedUsbDevices.contains(usbDevice)) {
                               iterator.remove();
                               context.ui().toastr().warn("Usb device was removed: " + usbDevice.devicePath);
                           }
                       }
                   });
        }
    }

    private void findAndFillUsbDeviceUUID(UsbDeviceInfo usbDevice) {
        String info = context.hardware().execute("sudo blkid | grep '^%s'".formatted(usbDevice.devicePath));
        String[] items = info.split(" ");
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

    private void handleUsbDeviceAdded(UsbDeviceInfo usbDevice) {
        context.ui().dialog().sendDialogRequest("mount_usb", "TITLE.MOUNT_USB",
            (responseType, pressedButton, parameters) ->
                mountUsb(context, parameters, usbDevice),
            dialogModel -> {
                dialogModel.appearance(new Icon("fab fa-usb"), "#313A5B");
                List<ActionInputParameter> inputs = new ArrayList<>();
                inputs.add(ActionInputParameter.message("Info: " + usbDevice.devicePath + " - " + usbDevice.size));
                inputs.add(ActionInputParameter.message("Label: " + usbDevice.label));
                inputs.add(ActionInputParameter.message("File system type: " + usbDevice.type));
                inputs.add(ActionInputParameter.icon("icon", "fab fa-usb"));
                inputs.add(ActionInputParameter.icon("color", "#5571E0"));

                inputs.add(ActionInputParameter.bool("save", true));
                inputs.add(ActionInputParameter.textRequired("path", "/mnt/usb", 3, 64));
                dialogModel.submitButton("Mount", button -> {
                }).group("General", inputs);
            });
    }

    @SneakyThrows
    private void mountUsb(Context context, ObjectNode parameters, UsbDeviceInfo usbDevice) {
        String path = parameters.get("path").asText();
        Files.createDirectories(Paths.get(path));
        context.hardware().execute("mount " + usbDevice.devicePath + " " + path);
        usbDevice.mount = path;
        String text = "MOUNT.SUCCESS";
        if (parameters.get("save").asBoolean()) {
            Path fstab = Paths.get("/etc/fstab");
            List<String> lines = Files.readAllLines(fstab);
            String existed = lines.stream().filter(l -> l.contains(usbDevice.uuid)).findAny().orElse(null);
            if (existed != null) {
                return;
            }
            lines.add("#INFO:%s~~~%s".formatted(parameters.get("icon").asText(), parameters.get("color").asText()));
            lines.add("UUID=%s %s %s defaults 0 0".formatted(usbDevice.uuid, path, usbDevice.type));
            Files.write(fstab, lines);
            text += "_P";
        }
        context.ui().toastr().success(Lang.getServerMessage(text, path));
    }

    private void findAndFillIconColor(UsbDeviceInfo usbDevice) {
        try {
            List<String> lines = Files.readAllLines(Paths.get("/etc/fstab"));
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.startsWith("UUID=" + usbDevice.uuid)) {
                    if (i > 0 && lines.get(i - 1).startsWith("#INFO:")) {
                        String[] comment = lines.get(i - 1).substring("#INFO:".length()).split("~~~");
                        usbDevice.icon = comment[0];
                        usbDevice.color = comment[1];
                    }
                    return;
                }
            }
        } catch (Exception ex) {
            log.error("Unable to read /etc/fstab", ex);
        } finally {
            if (usbDevice.icon == null) {
                usbDevice.icon = "fab fa-usb";
                usbDevice.color = UI.Color.random();
            }
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class UsbDeviceInfo {

        public int alias;
        private String uuid;
        private String label;
        private String devicePath;
        private boolean isBootable;
        private long startSector;
        private long endSector;
        private long sectors;
        private String size;
        private String type;
        private String mount;
        private String icon;
        private String color;

        public UsbDeviceInfo(String device) {
            String[] parts = device.split("\\s+");

            devicePath = parts[0];
            isBootable = parts[1].equals("*");
            startSector = Long.parseLong(parts[2]);
            endSector = Long.parseLong(parts[3]);
            sectors = Long.parseLong(parts[4]);
            size = parts[5];
            mount = parts.length > 8 ? parts[8] : "";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {return true;}
            if (o == null || getClass() != o.getClass()) {return false;}

            UsbDeviceInfo that = (UsbDeviceInfo) o;

            return devicePath.equals(that.devicePath);
        }

        @Override
        public int hashCode() {
            return devicePath.hashCode();
        }
    }

    private static final String CHECK_USB = """
        sudo fdisk -l | grep '^/dev/s' | while read -r line; do
            device_path=$(echo "$line" | awk '{print $1}')
            mount_point=$(df -h | awk -v device="$device_path" '$1 == device {print $6}')
            echo "$line $mount_point"
        done""";
}
