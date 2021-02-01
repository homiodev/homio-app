package org.touchhome.app.camera.openhub;

/**
 * class defines common constants, which are used across the whole binding.
 */
public class IpCameraBindingConstants {
    public static final String CM = "/cgi-bin/configManager.cgi?action=";

    private static final String BINDING_ID = "ipcamera";
    public static final String AUTH_HANDLER = "authorizationHandler";
    public static final String AMCREST_HANDLER = "amcrestHandler";
    public static final String COMMON_HANDLER = "commonHandler";
    public static final String INSTAR_HANDLER = "instarHandler";

    public static enum FFmpegFormat {
        HLS,
        GIF,
        RECORD,
        RTSP_ALARMS,
        MJPEG,
        SNAPSHOT
    }

    // List of all Thing Type UIDs
    public static final String THING_TYPE_GROUP = "group";
    /*public static final String GENERIC_THING = "generic";
    public static final String ONVIF_THING = "onvif";
    public static final String AMCREST_THING = "amcrest";
    public static final String FOSCAM_THING = "foscam";
    public static final String HIKVISION_THING = "hikvision";
    public static final String INSTAR_THING = "instar";
    public static final String DAHUA_THING = "dahua";
    public static final String DOORBIRD_THING = "doorbird";*/

 /*   public static final Set<String> SUPPORTED_THING_TYPES = new HashSet<>(
            Arrays.asList(ONVIF_THING, GENERIC_THING, AMCREST_THING, DAHUA_THING, INSTAR_THING,
                    FOSCAM_THING, DOORBIRD_THING, HIKVISION_THING));*/

   /* public static final Set<String> GROUP_SUPPORTED_THING_TYPES = new HashSet<>(
            Collections.singletonList(THING_TYPE_GROUP));*/

    // List of all Thing Config items
    public static final String CONFIG_IPADDRESS = "ipAddress";
    public static final String CONFIG_ONVIF_PORT = "onvifPort";

    // List of all Channel ids
    public static final String CHANNEL_RECORDING_GIF = "recordingGif";
    public static final String CHANNEL_GIF_HISTORY = "gifHistory";
    public static final String CHANNEL_RECORDING_MP4 = "recordingMp4";
    public static final String CHANNEL_MP4_PREROLL = "mp4Preroll";
    public static final String CHANNEL_MP4_HISTORY = "mp4History";
    public static final String CHANNEL_IMAGE = "image";
    public static final String CHANNEL_RTSP_URL = "rtspUrl";
    public static final String CHANNEL_IMAGE_URL = "imageUrl";
    public static final String CHANNEL_MJPEG_URL = "mjpegUrl";
    public static final String CHANNEL_HLS_URL = "hlsUrl";
    public static final String CHANNEL_PAN = "pan";
    public static final String CHANNEL_TILT = "tilt";
    public static final String CHANNEL_ZOOM = "zoom";
    public static final String CHANNEL_EXTERNAL_MOTION = "externalMotion";
    public static final String CHANNEL_MOTION_ALARM = "motionAlarm";
    public static final String CHANNEL_LINE_CROSSING_ALARM = "lineCrossingAlarm";
    public static final String CHANNEL_FACE_DETECTED = "faceDetected";
    public static final String CHANNEL_ITEM_LEFT = "itemLeft";
    public static final String CHANNEL_ITEM_TAKEN = "itemTaken";
    public static final String CHANNEL_AUDIO_ALARM = "audioAlarm";
    public static final String CHANNEL_ENABLE_MOTION_ALARM = "enableMotionAlarm";
    public static final String CHANNEL_FFMPEG_MOTION_ALARM = "ffmpegMotionAlarm";
    public static final String CHANNEL_ENABLE_LINE_CROSSING_ALARM = "enableLineCrossingAlarm";
    public static final String CHANNEL_ENABLE_AUDIO_ALARM = "enableAudioAlarm";
    public static final String CHANNEL_THRESHOLD_AUDIO_ALARM = "thresholdAudioAlarm";
    public static final String CHANNEL_ACTIVATE_ALARM_OUTPUT = "activateAlarmOutput";
    public static final String CHANNEL_ACTIVATE_ALARM_OUTPUT2 = "activateAlarmOutput2";
    public static final String CHANNEL_ENABLE_EXTERNAL_ALARM_INPUT = "enableExternalAlarmInput";
    public static final String CHANNEL_TRIGGER_EXTERNAL_ALARM_INPUT = "triggerExternalAlarmInput";
    public static final String CHANNEL_EXTERNAL_ALARM_INPUT = "externalAlarmInput";
    public static final String CHANNEL_EXTERNAL_ALARM_INPUT2 = "externalAlarmInput2";
    public static final String CHANNEL_AUTO_LED = "autoLED";
    public static final String CHANNEL_ENABLE_LED = "enableLED";
    public static final String CHANNEL_ENABLE_PIR_ALARM = "enablePirAlarm";
    public static final String CHANNEL_PIR_ALARM = "pirAlarm";
    public static final String CHANNEL_CELL_MOTION_ALARM = "cellMotionAlarm";
    public static final String CHANNEL_ENABLE_FIELD_DETECTION_ALARM = "enableFieldDetectionAlarm";
    public static final String CHANNEL_FIELD_DETECTION_ALARM = "fieldDetectionAlarm";
    public static final String CHANNEL_PARKING_ALARM = "parkingAlarm";
    public static final String CHANNEL_TAMPER_ALARM = "tamperAlarm";
    public static final String CHANNEL_TOO_DARK_ALARM = "tooDarkAlarm";
    public static final String CHANNEL_STORAGE_ALARM = "storageAlarm";
    public static final String CHANNEL_SCENE_CHANGE_ALARM = "sceneChangeAlarm";
    public static final String CHANNEL_TOO_BRIGHT_ALARM = "tooBrightAlarm";
    public static final String CHANNEL_TOO_BLURRY_ALARM = "tooBlurryAlarm";
    public static final String CHANNEL_TEXT_OVERLAY = "textOverlay";
    public static final String CHANNEL_EXTERNAL_LIGHT = "externalLight";
    public static final String CHANNEL_DOORBELL = "doorBell";
    public static final String CHANNEL_LAST_MOTION_TYPE = "lastMotionType";
    public static final String CHANNEL_GOTO_PRESET = "gotoPreset";
    public static final String CHANNEL_START_STREAM = "startStream";
    public static final String CHANNEL_ENABLE_PRIVACY_MODE = "enablePrivacyMode";
}
