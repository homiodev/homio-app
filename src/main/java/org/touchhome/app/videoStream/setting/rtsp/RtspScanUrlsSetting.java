package org.touchhome.app.videoStream.setting.rtsp;

import org.touchhome.app.setting.CoreSettingPlugin;
import org.touchhome.bundle.api.setting.SettingPluginTextSet;

import java.util.Set;

public class RtspScanUrlsSetting implements SettingPluginTextSet, CoreSettingPlugin<Set<String>> {

    @Override
    public int order() {
        return 1000;
    }

    @Override
    public String[] defaultValue() {
        return new String[]{"/av0", "/av0_0", "/av1", "/av2", "/cam", "/cam1", "/cam1/h264", "/cam1/h264", "/cam1/mjpeg",
                "/cam1/mpeg4", "/ch0", "/ch0.sdp", "/channel0", "/channel1", "/encoder1", "/h264", "/h264/media.amp",
                "/image.mpg", "/img/video.sav", "/ipcam.sdp", "/ipcam", "/jpeg", "/live/ch00_0", "/live.h264",
                "/live_mpeg4.sdp", "/live.sdp", "/livestream", "/media", "/media1", "/media/media.amp", "/media/video1",
                "/mpeg", "/mpeg4", "/mpeg4/1/media.amp", "/mpeg4/media.amp", "/mpg", "/mpg4/rtsp.amp", "/play1.sdp",
                "/play2.sdp", "/rtpvideo1.sdp", "/rtpvideo.sdp", "/stream", "/streaming/channels/0", "/video",
                "/video.mp4", "/file?file=jellyfish-5-mbps-hd-h264.mkv"};
    }

   /* @Override
    public boolean isAdvanced() {
        return true;
    }*/

    @Override
    public GroupKey getGroupKey() {
        return GroupKey.dashboard;
    }
}
