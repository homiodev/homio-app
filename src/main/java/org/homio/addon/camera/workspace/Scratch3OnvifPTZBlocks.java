package org.homio.addon.camera.workspace;

import static org.homio.addon.camera.CameraConstants.ENDPOINT_PAN;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_PAN_COMMAND;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_TILT;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_TILT_COMMAND;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_ZOOM;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_ZOOM_COMMAND;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.camera.CameraEntrypoint;
import org.homio.addon.camera.entity.IpCameraEntity;
import org.homio.addon.camera.service.IpCameraService;
import org.homio.api.EntityContext;
import org.homio.api.state.DecimalType;
import org.homio.api.state.State;
import org.homio.api.state.StringType;
import org.homio.api.workspace.WorkspaceBlock;
import org.homio.api.workspace.scratch.MenuBlock;
import org.homio.api.workspace.scratch.Scratch3ExtensionBlocks;
import org.springframework.stereotype.Component;

@Log4j2
@Getter
@Component
public class Scratch3OnvifPTZBlocks extends Scratch3ExtensionBlocks {

    private final MenuBlock.ServerMenuBlock menuOnvifCamera;
    private final MenuBlock.StaticMenuBlock<PanActionType> menuPanActionType;
    private final MenuBlock.StaticMenuBlock<TiltActionType> menuTiltActionType;
    private final MenuBlock.StaticMenuBlock<ZoomActionType> menuZoomActionType;
    private final MenuBlock.StaticMenuBlock<String> menuPreset;
    private final MenuBlock.StaticMenuBlock<GetPTZValueType> menuPtzValueType;

    public Scratch3OnvifPTZBlocks(EntityContext entityContext, CameraEntrypoint cameraEntrypoint) {
        super("#4F4BA6", entityContext, cameraEntrypoint, "onvifptz");
        setParent("media");

        // Menu
        this.menuOnvifCamera = menuServerItems("onvifCameraMenu", IpCameraEntity.class, "Onvif camera");
        this.menuPanActionType = menuStatic("panActionTypeMenu", PanActionType.class, PanActionType.Left);
        this.menuTiltActionType = menuStatic("tiltActionTypeMenu", TiltActionType.class, TiltActionType.Up);
        this.menuZoomActionType = menuStatic("zoomActionTypeMenu", ZoomActionType.class, ZoomActionType.In);
        this.menuPtzValueType = menuStatic("ptzValueTypeMenu", GetPTZValueType.class, GetPTZValueType.Pan);
        Map<String, String> presets = IntStream.range(1, 25).boxed().collect(Collectors.toMap(String::valueOf, num -> "Preset " + num));
        this.menuPreset = menuStaticList("presetMenu", presets, "1");

        blockReporter(50, "value_info", "Get [VALUE] of [VIDEO_STREAM]", this::getPTZValue,
                block -> {
                    block.addArgument(Scratch3CameraBlocks.VIDEO_STREAM, menuOnvifCamera);
                    block.addArgument(VALUE, menuPtzValueType);
                });

        blockCommand(200, "pan", "Pan [VALUE] of [VIDEO_STREAM]", this::firePanCommand,
                block -> {
                    block.addArgument(VALUE, 0);
                    block.addArgument(Scratch3CameraBlocks.VIDEO_STREAM, menuOnvifCamera);
                });

        blockCommand(210, "pan_cmd", "Pan [VALUE] of [VIDEO_STREAM]", this::firePanActionCommand,
                block -> {
                    block.addArgument(Scratch3CameraBlocks.VIDEO_STREAM, menuOnvifCamera);
                    block.addArgument(VALUE, this.menuPanActionType);
                });

        blockCommand(220, "tilt", "Tilt [VALUE] of [VIDEO_STREAM]", this::fireTiltCommand, block -> {
            block.addArgument(VALUE, 0);
            block.addArgument(Scratch3CameraBlocks.VIDEO_STREAM, menuOnvifCamera);
        });

        blockCommand(230, "tilt_cmd", "Tilt [VALUE] of [VIDEO_STREAM]", this::fireTiltActionCommand,
                block -> {
                    block.addArgument(Scratch3CameraBlocks.VIDEO_STREAM, menuOnvifCamera);
                    block.addArgument(VALUE, this.menuTiltActionType);
                });

        blockCommand(240, "zoom", "Zoom [VALUE] of [VIDEO_STREAM]", this::fireZoomCommand,
                block -> {
                    block.addArgument(VALUE, 0);
                    block.addArgument(Scratch3CameraBlocks.VIDEO_STREAM, menuOnvifCamera);
                });

        blockCommand(250, "zoom_cmd", "Zoom [VALUE] of [VIDEO_STREAM]", this::fireZoomActionCommand,
                block -> {
                    block.addArgument(Scratch3CameraBlocks.VIDEO_STREAM, menuOnvifCamera);
                    block.addArgument(VALUE, this.menuZoomActionType);
                });

        blockCommand(260, "to_preset", "Go to preset [PRESET] of [VIDEO_STREAM]", this::fireGoToPresetCommand,
                block -> {
                    block.addArgument(Scratch3CameraBlocks.VIDEO_STREAM, menuOnvifCamera);
                    block.addArgument("PRESET", this.menuPreset);
                });
    }

