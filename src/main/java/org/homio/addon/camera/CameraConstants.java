package org.homio.addon.camera;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

public class CameraConstants {

    public static final String CM = "/cgi-bin/configManager.cgi?action=";

    public static final String ENDPOINT_MOTION_THRESHOLD = "motion_threshold";
    public static final String ENDPOINT_MOTION_SCORE = "motion_score";
    public static final String ENDPOINT_AUDIO_THRESHOLD = "audio_threshold";

    public static final String ENDPOINT_AUTO_LED = "auto_led";
    public static final String ENDPOINT_ENABLE_LED = "enable_led";
    public static final String ENDPOINT_WATERMARK_SHOW = "show_watermark";
    public static final String ENDPOINT_DATETIME_SHOW = "show_datetime";
    public static final String ENDPOINT_NAME_SHOW = "show_name";
    public static final String ENDPOINT_RECORD_AUDIO = "record_audio";
    public static final String ENDPOINT_IMAGE_ROTATE = "image_rotate";
    public static final String ENDPOINT_DAY_NIGHT = "day_night";
    public static final String ENDPOINT_NAME_POSITION = "name_position";
    public static final String ENDPOINT_DATETIME_POSITION = "position_datetime";
    public static final String ENDPOINT_WHITE_BALANCE = "white_balance";
    public static final String ENDPOINT_RED_GAIN = "red_gain";
    public static final String ENDPOINT_3DNR = "3dnr";
    public static final String ENDPOINT_IMAGE_MIRROR = "image_mirror";
    public static final String ENDPOINT_EXPOSURE = "exposure";
    public static final String ENDPOINT_DRC = "drc";
    public static final String ENDPOINT_BLUE_GAIN = "blue_gain";
    public static final String ENDPOINT_BLC = "blc";
    public static final String ENDPOINT_BLACK_LIGHT = "black_light";
    public static final String ENDPOINT_ANTI_FLICKER = "anti_flicker";
    public static final String ENDPOINT_BGCOLOR = "bgcolor";

    public static final String ENDPOINT_BRIGHT = "image_bright";
    public static final String ENDPOINT_CONTRAST = "image_contrast";
    public static final String ENDPOINT_HUE = "image_hue";
    public static final String ENDPOINT_SATURATION = "image_saturation";
    public static final String ENDPOINT_SHARPEN = "image_sharpen";

    public static final String ENDPOINT_STREAM_MAIN_RESOLUTION = "stream_main_resolution";
    public static final String ENDPOINT_STREAM_MAIN_BITRATE = "stream_main_bit_rate";
    public static final String ENDPOINT_STREAM_MAIN_FRAMERATE = "stream_main_frame_rate";
    public static final String ENDPOINT_STREAM_MAIN_H264_PROFILE = "stream_main_H264_profile";

    public static final String ENDPOINT_STREAM_SECONDARY_RESOLUTION = "stream_secondary_resolution";
    public static final String ENDPOINT_STREAM_SECONDARY_BITRATE = "stream_secondary_bit_rate";
    public static final String ENDPOINT_STREAM_SECONDARY_FRAMERATE = "stream_secondary_frame_rate";
    public static final String ENDPOINT_STREAM_SECONDARY_H264_PROFILE = "stream_secondary_H264_profile";

    public static final String ENDPOINT_CPU_LOADING = "cpu_load";

    public static final String ENDPOINT_BANDWIDTH = "bandwidth";

    public static final String ENDPOINT_HDD = "hdd";

    public static final String ENDPOINT_REBOOT = "reboot";
    public static final String ENDPOINT_ENABLE_AUDIO_ALARM = "enable_audio_alarm";
    public static final String ENDPOINT_ENABLE_MOTION_ALARM = "enable_motion_alarm";
    public static final String ENDPOINT_ENABLE_FIELD_DETECTION_ALARM = "enable_field_alarm";
    public static final String ENDPOINT_ENABLE_LINE_CROSSING_ALARM = "enable_line_cross_alarm";
    public static final String ENDPOINT_ENABLE_EXTERNAL_ALARM = "enable_external_alarm";
    public static final String ENDPOINT_ENABLE_PIR_ALARM = "enable_pir_alarm";

    public static final String ENDPOINT_PAN = "pan";
    public static final String ENDPOINT_PAN_COMMAND = "pan_cmd";
    public static final String ENDPOINT_TILT = "tilt";
    public static final String ENDPOINT_TILT_COMMAND = "tilt_cmd";
    public static final String ENDPOINT_ZOOM = "zoom";
    public static final String ENDPOINT_ZOOM_COMMAND = "zoom_cmd";

