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
import org.homio.addon.homekit.accessories.BaseHomekitAccessory;
import org.homio.addon.homekit.accessories.HomekitAccessoryFactory;
import org.homio.api.Context;
import org.homio.api.ContextVar;
import org.homio.api.entity.CreateSingleEntity;
import org.homio.api.entity.device.DeviceEndpointsBehaviourContractStub;
import org.homio.api.entity.device.DeviceEntityAndSeries;
import org.homio.api.model.endpoint.BaseDeviceEndpoint;
import org.homio.api.model.endpoint.DeviceEndpoint;
import org.homio.api.service.EntityService;
import org.homio.api.ui.UI;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldPort;
import org.homio.api.ui.field.UIFieldType;
import org.homio.api.ui.field.action.UIContextMenuAction;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.ui.route.UIRouteMisc;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;

@SuppressWarnings("JpaAttributeTypeInspection")
@Entity
@Log4j2
@CreateSingleEntity
@UIRouteMisc(allowCreateItem = false)
public final class HomekitEntity extends DeviceEntityAndSeries<HomekitEndpointEntity> implements
        EntityService<HomekitService>,
        DeviceEndpointsBehaviourContractStub {

    @Override
    @UIField(order = 10, label = "homekitName")
    public String getName() {
        return super.getName();
    }

    @UIField(order = 1, inlineEdit = true)
    @UIFieldGroup("GENERAL")
    public boolean isStart() {
        return getJsonData("start", false);
    }

    public void setStart(boolean start) {
        setJsonData("start", start);
    }

    @UIField(order = 2, required = true, label = "homekitPin")
    @UIFieldGroup("GENERAL")
    public String getPin() {
        return getJsonData("pin", "031-45-154");
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
    public String getDefaultName() {
        return "HomioBridge";
    }

    @Override
    public long getEntityServiceHashCode() {
        return getJsonDataHashCode("salt", "port", "sid", "qrCode", "pin", "pk", "mac") +
               getChildEntityHashCode();
    }

    @Override
    public HomekitService createService(@NotNull Context context) {
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

    @UIContextMenuAction(
            value = "CLEAR_HOMEKIT_PAIRINGS",
            icon = "fas fa-soap",
            iconColor = UI.Color.RED,
            confirmMessage = "W.CONFIRM.CLEAR_HOMEKIT_PAIRINGS")
    public void clearHomekitPairings() {
        getService().clearHomekitPairings();
    }

    @SneakyThrows
    @Override
    public void beforePersist() {
        super.beforePersist();
        setMac(HomekitServer.generateMac());
        setPrivateKey(Base64.getEncoder().encodeToString(HomekitServer.generateKey()));
        setSetupId(HAPSetupCodeUtils.generateSetupId());
        setSalt(HomekitServer.generateSalt().toString());
    }

    @UIField(order = 12)
    public String getSalt() {
        return getJsonData("salt");
    }

    void setSalt(String salt) {
        setJsonData("salt", salt);
    }

    @UIField(order = 10, disableEdit = true)
    public String getMac() {
        return getJsonData("mac");
    }

    public void setMac(String value) {
        setJsonData("mac", value);
    }

    @UIField(order = 16, hideInEdit = true, type = UIFieldType.ImageBase64)
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

    void setPrivateKey(String value) {
        setJsonData("pk", value);
    }

    @Override
    public @NotNull Map<String, ? extends DeviceEndpoint> getDeviceEndpoints() {
        return getService().getEndpoints().values()
                .stream()
                .filter(e -> e.accessory() != null)
                .filter(e -> !(e.accessory() instanceof HomekitAccessoryFactory.HomekitGroup))
                .map(HomekitEndpointUI::new)
                .collect(Collectors.toMap(HomekitEndpointUI::getEndpointEntityID, e -> e));
    }

    @Override
    public @Nullable String getDescription() {
        return null;
    }

    @Override
    protected @NotNull String getDevicePrefix() {
        return "homekit";
    }

    @Override
    public void assembleActions(UIInputBuilder uiInputBuilder) {
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

    @UIField(order = 100, hideInView = true)
    public String getManufacturer() {
        return getJsonData("manu", "Homio");
    }

    void setManufacturer(String value) {
        setJsonData("manu", value);
    }

    @UIField(order = 101, hideInView = true)
    public String getSerialNumber() {
        return getJsonData("ser", "none");
    }

    void setSerialNumber(String value) {
        setJsonData("ser", value);
    }

    @Getter
    public static class HomekitEndpointUI extends BaseDeviceEndpoint<HomekitEntity> {

        private final @NotNull HomekitEndpointEntity endpoint;
        private final BaseHomekitAccessory accessory;

        public HomekitEndpointUI(HomekitEndpointContext ctx) {
            super(ctx.accessory().getVariable().getIconModel(),
                    "HOMEKIT",
                    ctx.service().context(),
                    ctx.owner(),
                    ctx.endpoint().getTitle(),
                    false,
                    ctx.accessory().getVariable() == null ?
                            EndpointType.string :
                            ctx.accessory().getVariable().getRestriction().getEndpointType());
            if (ctx.accessory().getVariable() != null) {
                setInitialValue(ctx.accessory().getVariable().getValue());
            }
            this.endpoint = ctx.endpoint();
            this.accessory = ctx.accessory();
            ctx.updateUI = () -> setValue(ctx.accessory().getVariable().getValue(), true);
        }

        @Override
        public @Nullable String getDescription() {
            StringBuilder value = new StringBuilder(endpoint.getAccessoryType().name());
            if (!endpoint.getGroup().isEmpty()) {
                var color = UI.Color.random(endpoint.getGroup().hashCode());
                value.insert(0, "<span style=\"color: " + color + ";\">[" + endpoint.getGroup() + "]</span> ");
            }
            for (Map.Entry<String, ContextVar.Variable> entry : accessory.getExtraVariables().entrySet()) {
                value.append(" ${field.%s}=%s".formatted(entry.getKey(), entry.getValue().getValue()));
            }

            return value.toString();
        }
    }
}

