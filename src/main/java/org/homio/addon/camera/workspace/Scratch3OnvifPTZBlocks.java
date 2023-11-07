package org.homio.addon.camera.workspace;

import static org.homio.addon.camera.CameraConstants.ENDPOINT_PAN;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_PAN_CONTINUOUS;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_TILT;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_TILT_CONTINUOUS;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_ZOOM;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_ZOOM_CONTINUOUS;

import de.onvif.soap.devices.PtzDevices;
import java.util.function.Function;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.camera.CameraEntrypoint;
import org.homio.addon.camera.entity.IpCameraEntity;
import org.homio.addon.camera.service.IpCameraService;
import org.homio.api.Context;
import org.homio.api.state.DecimalType;
import org.homio.api.state.State;
import org.homio.api.state.StringType;
import org.homio.api.workspace.WorkspaceBlock;
import org.homio.api.workspace.scratch.MenuBlock;
import org.homio.api.workspace.scratch.MenuBlock.ServerMenuBlock;
import org.homio.api.workspace.scratch.Scratch3ExtensionBlocks;
import org.springframework.stereotype.Component;

@Log4j2
@Getter
@Component
public class Scratch3OnvifPTZBlocks extends Scratch3ExtensionBlocks {

    private final MenuBlock.StaticMenuBlock<PanActionType> menuPanActionType;
    private final MenuBlock.StaticMenuBlock<TiltActionType> menuTiltActionType;
    private final MenuBlock.StaticMenuBlock<ZoomActionType> menuZoomActionType;
    private final MenuBlock.ServerMenuBlock menuPreset;
    private final ServerMenuBlock menuPanTiltCamera;
    private final ServerMenuBlock menuZoomCamera;
    private final ServerMenuBlock menuPresetCamera;

    public Scratch3OnvifPTZBlocks(Context context, CameraEntrypoint cameraEntrypoint) {
        super("#4F4BA6", context, cameraEntrypoint, "onvifptz");
        setParent(ScratchParent.media);

        // Menu
        this.menuPanTiltCamera = menuServer("panMenu", "rest/media/video/devices/pan", "Tilt/Pan camera");
        this.menuZoomCamera = menuServer("zoomMenu", "rest/media/video/devices/zoom", "Zoom camera");
        this.menuPresetCamera = menuServer("presetMenu", "rest/media/video/devices/presets", "Presets camera");

        this.menuPanActionType = menuStatic("panActionTypeMenu", PanActionType.class, PanActionType.Left);
        this.menuTiltActionType = menuStatic("tiltActionTypeMenu", TiltActionType.class, TiltActionType.Up);
        this.menuZoomActionType = menuStatic("zoomActionTypeMenu", ZoomActionType.class, ZoomActionType.In);
        this.menuPreset = menuServer("presets", "rest/media/video/presets", "-")
            .setDependency(menuPresetCamera);

        buildReport(50, "ptz_tilt_pct", "tilt", menuPanTiltCamera, PtzDevices::getCurrentPanPercentage);
        buildReport(55, "ptz_pan_pct", "pan", menuPanTiltCamera, PtzDevices::getCurrentPanPercentage);
        buildReport(60, "ptz_zoom_pct", "zoom", menuZoomCamera, PtzDevices::getCurrentZoomPercentage);

        blockCommand(200, "pan", "Pan [VALUE] of [VIDEO_STREAM]", this::firePanCommand,
                block -> {
                    block.addArgument(VALUE, 0);
                    block.addArgument(Scratch3CameraBlocks.VIDEO_STREAM, menuPanTiltCamera);
                });

        blockCommand(210, "pan_cmd", "Pan [VALUE] of [VIDEO_STREAM]", this::firePanActionCommand,
                block -> {
                    block.addArgument(Scratch3CameraBlocks.VIDEO_STREAM, menuPanTiltCamera);
                    block.addArgument(VALUE, this.menuPanActionType);
                });

        blockCommand(220, "tilt", "Tilt [VALUE] of [VIDEO_STREAM]", this::fireTiltCommand, block -> {
            block.addArgument(VALUE, 0);
            block.addArgument(Scratch3CameraBlocks.VIDEO_STREAM, menuPanTiltCamera);
        });

        blockCommand(230, "tilt_cmd", "Tilt [VALUE] of [VIDEO_STREAM]", this::fireTiltActionCommand,
                block -> {
                    block.addArgument(Scratch3CameraBlocks.VIDEO_STREAM, menuPanTiltCamera);
                    block.addArgument(VALUE, this.menuTiltActionType);
                });

        blockCommand(240, "zoom", "Zoom [VALUE] of [VIDEO_STREAM]", this::fireZoomCommand,
                block -> {
                    block.addArgument(VALUE, 0);
                    block.addArgument(Scratch3CameraBlocks.VIDEO_STREAM, menuZoomCamera);
                });

        blockCommand(250, "zoom_cmd", "Zoom [VALUE] of [VIDEO_STREAM]", this::fireZoomActionCommand,
                block -> {
                    block.addArgument(Scratch3CameraBlocks.VIDEO_STREAM, menuZoomCamera);
                    block.addArgument(VALUE, this.menuZoomActionType);
                });

        blockCommand(260, "to_preset", "Go to preset [PRESET] of [VIDEO_STREAM]", this::fireGoToPresetCommand,
                block -> {
                    block.addArgument(Scratch3CameraBlocks.VIDEO_STREAM, menuPresetCamera);
                    block.addArgument("PRESET", this.menuPreset);
                });
    }

