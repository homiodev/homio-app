package org.homio.addon.camera.workspace;

import static org.homio.addon.camera.CameraConstants.AlarmEvents.ExternalMotionAlarm;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_ENABLE_AUDIO_ALARM;

import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.IOUtils;
import org.homio.addon.camera.CameraConstants.AlarmEvents;
import org.homio.addon.camera.CameraEntrypoint;
import org.homio.addon.camera.entity.BaseCameraEntity;
import org.homio.addon.camera.entity.CameraPlaybackStorage;
import org.homio.addon.camera.entity.CameraPlaybackStorage.DownloadFile;
import org.homio.addon.camera.entity.OnvifCameraEntity;
import org.homio.addon.camera.entity.storage.VideoBaseStorageService;
import org.homio.addon.camera.onvif.brand.BaseOnvifCameraBrandHandler;
import org.homio.addon.camera.service.BaseCameraService;
import org.homio.api.EntityContext;
import org.homio.api.model.OptionModel.KeyValueEnum;
import org.homio.api.state.DecimalType;
import org.homio.api.state.OnOffType;
import org.homio.api.state.RawType;
import org.homio.api.state.State;
import org.homio.api.util.CommonUtils;
import org.homio.api.workspace.BroadcastLock;
import org.homio.api.workspace.WorkspaceBlock;
import org.homio.api.workspace.scratch.ArgumentType;
import org.homio.api.workspace.scratch.MenuBlock;
import org.homio.api.workspace.scratch.Scratch3ExtensionBlocks;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

@SuppressWarnings("unchecked")
@Log4j2
@Getter
@Component
public class Scratch3CameraBlocks extends Scratch3ExtensionBlocks {

    public static final String RECORD_STORAGE_MENU = "recordStorageMenu";
    protected static final String VIDEO_STREAM = "VIDEO_STREAM";
    private static final String ON_OFF = "ON_OFF";
    private static final String RECORD_TYPE = "RECORD_TYPE";

    private static final RetryPolicy<DownloadFile> PLAYBACK_DOWNLOAD_FILE_RETRY_POLICY =
            RetryPolicy.<DownloadFile>builder()
                    .handle(Exception.class)
                    .withDelay(Duration.ofSeconds(5))
                    .withMaxRetries(3)
                    .build();

    private final MenuBlock.ServerMenuBlock menuFfmpegCamera;
    private final MenuBlock.StaticMenuBlock<OnOffType.OnOffTypeEnum> menuOnOff;
    private final MenuBlock.StaticMenuBlock<RecordType> menuRecordType;

    // private final MenuBlock.ServerMenuBlock profileMenu;
    private final MenuBlock.ServerMenuBlock menuFfmpegCameraWithProfiles;
    private final MenuBlock.StaticMenuBlock<CameraReportCommands> menuCameraReportCommand;
    private final MenuBlock.StaticMenuBlock<BoolHatMenuEnum> menuBoolHat;
    private final MenuBlock.StaticMenuBlock<SetCameraIntParamEnum> menuIntParam;
    private final MenuBlock.StaticMenuBlock<SetCameraBoolParamEnum> menuBoolParam;
    private final MenuBlock.StaticMenuBlock<CameraProfileReportCommands> menuCameraProfileReportCommand;
    private final MenuBlock.StaticMenuBlock<OnvifCameraHatType> menuOnvifCameraHatType;
    private final MenuBlock.ServerMenuBlock menuCameraRecord;
    private final MenuBlock.ServerMenuBlock menuOnvifCamera;

