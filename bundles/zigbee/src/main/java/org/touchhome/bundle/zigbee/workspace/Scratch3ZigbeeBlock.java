package org.touchhome.bundle.zigbee.workspace;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.scratch.BlockType;
import org.touchhome.bundle.api.scratch.MenuBlock;
import org.touchhome.bundle.api.scratch.Scratch3Block;
import org.touchhome.bundle.zigbee.ZigBeeDevice;
import org.touchhome.bundle.zigbee.converter.impl.ZigBeeConverterEndpoint;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class Scratch3ZigbeeBlock extends Scratch3Block {

    @Getter
    @JsonIgnore
    private LinkGeneratorHandler linkGenerator;

    @Getter
    @JsonIgnore
    private int linkClusterID;

    @Getter
    @JsonIgnore
    private String linkClusterName;

    @Getter
    @JsonIgnore
    private Integer linkClusterCount;

    @Getter
    @JsonIgnore
    private List<ZigBeeEventHandler> eventConsumers = new ArrayList<>();

    public Scratch3ZigbeeBlock(int order, String opcode, BlockType blockType, String text, Scratch3BlockHandler handler, Scratch3BlockEvaluateHandler evaluateHandler) {
        super(order, opcode, blockType, text, handler, evaluateHandler);
    }

    void setLinkGenerator(LinkGeneratorHandler linkGenerator, int clusterID) {
        setLinkGenerator(linkGenerator, clusterID, null, null);
    }

    void setLinkGenerator(LinkGeneratorHandler linkGenerator, int clusterID, String clusterName) {
        setLinkGenerator(linkGenerator, clusterID, null, clusterName);
    }

    void setLinkGenerator(LinkGeneratorHandler linkGenerator, int clusterID, int clusterCount) {
        setLinkGenerator(linkGenerator, clusterID, clusterCount, null);
    }

    void setLinkGenerator(LinkGeneratorHandler linkGenerator, int clusterID, Integer clusterCount, String linkClusterName) {
        this.linkGenerator = linkGenerator;
        this.linkClusterID = clusterID;
        this.linkClusterName = linkClusterName;
        this.linkClusterCount = clusterCount;
    }

    void addZigBeeEventHandler(ZigBeeEventHandler zigBeeEventHandler) {
        this.eventConsumers.add(zigBeeEventHandler);
    }

    public boolean matchLink(ZigBeeConverterEndpoint zigBeeConverterEndpoint, ZigBeeDevice zigBeeDevice) {
        return this.linkGenerator != null && zigBeeConverterEndpoint.getClusterId() == this.linkClusterID &&
                (this.linkClusterCount == null || this.linkClusterCount == zigBeeDevice.getChannelCount(this.linkClusterID)) &&
                (this.linkClusterName == null || this.linkClusterName.equals(zigBeeConverterEndpoint.getClusterName()));
    }

    void setDefaultLinkBooleanHandler(EntityContext entityContext, ZigBeeDeviceUpdateValueListener zigBeeDeviceUpdateValueListener, String sensorDescription, String sensorName, MenuBlock.ServerMenuBlock sensorMenu,
                                      int clusterId, String clusterName, String extensionId) {
        this.allowLinkBoolean((varId, workspaceBlock) ->
                Scratch3ZigBeeBlocks.linkVariable(zigBeeDeviceUpdateValueListener, varId, sensorDescription, workspaceBlock, sensorName, sensorMenu, clusterId, clusterName));
        this.setLinkGenerator((endpoint, zigBeeDevice, varGroup, varName) ->
                        codeGenerator(extensionId)
                                .setMenu(sensorMenu, zigBeeDevice.getNodeIeeeAddress())
                                .generateBooleanLink(varGroup, varName, entityContext),
                clusterId, clusterName);

    }

    void setDefaultLinkFloatHandler(EntityContext entityContext, ZigBeeDeviceUpdateValueListener zigBeeDeviceUpdateValueListener,
                                    String description, String sensorName, MenuBlock.ServerMenuBlock sensorMenu, int clusterId, String clusterName, String extensionId) {
        this.allowLinkFloatVariable((varId, workspaceBlock) ->
                Scratch3ZigBeeBlocks.linkVariable(zigBeeDeviceUpdateValueListener, varId, description, workspaceBlock, sensorName, sensorMenu));
        this.setLinkGenerator((endpoint, zigBeeDevice, varGroup, varName) ->
                this.codeGenerator(extensionId)
                        .setMenu(sensorMenu, zigBeeDevice.getNodeIeeeAddress())
                        .generateFloatLink(varGroup, varName, entityContext), clusterId, clusterName);
    }

    public interface ZigBeeEventHandler {
        void handle(String ieeeAddress, String endpointRef, Consumer<ScratchDeviceState> consumer);
    }

    public interface LinkGeneratorHandler {
        void handle(ZigBeeConverterEndpoint endpoint, ZigBeeDevice zigBeeDevice, String varGroup, String varName);
    }
}
