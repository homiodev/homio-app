package org.homio.app.video.ffmpeg;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.homio.hquery.api.ErrorsHandler;
import org.homio.hquery.api.HQueryMaxWaitTimeout;
import org.homio.hquery.api.HQueryParam;
import org.homio.hquery.api.HardwareQuery;
import org.homio.hquery.api.HardwareRepository;
import org.homio.hquery.api.RawParse;

@HardwareRepository
public interface FfmpegHardwareRepository {

    @HardwareQuery(
            name = "Run ffmpeg command",
            redirectErrorsToInputs = true,
            value = ":ffmpeg :inputOptions -i :source :output", win = ":ffmpeg :inputOptions -i :source :output")
    @ErrorsHandler(throwError = true, logError = false)
    void fireFfmpeg(@HQueryParam("ffmpeg") String ffmpeg,
                    @HQueryParam("inputOptions") String inputOptions,
                    @HQueryParam("source") String source,
                    @HQueryParam("output") String output,
                    @HQueryMaxWaitTimeout int maxWaitTimeout);

    @RawParse(nix = LinuxInputDeviceBuilder.class, win = WindowsInputDeviceBuilder.class)
    @HardwareQuery(
            name = "Ffmpeg create input device",
            value = ":ffmpeg -f v4l2 -hide_banner -list_formats all -i :vfile",
            win = ":ffmpeg -list_options true -f dshow -hide_banner -i \"video=:vfile\"",
            redirectErrorsToInputs = true)
    FFMPEGVideoDevice createVideoInputDevice(@HQueryParam("ffmpeg") String ffmpeg, @HQueryParam("vfile") String vfile);

    @RawParse(nix = WindowsInputVideoDevicesParser.class, win = WindowsInputVideoDevicesParser.class)
    @HardwareQuery(name = "Ffmpeg get window devices", value = "",
            win = ":ffmpeg -list_devices true -f dshow -hide_banner -i dummy",
            printOutput = true, cacheValid = 60, redirectErrorsToInputs = true)
    Pair<List<String>, List<String>> getWindowsInputDevices(@HQueryParam("ffmpeg") String ffmpeg);

    default Set<String> getVideoDevices(String ffmpegPath) {
        return getStrings("video", () -> getWindowsInputDevices(ffmpegPath).getKey());
    }

    default Set<String> getAudioDevices(String ffmpegPath) {
        return getStrings("audio", () -> getWindowsInputDevices(ffmpegPath).getValue());
    }

    default Set<String> getStrings(String prefix, Supplier<List<String>> windowDeviceFetcher) {
        Set<String> devices = new HashSet<>();
        if (SystemUtils.IS_OS_LINUX) {
            File DEV = new File("/dev");
            String[] names = DEV.list(
                    (dir, name) -> dir.getName().equals("dev") && name.startsWith(prefix) && Character.isDigit(name.charAt(5)));
            for (String name : names) {
                devices.add(new File(DEV, name).getAbsolutePath());
            }
        } else {
            devices.addAll(windowDeviceFetcher.get());
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
            List<String> devices = result.getKey();
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
                        devices = result.getValue();
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
        public FFMPEGVideoDevice handle(List<String> inputs, Field field) {
            for (String line : inputs) {
                if (line.startsWith(STARTER) && line.contains(MARKER)) {
                    String resolutions = line.split(" : ")[3].trim();
                    return new FFMPEGVideoDevice(resolutions);
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
        public FFMPEGVideoDevice handle(List<String> inputs, Field field) {
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

            return new FFMPEGVideoDevice(vinfo.toString().trim());
        }
    }
}