    public static final String ENDPOINT_ACTIVATE_ALARM_OUTPUT = "activate_alarm_output";
    public static final String ENDPOINT_ACTIVATE_ALARM_OUTPUT2 = "activate_alarm_output2";
    public static final String ENDPOINT_TRIGGER_EXTERNAL_ALARM_INPUT = "trigger_external_alarm_input";
    public static final String ENDPOINT_EXTERNAL_ALARM_INPUT = "external_alarm_input";
    public static final String ENDPOINT_EXTERNAL_ALARM_INPUT2 = "external_alarm_input2";

    public static final String ENDPOINT_TEXT_OVERLAY = "text_overlay";
    public static final String ENDPOINT_EXTERNAL_LIGHT = "external_light";
    public static final String ENDPOINT_DOORBELL = "door_bell";
    public static final String ENDPOINT_GOTO_PRESET = "goto_preset";
    public static final String ENDPOINT_ENABLE_PRIVACY_MODE = "enable_privacy_mode";

    public static final String ENDPOINT_MOTION_ALARM = "motion_alarm";
    public static final String ENDPOINT_FIELD_DETECTION_ALARM = "field_detect_alarm";
    public static final String ENDPOINT_LINE_CROSSING_ALARM = "line_cross_alarm";
    public static final String ENDPOINT_PIR_ALARM = "pir_alarm";
    public static final String ENDPOINT_CELL_MOTION_ALARM = "cell_alarm";
    public static final String ENDPOINT_ITEM_TAKEN_ALARM = "item_taken_alarm";
    public static final String ENDPOINT_ITEM_LEFT_ALARM = "item_left_alarm";
    public static final String ENDPOINT_CAR_ALARM = "car_alarm";
    public static final String ENDPOINT_HUMAN_ALARM = "human_alarm";
    public static final String ENDPOINT_FACE_DETECT = "face_detect_alarm";
    public static final String ENDPOINT_EXTERNAL_ALARM = "external_alarm";
    public static final String ENDPOINT_AUDIO_ALARM = "audio_alarm";
    public static final String ENDPOINT_TAMPER_ALARM = "tamper_alarm";
    public static final String ENDPOINT_STORAGE_ALARM = "storage_alarm";

    public static final String ENDPOINT_PARKING_ALARM = "parking_alarm";
    public static final String ENDPOINT_TOO_DARK_ALARM = "too_dark_alarm";
    public static final String ENDPOINT_SCENE_CHANGE_ALARM = "scene_change_alarm";
    public static final String ENDPOINT_TOO_BRIGHT_ALARM = "too_bright_alarm";
    public static final String ENDPOINT_TOO_BLURRY_ALARM = "too_blurry_alarm";


    @RequiredArgsConstructor
    public enum AlarmEvent {
        MotionAlarm(ENDPOINT_MOTION_ALARM),
        FieldDetectAlarm(ENDPOINT_FIELD_DETECTION_ALARM),
        LineCrossAlarm(ENDPOINT_LINE_CROSSING_ALARM),
        PirAlarm(ENDPOINT_PIR_ALARM),
        CellMotionAlarm(ENDPOINT_CELL_MOTION_ALARM),
        ItemTakenDetection(ENDPOINT_ITEM_TAKEN_ALARM),
        ItemLeftDetection(ENDPOINT_ITEM_LEFT_ALARM),
        CarAlarm(ENDPOINT_CAR_ALARM),
        HumanAlarm(ENDPOINT_HUMAN_ALARM),
        FaceDetect(ENDPOINT_FACE_DETECT),
        ExternalMotionAlarm(ENDPOINT_EXTERNAL_ALARM),
        AudioAlarm(ENDPOINT_AUDIO_ALARM),
        TamperAlarm(ENDPOINT_TAMPER_ALARM),
        ParkingAlarm(ENDPOINT_PARKING_ALARM),
        TooDarkAlarm(ENDPOINT_TOO_DARK_ALARM),
        SceneChangeAlarm(ENDPOINT_SCENE_CHANGE_ALARM),
        TooBrightAlarm(ENDPOINT_TOO_BRIGHT_ALARM),
        TooBlurryAlarm(ENDPOINT_TOO_BLURRY_ALARM),
        StorageAlarm(ENDPOINT_STORAGE_ALARM);

        @Getter
        private final String endpoint;
    }
}