    public Scratch3CameraBlocks(EntityContext entityContext, CameraEntrypoint cameraEntrypoint) {
        super("#8F4D77", entityContext, cameraEntrypoint, null);
        setParent("media");

        // Menu
        this.menuCameraRecord = menuServerItems(RECORD_STORAGE_MENU, VideoBaseStorageService.class, "Record storage");
        this.menuFfmpegCamera = menuServerItems("ffmpegCameraMenu", BaseCameraEntity.class, "Camera");
        this.menuFfmpegCameraWithProfiles = menuServer("ffmpegCameraMenuWithProfiles", "rest/media/video/ffmpegWithProfiles", "Camera");
        this.menuOnOff = menuStatic("onOff", OnOffType.OnOffTypeEnum.class, OnOffType.OnOffTypeEnum.On);
        this.menuRecordType = menuStatic("recordType", RecordType.class, RecordType.Mp4);

        this.menuCameraReportCommand = menuStaticKV("camReport", CameraReportCommands.class, CameraReportCommands.MotionAlarmThreshold);
        this.menuCameraProfileReportCommand = menuStaticKV("camProfileReport", CameraProfileReportCommands.class,
                CameraProfileReportCommands.Snapshot);

        this.menuBoolHat = menuStaticKV("boolHatMenu", BoolHatMenuEnum.class, BoolHatMenuEnum.MotionAlarm);
        this.menuIntParam = menuStaticKV("intParamMenu", SetCameraIntParamEnum.class, SetCameraIntParamEnum.MotionAlarm);
        this.menuBoolParam = menuStaticKV("boolParamMenu", SetCameraBoolParamEnum.class, SetCameraBoolParamEnum.IRLedValue);
        this.menuOnvifCameraHatType = menuStatic("onvifCameraHatType", OnvifCameraHatType.class, OnvifCameraHatType.FaceDetection);
        this.menuOnvifCamera = menuServerItems("cameraMenu", OnvifCameraEntity.class, "Onvif camera");

        // Hats
        blockHat(10, "bool_hat", "[VALUE] [ON_OFF] of [VIDEO_STREAM]", this::listenBoolHat, block -> {
            block.addArgument(VIDEO_STREAM, this.menuFfmpegCamera);
            block.addArgument("VALUE", this.menuBoolHat);
            block.addArgument(ON_OFF, this.menuOnOff);
        });

        blockCommand(15, "record", "Infinite record [VIDEO_STREAM] to file [NAME] using [FS]", this::recordCameraHandler, block -> {
            block.addArgument(VIDEO_STREAM, this.menuFfmpegCameraWithProfiles);
            block.addArgument("FS", this.menuCameraRecord);
            block.addArgument("NAME", "video-%03d.mp4");
        });

        // Reporters
        blockReporter(33, "camReporter", "Get [ENTITY] of [VIDEO_STREAM]", this::getCameraReportHandler, block -> {
            block.addArgument(VIDEO_STREAM, this.menuFfmpegCamera);
            block.addArgument(ENTITY, this.menuCameraReportCommand);
        });

        blockReporter(40, "get_img", "Get [ENTITY] of [VIDEO_STREAM]", this::getCameraWithProfileParameterReporter, block -> {
            block.addArgument(VIDEO_STREAM, this.menuFfmpegCameraWithProfiles);
            block.addArgument(ENTITY, this.menuCameraProfileReportCommand);
        });

        blockReporter(110, "get_gif_mp4", "Get [RECORD_TYPE] [TIME]sec. of [VIDEO_STREAM]", this::getGifMP4Reporter, block -> {
            block.addArgument(VIDEO_STREAM, this.menuFfmpegCameraWithProfiles);
            block.addArgument("TIME", ArgumentType.number, "5");
            block.addArgument(RECORD_TYPE, this.menuRecordType);
            block.appendSpace();
        });

        // Commands
        /* this.recordImageCommand = withServerFfmpeg(blockCommand(140, "record_image",
                "Record image of [VIDEO_STREAM]", this::firePollImageCommand)); */

        blockCommand(150, "record_gif_mp4", "Record [RECORD_TYPE] [TIME]sec. to file [NAME] of [VIDEO_STREAM]",
                this::fireRecordGifMP4Command, block -> {
                    block.addArgument(VIDEO_STREAM, this.menuFfmpegCameraWithProfiles);
                    block.addArgument(RECORD_TYPE, this.menuRecordType);
                    block.addArgument("TIME", ArgumentType.number, "5");
                    block.addArgument("NAME", ArgumentType.string, "${YEAR}/${MONTH}/video-${TIME}");
                });

        blockCommand(160, "set_int_param", "Set [ENTITY] as [VALUE] of [VIDEO_STREAM]", this::setIntParamCommand, block -> {
            block.addArgument(VIDEO_STREAM, this.menuFfmpegCamera);
            block.addArgument(ENTITY, this.menuIntParam);
            block.addArgument(VALUE, 35);
        });

        blockCommand(170, "set_bool_param", "Fire event [ENTITY] as [ON_OFF] of [VIDEO_STREAM]", this::setBoolParamCommand, block -> {
            block.addArgument(VIDEO_STREAM, this.menuFfmpegCamera);
            block.addArgument(ENTITY, this.menuBoolParam);
            block.addArgument(ON_OFF, this.menuOnOff);
        });

        blockHat(200, "onvif_alarm_detect", "[VALUE] alarm of [VIDEO_STREAM]", this::whenDetectionAlarmHat, block -> {
            block.addArgument(VIDEO_STREAM, getMenuOnvifCamera());
            block.addArgument("VALUE", menuOnvifCameraHatType);
        });
    }

