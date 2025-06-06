package org.homio.addon.homekit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import io.github.hapjava.server.impl.HomekitServer;
import io.github.hapjava.server.impl.crypto.HAPSetupCodeUtils;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.api.Context;
import org.homio.api.entity.CreateSingleEntity;
import org.homio.api.entity.HasTabEntities;
import org.homio.api.entity.device.DeviceEndpointsBehaviourContractStub;
import org.homio.api.entity.types.MiscEntity;
import org.homio.api.model.Icon;
import org.homio.api.model.endpoint.BaseDeviceEndpoint;
import org.homio.api.model.endpoint.DeviceEndpoint;
import org.homio.api.service.EntityService;
import org.homio.api.ui.UISidebarChildren;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldPort;
import org.homio.api.ui.field.UIFieldType;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings("JpaAttributeTypeInspection")
@Entity
@Log4j2
@CreateSingleEntity
@UISidebarChildren(icon = "fas fa-house-chimney-window", color = "#D6AD3A", allowCreateItem = false)
public class HomekitEntity extends MiscEntity implements EntityService<HomekitService>,
        DeviceEndpointsBehaviourContractStub, HasTabEntities<HomekitEndpointEntity> {

    @UIField(order = 1, inlineEdit = true)
    @UIFieldGroup("GENERAL")
    public boolean isStart() {
        return getJsonData("start", false);
    }

    public void setStart(boolean start) {
        setJsonData("start", start);
    }

    @UIField(order = 2)
    @UIFieldGroup("GENERAL")
    public String getPin() {
        return getJsonData("pin");
    }

    public void setPin(String value) {
        setJsonData("pin", value);
        regenerateQrCode();
    }

    @UIField(order = 3, disableEdit = true)
    public String getSetupId() {
        return getJsonData("sid");
    }

    public void setSetupId(String id) {
        setJsonData("sid", id);
        regenerateQrCode();
    }

    @UIField(order = 4)
    @UIFieldPort(min = 1024)
    public int getPort() {
        return getJsonData("port", 9123);
    }

    public void setPort(int value) {
        setJsonData("port", value);
    }

    @UIField(order = 5, disableEdit = true)
    public String getQrCode() {
        return getJsonData("qrCode");
    }

    private void setQrCode(String value) {
        setJsonData("qrCode", value);
    }

    @Override
    protected @NotNull String getDevicePrefix() {
        return "homekit";
    }

    @Override
    public @Nullable String getDefaultName() {
        return "HomioBridge";
    }

    @Override
    public long getEntityServiceHashCode() {
        return getJsonDataHashCode("instances", "port");
    }

    @Override
    public @NotNull Class<HomekitService> getEntityServiceItemClass() {
        return HomekitService.class;
    }

    @Override
    public @Nullable HomekitService createService(@NotNull Context context) {
        return new HomekitService(context, this);
    }

    @SneakyThrows
    private void regenerateQrCode() {
        if (!getPin().isEmpty() && !getSetupId().isEmpty()) {
            setQrCode(HAPSetupCodeUtils.getSetupURI(getPin().replaceAll("-", ""), getSetupId(), 1));
            BitMatrix matrix = new MultiFormatWriter().encode(getQrCode(), BarcodeFormat.QR_CODE, 200, 200);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            String qrImage = Base64.getEncoder().encodeToString(out.toByteArray());
            setQrImage("data:image/png;base64," + qrImage);
        }
    }

    @SneakyThrows
    @Override
    public void beforePersist() {
        super.beforePersist();
        setMac(HomekitServer.generateMac());
        setPrivateKey(Base64.getEncoder().encodeToString(HomekitServer.generateKey()));
        setSetupId(HAPSetupCodeUtils.generateSetupId());
        setPin("012-21-121");
        setSalt(HomekitServer.generateSalt().toString());
    }

    @UIField(order = 12)
    public String getSalt() {
        return getJsonData("salt");
    }

    private void setSalt(String salt) {
        setJsonData("salt", salt);
    }

    @UIField(order = 10, disableEdit = true)
    public String getMac() {
        return getJsonData("mac");
    }

    private void setMac(String value) {
        setJsonData("mac", value);
    }

    @UIField(order = 16, disableEdit = true, type = UIFieldType.ImageBase64)
    public String getQrImage() {
        return getJsonData("qrImage");
    }

    private void setQrImage(String value) {
        setJsonData("qrImage", value);
    }

    @JsonIgnore
    public String getPrivateKey() {
        return getJsonData("pk");
    }

    private void setPrivateKey(String value) {
        setJsonData("pk", value);
    }

    public Set<HomekitEndpointEntity> getItems() {
        return getJsonDataSet("eps", HomekitEndpointEntity.class);
    }

    @Override
    public void setItems(Set<HomekitEndpointEntity> items) {
        setJsonDataObject("eps", items);
    }

    @Override
    public @NotNull Map<String, ? extends DeviceEndpoint> getDeviceEndpoints() {
        return getService().getEndpoints().values().stream().map(homekitEndpointEntity -> new HomekitEndpointUI(homekitEndpointEntity, HomekitEntity.this)).collect(Collectors.toMap(HomekitEndpointUI::getId, e -> e));
    }

    @Override
    public void assembleActions(UIInputBuilder uiInputBuilder) {
        /*for (int i = 0; i < getInstances(); i++) {
            new HomekitEndpoint(getHomekitInstance(i), this).assembleUIAction(uiInputBuilder);
        }*/
    }

    @UIField(order = 20, disableEdit = true)
    public String getUsers() {
        return String.join(",", getUsersInternal().keySet());
    }

    // user - encoded_key
    @JsonIgnore
    public Map<String, String> getUsersInternal() {
        return getJsonDataMap("users", String.class);
    }

    /*@UIContextMenuAction(value = "ADD_WIDGET", icon = "fas fa-globe", iconColor = "#4AA734")
    public void addEndpoint() {
        context().ui().dialog().sendDialogRequest("key", "title",
                (responseType, pressedButton, params) -> {
                    var endpoints = getRawEndpoints();
                    var newEndpoint = new EndpointDTO(params.get("name").asText(),
                            HomekitAccessoryType.valueOf(params.get("accessoryType").asText()))
                            .addVariable(HomekitCharacteristicType.valueOf(params.path("type_1`").asText()),
                                    context().var().getVariable(params.path("variable_1").asText()))
                            .addVariable(HomekitCharacteristicType.valueOf(params.path("type_2`").asText()),
                                    context().var().getVariable(params.path("variable_2").asText()));
                    if (endpoints.add(newEndpoint)) {
                        getService().addEndpoint(newEndpoint);
                    }
                }, new Consumer<DialogModel>() {
                    @Override
                    public void accept(DialogModel dialogEditor) {
                        dialogEditor.disableKeepOnUi();
                        dialogEditor.appearance(new Icon("fas fa-house-chimney-window"), null);
                        List<ActionInputParameter> inputs = new ArrayList<>();
                        inputs.add(ActionInputParameter.textRequired("name", "Accessory name", 3, 20));
                        inputs.add(ActionInputParameter.select("accessoryType", SWITCH.name(), OptionModel.enumList(HomekitAccessoryType.class)));
                        dialogEditor
                                .submitButton("Create", button -> {
                                })
                                .group("General", inputs)
                                *//*.group("MandatoryCharacteristics", dialogGroup -> {
                                    dialogGroup.setBorderColor("#FFAAAA");
                                    var typeSelect = ActionInputParameter.select("type", HomekitCharacteristicType.EMPTY.name(), OptionModel.enumList(HomekitAccessoryType.class);
                                    var varSelect = ActionInputParameter.selectVariable("variable_1");
                                    dialogGroup.input(ActionInputParameter.multiItems("characteristic",
                                            List.of(typeSelect, varSelect)));
                                })
                                .group("OptionalCharacteristics", dialogGroup -> {
                                    dialogGroup.setBorderColor("#FF00FF");
                                    var typeSelect = ActionInputParameter.select("type", HomekitCharacteristicType.EMPTY.name(), OptionModel.enumList(HomekitAccessoryType.class);
                                    var varSelect = ActionInputParameter.selectVariable("variable_1");
                                    dialogGroup.input(ActionInputParameter.multiItems("characteristic",
                                            List.of(typeSelect, varSelect)));
                                })*//*;
                    }
                });
    }*/

   /* @JsonIgnore
    @NotNull Set<EndpointDTO> getRawEndpoints() {
        return getJsonDataSet("ep", EndpointDTO.class);
    }*/

    public static class HomekitEndpointUI extends BaseDeviceEndpoint<HomekitEntity> {

        @Getter
        private final String id;

        public HomekitEndpointUI(HomekitEndpointEntity endpoint, HomekitEntity device) {
            super(new Icon("fas fa-1"),
                    "Homekit 1",
                    device.context(),
                    device,
                    endpoint.getName(),
                    false,
                    DeviceEndpoint.EndpointType.string);
            this.id = String.valueOf(endpoint.getId());
        }

        public void assembleUIAction(@NotNull UIInputBuilder uiInputBuilder) {
            /*String html = Arrays.stream(getValue().toString().split(" "))
                    .collect(Collectors.joining("</div><div>", "<div>", "</div>"));
            uiInputBuilder.addInfo("<div class=\"dfc fs14\">%s</div>".formatted(html), UIInfoItemBuilder.InfoType.HTML);
            uiInputBuilder.addButton("qrCode", new Icon("fas fa-retweet", "#FF0000"),
                            (context, params) -> {
                                context.ui().dialog().topImageDialog("QR code", "fas fa-icon", "#FF00FF")
                                        .sendImage(settings.getQrImage());
                                return null;
                                //return sendRequest(Z2MDeviceEndpointFirmwareUpdate.Request.update);
                            })
                    .setText("QR")
                    .setConfirmMessage("W.CONFIRM.ZIGBEE_UPDATE")
                    .setConfirmMessageDialogColor(UI.Color.ERROR_DIALOG)
                    .setDisabled(!getDevice().getStatus().isOnline());*/
        }
    }
}
