package org.touchhome.app.videoStream.ffmpeg;

import org.springframework.data.util.Pair;
import org.touchhome.bundle.api.hquery.api.HQueryParam;
import org.touchhome.bundle.api.hquery.api.HardwareQuery;
import org.touchhome.bundle.api.hquery.api.HardwareRepositoryAnnotation;
import org.touchhome.bundle.api.hquery.api.RawParse;
import org.touchhome.bundle.api.util.TouchHomeUtils;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@HardwareRepositoryAnnotation
public interface FfmpegInputDeviceHardwareRepository {

    @RawParse(nix = LinuxInputDeviceBuilder.class, win = WindowsInputDeviceBuilder.class)
    @HardwareQuery(
            value = ":ffmpeg -f video4linux2 -hide_banner -list_formats all -i :vfile",
            win = ":ffmpeg -list_options true -f dshow -hide_banner -i \"video=:vfile\"",
            redirectErrorsToInputs = true)
    FFmpegVideoDevice createVideoInputDevice(@HQueryParam("ffmpeg") String ffmpeg, @HQueryParam("vfile") String vfile);

    @RawParse(nix = WindowsInputVideoDevicesParser.class, win = WindowsInputVideoDevicesParser.class)
    @HardwareQuery(value = "", win = ":ffmpeg -list_devices true -f dshow -hide_banner -i dummy",
            printOutput = true, cacheValid = 60, redirectErrorsToInputs = true)
    Pair<List<String>, List<String>> getWindowsInputDevices(@HQueryParam("ffmpeg") String ffmpeg);

    default List<String> getVideoDevices(String ffmpegPath) {
        List<String> devices = new ArrayList<>();
        if (TouchHomeUtils.OS_NAME.isLinux()) {
            File DEV = new File("/dev");
            String[] names = DEV.list((dir, name) -> dir.getName().equals("dev") && name.startsWith("video") && Character.isDigit(name.charAt(5)));
            for (String name : names) {
                devices.add(new File(DEV, name).getAbsolutePath());
            }
        } else {
            devices.addAll(this.getWindowsInputDevices(ffmpegPath).getFirst());
        }
        return devices;
    }

    class WindowsInputVideoDevicesParser implements RawParse.RawParseHandler {
        private final String STARTER = "[dshow";
        private final String NAME_MARKER = "]  \"";
        private final String VIDEO_MARKER = "] DirectShow video";
        private final String AUDIO_MARKER = "] DirectShow audio";

        @Override
        public Pair<List<String>, List<String>> handle(List<String> inputs, Field field) {
            boolean startDevices = false;
            Pair<List<String>, List<String>> result = Pair.of(new ArrayList<>(2), new ArrayList<>(2));
                List<String> devices = result.getFirst();
            for (String line : inputs) {
                if (line.startsWith(STARTER) && line.contains(VIDEO_MARKER)) {
                    startDevices = true;
                    continue;
                }
                if (startDevices) {
                    if (line.startsWith(STARTER) && line.contains(NAME_MARKER)) {
                        String deviceName = line.substring(line.indexOf(NAME_MARKER) + NAME_MARKER.length());
                        deviceName = deviceName.substring(0, deviceName.length() - 1);
                        devices.add(deviceName);
                        continue;
                    }
                    if (line.startsWith(STARTER) && line.contains(AUDIO_MARKER)) {
                        devices = result.getSecond();
                    }
                }
            }
            return result;
        }
    }

    class LinuxInputDeviceBuilder implements RawParse.RawParseHandler {

        final String STARTER = "[video4linux2";
        final String MARKER = "] Raw";

        @Override
        public FFmpegVideoDevice handle(List<String> inputs, Field field) {
            for (String line : inputs) {
                if (line.startsWith(STARTER) && line.contains(MARKER)) {
                    String resolutions = line.split(" : ")[3].trim();
                    return new FFmpegVideoDevice(resolutions);
                }
            }
            return null;
        }
    }

    class WindowsInputDeviceBuilder implements RawParse.RawParseHandler {

        final String STARTER = "[dshow";
        final String MARKER = "max s=";
        Set<String> resolutions = new LinkedHashSet<>();

        @Override
        public FFmpegVideoDevice handle(List<String> inputs, Field field) {
            for (String line : inputs) {
                if (line.startsWith(STARTER) && line.contains(MARKER)) {
                    int begin = line.indexOf(MARKER) + MARKER.length();
                    String resolution = line.substring(begin, line.indexOf(" ", begin));
                    resolutions.add(resolution);
                }
            }
            StringBuilder vinfo = new StringBuilder();
            for (String resolution : resolutions) {
                vinfo.append(resolution).append(" ");
            }

            return new FFmpegVideoDevice(vinfo.toString().trim());
        }
    }
}