    private static BaseOnvifCameraBrandHandler getBrandHandler(BaseCameraEntity<?, ?> entity) {
        return ((OnvifCameraEntity) entity).getService().getBrandHandler();
    }

    private void whenDetectionAlarmHat(WorkspaceBlock workspaceBlock) {
        throw new RuntimeException("Not implemented yet");
    }

    private State getCameraReportHandler(WorkspaceBlock workspaceBlock) {
        CameraReportCommands camReportCommands = workspaceBlock.getMenuValue(ENTITY, this.menuCameraReportCommand);
        return camReportCommands.reportFn.apply(workspaceBlock, this);
    }

    private void recordCameraHandler(WorkspaceBlock workspaceBlock) {
        VideoBaseStorageService<?> storage = workspaceBlock.getMenuValueEntity("FS", this.menuCameraRecord);
        String output = workspaceBlock.getInputString("NAME");

        CameraWithProfile camera = getCameraProfile(workspaceBlock);

        workspaceBlock.handleAndRelease(
                () -> storage.startRecord(output, output, camera.profile, camera.entity, entityContext),
                () -> storage.stopRecord(output, output, camera.entity));
    }

    @SneakyThrows
    private RawType getCameraWithProfileParameterReporter(WorkspaceBlock workspaceBlock) {
        CameraWithProfile cameraProfile = getCameraProfile(workspaceBlock);
        CameraProfileReportCommands command = workspaceBlock.getMenuValue(ENTITY, this.menuCameraProfileReportCommand);
        return command.handler.handle(workspaceBlock, this, cameraProfile);
    }

    private void setIntParamCommand(WorkspaceBlock workspaceBlock) {
        workspaceBlock.getMenuValue(ENTITY, this.menuIntParam).handler
                .handle(getEntity(workspaceBlock, this.menuFfmpegCamera), workspaceBlock.getInputInteger(VALUE), this,
                        workspaceBlock);
    }

    private void setBoolParamCommand(WorkspaceBlock workspaceBlock) {
        workspaceBlock.getMenuValue(ENTITY, this.menuBoolParam).handler
                .handle(getEntity(workspaceBlock, this.menuFfmpegCamera), workspaceBlock.getMenuValue(ON_OFF, this.menuOnOff),
                        workspaceBlock);
    }