    private State getPTZValue(WorkspaceBlock workspaceBlock) {
        GetPTZValueType menu = workspaceBlock.getMenuValue(VALUE, this.menuPtzValueType);
        return menu.handler.apply(getOnvifService(workspaceBlock));
    }

    private void fireGoToPresetCommand(WorkspaceBlock workspaceBlock) {
        int preset = Integer.parseInt(workspaceBlock.getMenuValue("PRESET", this.menuPreset));
        getOnvifService(workspaceBlock).gotoPreset(preset);
    }

    private void fireZoomActionCommand(WorkspaceBlock workspaceBlock) {
        String command = workspaceBlock.getMenuValue(VALUE, this.menuZoomActionType).name().toUpperCase();
        setEndpointValue(workspaceBlock, ENDPOINT_ZOOM_COMMAND, new StringType(command));
    }

    private void fireZoomCommand(WorkspaceBlock workspaceBlock) {
        setEndpointValue(workspaceBlock, ENDPOINT_ZOOM, new DecimalType(workspaceBlock.getInputFloat(VALUE)));
    }

    private void fireTiltActionCommand(WorkspaceBlock workspaceBlock) {
        String command = workspaceBlock.getMenuValue(VALUE, this.menuTiltActionType).name().toUpperCase();
        setEndpointValue(workspaceBlock, ENDPOINT_TILT_COMMAND, new StringType(command));
    }

    private void fireTiltCommand(WorkspaceBlock workspaceBlock) {
        setEndpointValue(workspaceBlock, ENDPOINT_TILT, new DecimalType(workspaceBlock.getInputFloat(VALUE)));
    }

    private void firePanActionCommand(WorkspaceBlock workspaceBlock) {
        String command = workspaceBlock.getMenuValue(VALUE, this.menuPanActionType).name().toUpperCase();
        setEndpointValue(workspaceBlock, ENDPOINT_PAN_COMMAND, new StringType(command));
    }

    private void firePanCommand(WorkspaceBlock workspaceBlock) {
        setEndpointValue(workspaceBlock, ENDPOINT_PAN, new DecimalType(workspaceBlock.getInputFloat(VALUE)));
    }

    private IpCameraService getOnvifService(WorkspaceBlock workspaceBlock) {
        return getOnvifEntity(workspaceBlock).getService();
    }

    private IpCameraEntity getOnvifEntity(WorkspaceBlock workspaceBlock) {
        return workspaceBlock.getMenuValueEntityRequired(Scratch3CameraBlocks.VIDEO_STREAM, menuOnvifCamera);
    }

    private void setEndpointValue(WorkspaceBlock workspaceBlock, String endpoint, State state) {
        getOnvifService(workspaceBlock).getEndpoints().get(endpoint).setValue(state, true);
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

    @RequiredArgsConstructor
    private enum GetPTZValueType {
        Zoom(camera -> new DecimalType(camera.getOnvifDeviceState().getPtzDevices().getCurrentZoomPercentage())),
        Tilt(camera -> new DecimalType(camera.getOnvifDeviceState().getPtzDevices().getCurrentTiltPercentage())),
        Pan(camera -> new DecimalType(camera.getOnvifDeviceState().getPtzDevices().getCurrentPanPercentage()));

        private final Function<IpCameraService, State> handler;
    }
}