    private void buildReport(int order, String opcode, String text, ServerMenuBlock menu, Function<PtzDevices, Float> fetcher) {
        blockReporter(order, opcode, text + " of [VIDEO_STREAM]", workspaceBlock -> {
                IpCameraService service = getOnvifService(workspaceBlock, menu);
                return new DecimalType(fetcher.apply(service.getOnvifDeviceState().getPtzDevices()));
            },
            block -> block.addArgument(Scratch3CameraBlocks.VIDEO_STREAM, menuPanTiltCamera));
    }

    private void fireGoToPresetCommand(WorkspaceBlock workspaceBlock) {
        String token = workspaceBlock.getMenuValue("presets", this.menuPreset);
        IpCameraService service = getOnvifService(workspaceBlock, menuPresetCamera);
        service.getOnvifDeviceState().getPtzDevices().gotoPreset(service.getProfile(), token);
    }

    private void fireZoomActionCommand(WorkspaceBlock workspaceBlock) {
        String command = workspaceBlock.getMenuValue(VALUE, this.menuZoomActionType).name().toUpperCase();
        setEndpointValue(menuZoomCamera, workspaceBlock, ENDPOINT_ZOOM_CONTINUOUS, new StringType(command));
    }

    private void fireZoomCommand(WorkspaceBlock workspaceBlock) {
        setEndpointValue(menuZoomCamera, workspaceBlock, ENDPOINT_ZOOM, new DecimalType(workspaceBlock.getInputFloat(VALUE)));
    }

    private void fireTiltActionCommand(WorkspaceBlock workspaceBlock) {
        String command = workspaceBlock.getMenuValue(VALUE, this.menuTiltActionType).name().toUpperCase();
        setEndpointValue(menuPanTiltCamera, workspaceBlock, ENDPOINT_TILT_CONTINUOUS, new StringType(command));
    }

    private void fireTiltCommand(WorkspaceBlock workspaceBlock) {
        setEndpointValue(menuPanTiltCamera, workspaceBlock, ENDPOINT_TILT, new DecimalType(workspaceBlock.getInputFloat(VALUE)));
    }

    private void firePanActionCommand(WorkspaceBlock workspaceBlock) {
        String command = workspaceBlock.getMenuValue(VALUE, this.menuPanActionType).name().toUpperCase();
        setEndpointValue(menuPanTiltCamera, workspaceBlock, ENDPOINT_PAN_CONTINUOUS, new StringType(command));
    }

    private void firePanCommand(WorkspaceBlock workspaceBlock) {
        setEndpointValue(menuPanTiltCamera, workspaceBlock, ENDPOINT_PAN, new DecimalType(workspaceBlock.getInputFloat(VALUE)));
    }

    private IpCameraService getOnvifService(WorkspaceBlock workspaceBlock, ServerMenuBlock menu) {
        IpCameraEntity entity = workspaceBlock.getMenuValueEntityRequired(Scratch3CameraBlocks.VIDEO_STREAM, menu);
        return entity.getService();
    }

    private void setEndpointValue(ServerMenuBlock menu, WorkspaceBlock workspaceBlock, String endpoint, State state) {
        getOnvifService(workspaceBlock, menu).getEndpoints().get(endpoint).setValue(state, true);
    }

    private enum PanActionType {
        Left, Right, Off
    }

    private enum TiltActionType {
        Up, Down, Off
    }

    private enum ZoomActionType {
        In, Out, Off
    }
}