    private RawType getGifMP4Reporter(WorkspaceBlock workspaceBlock) {
        Integer time = workspaceBlock.getInputInteger("TIME");
        State timeStr = workspaceBlock.getValue("time");
        if (timeStr != null) {
            time = timeStr.intValue();
        }
        CameraWithProfile camera = getCameraProfile(workspaceBlock);
        RecordType recordType = workspaceBlock.getMenuValue(RECORD_TYPE, this.menuRecordType);
        return new RawType(recordType.getHandler.getData(camera.entity.getService(), camera.profile, time),
                recordType.mimeType, camera.entity.getTitle() + " " + recordType.name());
    }

    private void fireRecordGifMP4Command(WorkspaceBlock workspaceBlock) {
        CameraWithProfile camera = getCameraProfile(workspaceBlock);
        String fileName = workspaceBlock.getInputStringRequired("NAME");
        RecordType recordType = workspaceBlock.getMenuValue(RECORD_TYPE, this.menuRecordType);

        BaseCameraService<?, ?> service = camera.entity.getService();
        Path basePath = recordType.getBasePath(service);
        Path path = BaseCameraEntity.buildFilePathForRecord(basePath, fileName, recordType.ext);
        workspaceBlock.logInfo("Record <{}> to <{}>", recordType, path);
        recordType.recordHandler.record(service, path, camera.profile,
                workspaceBlock.getInputInteger("TIME"));
    }

    private void listenBoolHat(WorkspaceBlock workspaceBlock) {
        BoolHatMenuEnum boolHatMenuEnum = workspaceBlock.getMenuValue(VALUE, this.menuBoolHat);
        workspaceBlock.handleNext(next -> {
            BaseCameraEntity<?, ?> entity = getEntity(workspaceBlock, this.menuFfmpegCamera);

            workspaceBlock.onRelease(() -> entity.getService().removeMotionAlarmListener(workspaceBlock.getId()));
            entity.getService().addMotionAlarmListener(workspaceBlock.getId());

            OnOffType.OnOffTypeEnum onOffType = workspaceBlock.getMenuValue(ON_OFF, this.menuOnOff);
            boolHatMenuEnum.handler.handle(entity, onOffType, workspaceBlock, next);
        });
    }

    private <T extends BaseCameraEntity<?, ?>> T getEntity(WorkspaceBlock workspaceBlock,
                                                    MenuBlock.ServerMenuBlock serverMenuBlock) {
        BaseCameraEntity<?, ?> entity = workspaceBlock.getMenuValueEntityRequired(VIDEO_STREAM, serverMenuBlock);
        if (!entity.isStart()) {
            throw new RuntimeException("Video camera " + entity.getTitle() + " not started");
        }
        return (T) entity;
    }

    /* private void firePollImageCommand(WorkspaceBlock workspaceBlock) {
        getFFmpegHandler(workspaceBlock).startSnapshot();
    } */

    private CameraWithProfile getCameraProfile(WorkspaceBlock workspaceBlock) {
        String[] cameraWithProfile = workspaceBlock.getMenuValue(VIDEO_STREAM, menuFfmpegCameraWithProfiles).split("/");
        BaseCameraEntity<?, ?> cameraEntity = workspaceBlock.getEntityContext().getEntity(cameraWithProfile[0]);
        String profile = cameraWithProfile.length > 1 ? cameraWithProfile[1] : null;
        return new CameraWithProfile(cameraEntity, profile);
    }

    @RequiredArgsConstructor
    private enum SetCameraBoolParamEnum implements KeyValueEnum {
        IRLedValue("IR led value", (entity, onOffType, workspaceBlock) -> {
            if (entity instanceof OnvifCameraEntity) {
                Consumer<Boolean> handler = getBrandHandler(entity).getIRLedHandler();
                if (handler == null) {
                    workspaceBlock.logErrorAndThrow("Unable to find ir led handler for camera: <{}>", entity.getTitle());
                } else {
                    handler.accept(onOffType.boolValue());
                }
            } else {
                workspaceBlock.logErrorAndThrow("Unable to find ir led handler for camera: <{}>", entity.getTitle());
            }
        }),

        AudioAlarm("Audio alarm", (entity, onOffType, workspaceBlock) ->
            entity.getService().audioDetected(onOffType.boolValue())),

