package org.homio.addon.camera.workspace;

import static org.homio.api.EntityContextMedia.FFMPEGFormat.RTSP_ALARMS;
import static org.homio.api.util.CommonUtils.addToListSafe;

import com.pivovarit.function.ThrowingBiConsumer;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.camera.CameraEntrypoint;
import org.homio.api.EntityContext;
import org.homio.api.EntityContextMedia.FFMPEG;
import org.homio.api.EntityContextMedia.FFMPEGHandler;
import org.homio.api.state.DecimalType;
import org.homio.api.workspace.WorkspaceBlock;
import org.homio.api.workspace.scratch.Scratch3ExtensionBlocks;
import org.springframework.stereotype.Component;

@Log4j2
@Getter
@Component
public class Scratch3FFmpegBlocks extends Scratch3ExtensionBlocks {

    private static final int FFMPEG_WAIT_TO_START_TIMEOUT = 1000;

    public Scratch3FFmpegBlocks(EntityContext entityContext, CameraEntrypoint cameraEntrypoint) {
        super("#87B023", entityContext, cameraEntrypoint, "ffmpeg");
        setParent("media");

        blockCommand(10, FFmpegApplyHandler.argsInput.name(), "Input arg [VALUE]", this::skipHandler, block ->
                block.addArgument(VALUE, ""));
        blockCommand(20, FFmpegApplyHandler.argsOutput.name(), "Output arg [VALUE]", this::skipHandler, block ->
                block.addArgument(VALUE, ""));

        blockCommand(30, "fire_ffmpeg", "Run FFmpeg input [INPUT] output [OUTPUT]", this::fireFFmpegCommand, block -> {
            block.addArgument("INPUT", "");
            block.addArgument("OUTPUT", "");
        });
    }

    private void skipHandler(WorkspaceBlock workspaceBlock) {
        // skip execution
    }

    private void fireFFmpegCommand(WorkspaceBlock workspaceBlock) throws InterruptedException {
        String input = workspaceBlock.getInputString("INPUT");
        String output = workspaceBlock.getInputString("OUTPUT");
        FfmpegBuilder ffmpegBuilder = new FfmpegBuilder();
        applyParentBlocks(ffmpegBuilder, workspaceBlock.getParent());

        FFMPEG ffmpeg = entityContext.media().buildFFMPEG(workspaceBlock.getId(),
                "FFMPEG workspace", new FFMPEGHandler() {
                    @Override
                    public String getEntityID() {
                        return null;
                    }

                    @Override
                    public void motionDetected(boolean on, String key) {

                    }

                    @Override
                    public void audioDetected(boolean on) {

                    }

                    @Override
                    public void ffmpegError(String error) {
                        log.error("FFmpeg error: <{}>", error);

                    }

                    @Override
                    public DecimalType getMotionThreshold() {
                        return new DecimalType(30);
                    }
                }, log, RTSP_ALARMS, String.join(" ", ffmpegBuilder.inputArgs), input,
                String.join(" ", ffmpegBuilder.outputArgs),
                output, "", "", null);
        try {
            ffmpeg.startConverting();
            // wait to able process start
            Thread.sleep(FFMPEG_WAIT_TO_START_TIMEOUT);
            while (!Thread.currentThread().isInterrupted() && ffmpeg.getIsAlive()) {
                Thread.sleep(1000);
            }
        } catch (InterruptedException ex) { // thread was interrupted
            log.error("Error while wait finish for ffmpeg: <{}>", ex.getMessage());
            // if someone else interrupted thread and ffmpeg not started yet - wait 1 sec to allow process to be started
            if (!ffmpeg.getIsAlive()) {
                Thread.sleep(FFMPEG_WAIT_TO_START_TIMEOUT);
            }
        }

        ffmpeg.stopConverting();
        log.info("FFmpeg has been stopped");

       /* Path ffmpegPath = entityContext.setting().getValue(CameraFFMPEGInstallPathOptions.class);
        FfmpegInputDeviceHardwareRepository repository = entityContext.getBean(FfmpegInputDeviceHardwareRepository.class);
        repository.fireFfmpeg(ffmpegPath.toString(), input, source, output, -1);*/
    }

    @SneakyThrows
    private void applyParentBlocks(FfmpegBuilder ffmpegBuilder, WorkspaceBlock parent) {
        if (parent == null || !parent.getBlockId().startsWith("ffmpeg_args")) {
            return;
        }
        applyParentBlocks(ffmpegBuilder, parent.getParent());
        Scratch3FFmpegBlocks.FFmpegApplyHandler.valueOf(parent.getOpcode()).applyFn.accept(parent, ffmpegBuilder);
    }

    @AllArgsConstructor
    private enum FFmpegApplyHandler {
        argsInput((workspaceBlock, builder) -> {
            addToListSafe(builder.inputArgs, workspaceBlock.getInputString(VALUE).trim());
        }),
        argsOutput((workspaceBlock, builder) -> {
            addToListSafe(builder.outputArgs, workspaceBlock.getInputString(VALUE).trim());
        });

        private final ThrowingBiConsumer<WorkspaceBlock, FfmpegBuilder, Exception> applyFn;
    }

    private static class FfmpegBuilder {

        private final List<String> inputArgs = new ArrayList<>();
        private final List<String> outputArgs = new ArrayList<>();
    }
}
