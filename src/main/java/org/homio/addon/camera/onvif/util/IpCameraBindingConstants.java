package org.homio.addon.camera.onvif.util;

/**
 * class defines common constants, which are used across the whole binding.
 */
public class IpCameraBindingConstants {

  public static final String CM = "/cgi-bin/configManager.cgi?action=";

  public static final String AUTH_HANDLER = "authorizationHandler";
  public static final String COMMON_HANDLER = "commonHandler";

  // general motion key for al types of motion
  public static final String MOTION_ALARM = "motionAlarm";

  // List of all Channel ids
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

  public static final String CHANNEL_STREAM_MAIN_RESOLUTION = "streamMainResolution";
  public static final String CHANNEL_STREAM_MAIN_BITRATE = "streamMainBitRate";
  public static final String CHANNEL_STREAM_MAIN_FRAMERATE = "streamMainFrameRate";
  public static final String CHANNEL_STREAM_MAIN_H264_PROFILE = "streamMainH264Profile";

  public static final String CHANNEL_STREAM_SECONDARY_RESOLUTION = "streamSecondaryResolution";
  public static final String CHANNEL_STREAM_SECONDARY_BITRATE = "streamSecondaryBitRate";
  public static final String CHANNEL_STREAM_SECONDARY_FRAMERATE = "streamSecondaryFrameRate";
  public static final String CHANNEL_STREAM_SECONDARY_H264_PROFILE = "streamSecondaryH264Profile";

  public static final String CHANNEL_ENABLE_MOTION_ALARM = "enableMotionAlarm";
  public static final String CHANNEL_FFMPEG_MOTION_ALARM = "ffmpegMotionAlarm";
  public static final String CHANNEL_ENABLE_LINE_CROSSING_ALARM = "enableLineCrossingAlarm";
  public static final String CHANNEL_ENABLE_AUDIO_ALARM = "enableAudioAlarm";
  public static final String CHANNEL_ACTIVATE_ALARM_OUTPUT = "activateAlarmOutput";
  public static final String CHANNEL_ACTIVATE_ALARM_OUTPUT2 = "activateAlarmOutput2";
  public static final String CHANNEL_ENABLE_EXTERNAL_ALARM_INPUT = "enableExternalAlarmInput";
  public static final String CHANNEL_TRIGGER_EXTERNAL_ALARM_INPUT = "triggerExternalAlarmInput";
  public static final String CHANNEL_EXTERNAL_ALARM_INPUT = "externalAlarmInput";
  public static final String CHANNEL_EXTERNAL_ALARM_INPUT2 = "externalAlarmInput2";
  public static final String CHANNEL_POSITION_NAME = "positionName";
  public static final String CHANNEL_ANTI_FLICKER = "antiFlicker";
  public static final String CHANNEL_EXPOSURE = "exposure";
  public static final String CHANNEL_IMAGE_MIRROR = "mirrorImage";
  public static final String CHANNEL_3DNR = "3dnr";

  public static final String CHANNEL_POSITION_DATETIME = "positionDateTime";
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
  public static final String CHANNEL_CAR_ALARM = "carAlarm";
  public static final String CHANNEL_HUMAN_ALARM = "humanAlarm";
}