        MotionAlarm("Motion alarm", (entity, onOffType, workspaceBlock) ->
            entity.getService().motionDetected(onOffType.boolValue(), ExternalMotionAlarm));

        @Getter
        private final String value;

        private final SetHandler handler;

        private interface SetHandler {

            void handle(BaseCameraEntity<?, ?> entity, OnOffType.OnOffTypeEnum onOffType, WorkspaceBlock workspaceBlock);
        }
    }

    @RequiredArgsConstructor
    private enum BoolHatMenuEnum implements KeyValueEnum {
        AudioAlarm("Audio alarm", (entity, onOffType, workspaceBlock, next) -> {
            BroadcastLock audioAlarm = workspaceBlock.getBroadcastLockManager().getOrCreateLock(workspaceBlock,
                ENDPOINT_ENABLE_AUDIO_ALARM + ":" + entity.getEntityID());
            workspaceBlock.subscribeToLock(audioAlarm, state -> ((OnOffType) state).boolValue() == onOffType.boolValue(),
                    next::handle);
        }),

        MotionAlarm("Motion alarm", (entity, onOffType, workspaceBlock, next) -> {
            BroadcastLock motionAlarm =
                workspaceBlock.getBroadcastLockManager().getOrCreateLock(workspaceBlock, AlarmEvents.MotionAlarm + ":" + entity.getEntityID());
            workspaceBlock.subscribeToLock(motionAlarm, state -> ((OnOffType) state).boolValue() == onOffType.boolValue(),
                    next::handle);
        });

        @Getter
        private final String value;
        private final AlarmHandler handler;

        private interface AlarmHandler {

            void handle(BaseCameraEntity<?, ?> entity, OnOffType.OnOffTypeEnum onOffType,
                        WorkspaceBlock workspaceBlock, WorkspaceBlock next);
        }
    }

    @RequiredArgsConstructor
    private enum SetCameraIntParamEnum implements KeyValueEnum {
        AudioAlarm("Audio alarm", (entity, value, scratch, workspaceBlock) ->
            entity.setAudioThreshold(value)),

        MotionAlarm("Motion alarm", (entity, value, scratch, workspaceBlock) ->
            entity.setMotionThreshold(value));

        @Getter
        private final String value;
        private final SetCameraIntParamHandler handler;

        private interface SetCameraIntParamHandler {

            void handle(BaseCameraEntity<?, ?> entity, int value, Scratch3CameraBlocks scratch,
                        WorkspaceBlock workspaceBlock);
        }
    }

    @RequiredArgsConstructor
    private enum CameraProfileReportCommands implements KeyValueEnum {
        Snapshot("Snapshot", (workspaceBlock, scratch, cameraProfile) -> {
            BaseCameraService<?, ?> service = cameraProfile.entity.getService();
            return new RawType(service.getSnapshot(), MimeTypeUtils.IMAGE_JPEG_VALUE, "snapshot.jpg");
        }),
        LastPlayback("Last playback", (workspaceBlock, scratch, cameraProfile) -> {
            BaseCameraEntity<?, ?> entity = cameraProfile.entity;
            if (entity instanceof CameraPlaybackStorage cameraPlaybackStorage) {
                String profile = cameraProfile.profile;
                CameraPlaybackStorage.PlaybackFile playbackFile = cameraPlaybackStorage.getLastPlaybackFile(workspaceBlock.getEntityContext(), profile);
                if (playbackFile == null) {
                    return null;
                }
                Path path = CommonUtils.getMediaPath().resolve("camera").resolve(entity.getEntityID()).resolve("playback")
                        .resolve(playbackFile.id);

                DownloadFile downloadFile;

                if (Files.exists(path)) {
                    downloadFile = new DownloadFile(new UrlResource(path.toUri()), Files.size(path),
                            playbackFile.id, null);
                } else {
                    downloadFile = Failsafe
                            .with(PLAYBACK_DOWNLOAD_FILE_RETRY_POLICY).onFailure(event ->
                                    log.error("Unable to download playback file: <{}>. <{}>. Msg: <{}>", entity.getTitle(), playbackFile.id,
                                            CommonUtils.getErrorMessage(event.getException())))
                            .get(context -> {
                                log.info("Reply <{}>. Download playback video file <{}>. <{}>", context.getAttemptCount(), entity.getTitle(),
                                        playbackFile.id);
                                return cameraPlaybackStorage.downloadPlaybackFile(workspaceBlock.getEntityContext(), "main", playbackFile.id, path);
                            });
                }
                return new RawType(IOUtils.toByteArray(downloadFile.stream().getInputStream()), "video/mp4", playbackFile.name);
            }
            workspaceBlock.logErrorAndThrow("Camera not support playback storage");
            return null;
        });

        @Getter
        private final String value;
        private final CameraProfileHandler handler;

        interface CameraProfileHandler {

            RawType handle(WorkspaceBlock workspaceBlock, Scratch3CameraBlocks scratch3CameraBlocks,
                           CameraWithProfile cameraProfile) throws IOException;
        }
    }

    @RequiredArgsConstructor
    private enum CameraReportCommands implements KeyValueEnum {
        IRValue("IR led value", (workspaceBlock, scratch) -> {
            BaseCameraEntity<?, ?> entity = scratch.getEntity(workspaceBlock, scratch.menuFfmpegCamera);
            if (entity instanceof OnvifCameraEntity) {
                Supplier<Boolean> handler = getBrandHandler(entity).getIrLedValueHandler();
                if (handler == null) {
                    workspaceBlock.logErrorAndThrow("Unable to find ir led get value handler for camera: <{}>",
                            entity.getTitle());
                } else {
                    return OnOffType.of(handler.get());
                }
            } else {
                workspaceBlock.logErrorAndThrow("Unable to find ir led get value handler for camera: <{}>", entity.getTitle());
            }
            return null;
        }),
        AudioAlarmThreshold("Audio alarm threshold", (workspaceBlock, scratch) ->
            new DecimalType(scratch.getEntity(workspaceBlock, scratch.menuFfmpegCamera).getAudioThreshold())),
        MotionAlarmThreshold("Motion alarm threshold", (workspaceBlock, scratch) ->
            new DecimalType(scratch.getEntity(workspaceBlock, scratch.menuFfmpegCamera).getMotionThreshold())),
        LastImage("Last image", (workspaceBlock, scratch) ->
            new RawType(
                scratch.getEntity(workspaceBlock, scratch.menuFfmpegCamera).getSnapshot(),
                MimeTypeUtils.IMAGE_JPEG_VALUE));

        @Getter
        private final String value;
        private final BiFunction<WorkspaceBlock, Scratch3CameraBlocks, State> reportFn;
    }

    @RequiredArgsConstructor
    public enum RecordType {
        Gif("image/gif", "gif", BaseCameraService::recordGifSync, BaseCameraService::recordGifAsync),
        Mp4("video/mp4", "mp4", BaseCameraService::recordMp4Sync, BaseCameraService::recordMp4Async);
        public final String mimeType;
        public final String ext;
        private final GetDataHandler getHandler;
        private final RecordHandler recordHandler;

        public Path getBasePath(BaseCameraService<?, ?> service) {
            if (this == RecordType.Gif) {
                return service.getFfmpegGifOutputPath();
            }
            return service.getFfmpegMP4OutputPath();
        }

        private interface GetDataHandler {

            byte[] getData(BaseCameraService<?, ?> service, String profile, int time);
        }

        private interface RecordHandler {

            void record(BaseCameraService<?, ?> service, Path filePath, String profile, int time);
        }
    }

    @AllArgsConstructor
    private static class CameraWithProfile {

        private BaseCameraEntity<?, ?> entity;
        private String profile;
    }
}
