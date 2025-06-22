package org.homio.addon.homekit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import io.github.hapjava.characteristics.impl.base.EnumCharacteristic;
import io.github.hapjava.characteristics.impl.thermostat.TemperatureDisplayUnitEnum;
import io.github.hapjava.server.impl.HomekitServer;
import io.github.hapjava.server.impl.crypto.HAPSetupCodeUtils;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.homio.addon.homekit.accessories.HomekitAccessoryFactory;
import org.homio.addon.homekit.accessories.HomekitThermostat;
import org.homio.api.Context;
import org.homio.api.ContextVar;
import org.homio.api.entity.CreateSingleEntity;
import org.homio.api.entity.device.DeviceEndpointsBehaviourContractStub;
import org.homio.api.entity.device.DeviceEntityAndSeries;
import org.homio.api.entity.log.HasEntityLog;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.endpoint.BaseDeviceEndpoint;
import org.homio.api.model.endpoint.DeviceEndpoint;
import org.homio.api.service.EntityService;
import org.homio.api.state.State;
import org.homio.api.ui.UI;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldPort;
import org.homio.api.ui.field.action.UIContextMenuAction;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.ui.route.UIRouteMisc;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings({"JpaAttributeTypeInspection", "unused"})
@Entity
@CreateSingleEntity
@UIRouteMisc(allowCreateItem = false)
public final class HomekitEntity extends DeviceEntityAndSeries<HomekitEndpointEntity> implements
        EntityService<HomekitService>,
        DeviceEndpointsBehaviourContractStub,
        HasEntityLog {

    @Override
    @UIField(order = 10, label = "homekitName")
    public String getName() {
        return super.getName();
    }

    @UIField(order = 11, inlineEdit = true)
    @UIFieldGroup("GENERAL")
    public boolean isStart() {
        return getJsonData("start", false);
    }

    public void setStart(boolean start) {
        setJsonData("start", start);
    }

    @UIField(order = 1, required = true, label = "homekitPin")
    @UIFieldGroup(value = "CONNECTION", order = 20, borderColor = "#248E91")
    public String getPin() {
        return getJsonData("pin", "031-45-154");
    }

    public void setPin(String value) {
        setJsonData("pin", value);
        regenerateQrCode();
    }

    @UIField(order = 2, disableEdit = true)
    @UIFieldGroup("CONNECTION")
    public String getSetupId() {
        return getJsonData("sid");
    }

    public void setSetupId(String id) {
        setJsonData("sid", id);
        regenerateQrCode();
    }

    @UIField(order = 3)
    @UIFieldPort(min = 1024)
    @UIFieldGroup("CONNECTION")
    public int getPort() {
        return getJsonData("port", 9123);
    }

    public void setPort(int value) {
        setJsonData("port", value);
    }

    @UIField(order = 4, disableEdit = true)
    @UIFieldGroup("CONNECTION")
    public String getQrCode() {
        return getJsonData("qrCode");
    }

    private void setQrCode(String value) {
        setJsonData("qrCode", value);
    }

    @UIField(order = 10)
    @UIFieldGroup("CONNECTION")
    public String getSalt() {
        return getJsonData("salt");
    }

    void setSalt(String salt) {
        setJsonData("salt", salt);
    }

    @UIField(order = 11, disableEdit = true)
    @UIFieldGroup("CONNECTION")
    public String getMac() {
        return getJsonData("mac");
    }

    public void setMac(String value) {
        setJsonData("mac", value);
    }

    @UIField(order = 1, hideInView = true)
    @UIFieldGroup(value = "MISC", order = 100, borderColor = "#4D912A")
    public String getManufacturer() {
        return getJsonData("manu", "Homio");
    }

    void setManufacturer(String value) {
        setJsonData("manu", value);
    }

    @UIField(order = 2, hideInView = true)
    @UIFieldGroup("MISC")
    public String getSerialNumber() {
        return getJsonData("ser", "none");
    }

    void setSerialNumber(String value) {
        setJsonData("ser", value);
    }

    @UIField(order = 3, disableEdit = true)
    @UIFieldGroup("MISC")
    public String getUsers() {
        return String.join(",", getUsersInternal().keySet());
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
            confirmMessageDialogColor = UI.Color.RED,
            confirmMessage = "W.CONFIRM.CLEAR_HOMEKIT_PAIRINGS")
    public ActionResponseModel clearHomekitPairings() {
        getService().clearHomekitPairings();
        return ActionResponseModel.success();
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

    // @UIField(order = 16, hideInEdit = true, type = UIFieldType.ImageBase64)
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
                .filter(e -> e.accessory() == null || !(e.accessory() instanceof HomekitAccessoryFactory.HomekitGroup))
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

    // user – encoded_key
    @JsonIgnore
    public Map<String, String> getUsersInternal() {
        return getJsonDataMap("users", String.class);
    }

    @UIContextMenuAction(value = "SHOW_QR_CODE", icon = "fas fa-qrcode",
            iconColor = UI.Color.BLUE, attachToField = "qrCode")
    public void showQrCode() {
        context().ui().dialog().topImageDialog("field.qrCode", "fas fa-qrcode", UI.Color.BLUE)
                .sendImage(getJsonData("qrImage"));
    }

    @Override
    public void logBuilder(@NotNull HasEntityLog.EntityLogBuilder logBuilder) {
        logBuilder.addTopicFilterByEntityID(HomekitEntity.class);
    }

    @Getter
    public static class HomekitEndpointUI extends BaseDeviceEndpoint<HomekitEntity> {

        private final @NotNull HomekitEndpointContext ctx;

        public HomekitEndpointUI(HomekitEndpointContext ctx) {
            super("HOMEKIT", ctx.service().context());
            this.ctx = ctx;
            setIgnoreDuplicates(false);
            setEndpointEntityID(ctx.endpoint().getEntityID());
            setDevice(ctx.owner());
            setEndpointType(EndpointType.string);
            if (ctx.accessory() == null) {
                return;
            }
            String type = ctx.accessory().getMasterCharacteristic().getType();
            ctx.characteristicsInfo(type).ifPresent(ci -> setIcon(ci.variable().getIconModel()));

            setInitialValue(getAccessoryValue());
            if (HomekitThermostat.class.isAssignableFrom(ctx.accessory().getClass())) {
                var unit = ctx.endpoint().getTemperatureUnit();
                setUnit(unit == TemperatureDisplayUnitEnum.CELSIUS ? "°C" : "°F");
            }
            ctx.setUpdateUI(() -> setValue(getAccessoryValue(), true));
        }

        private static @NotNull StringBuilder buildVariablesDescription(List<Item> items) {
            StringBuilder value = new StringBuilder("<table>");
            for (int i = 0; i < items.size(); i += 2) {
                value.append("<tr>");
                for (int j = 0; j < 2; j++) {
                    if (i + j < items.size()) {
                        var item = items.get(i + j);
                        String displayValue;

                        if (item.value != null && item.value.matches("#[0-9a-fA-F]{3}([0-9a-fA-F]{3})?")) {
                            displayValue = String.format(
                                    "<span style=\"padding:2px 2px 2px 12px;background:%s;\">%s</span>",
                                    item.value, item.value);
                        } else {
                            displayValue = item.value + item.unit;
                        }

                        value.append("""
                        <td style="padding:0;">
                            <span style="color:%s;">
                                <i class="fa-fw %s" style="margin-right: 2px;"></i>${field.%s}
                            </span>: %s
                        </td>
                        """.formatted(item.color, item.icon, item.name, displayValue));
                    } else {
                        value.append("<td style=\"padding:0;\"></td>");
                    }
                }
                value.append("</tr>");
            }
            value.append("</table>");
            return value;
        }

        private State getAccessoryValue() {
            try {
                var masterCharacteristic = ctx.accessory().getMasterCharacteristic();
                if (masterCharacteristic == null) return State.empty;
                if (masterCharacteristic instanceof EnumCharacteristic<?> ec) {
                    return State.of(ec.getEnumValue().get() + "(" + ec.getValue().get() + ")");
                }
                return State.of(masterCharacteristic.getValue().get());
            } catch (Exception e) {
                return State.empty;
            }
        }

        @Override
        public @NotNull String getName(boolean shortFormat) {
            var masterCharacteristic = ctx.accessory() == null ? null : ctx.accessory().getMasterCharacteristic();
            return "<span style=\"color:%s;font-size:11px\">[%s%s]</span> %s".formatted(
                    UI.Color.PRIMARY_COLOR,
                    ctx.endpoint().getAccessoryType().name(),
                    masterCharacteristic == null ? "" : " / ${field.%s}".formatted(ctx.getCharacteristicsInfo(masterCharacteristic.getClass()).name()),
                    ctx.endpoint().getTitle());
        }

        @Override
        public @Nullable String getDescription() {
            if (ctx.error() != null) {
                return "<span class=\"error\">" + ctx.error() + "</span>";
            }

            List<Item> items = new ArrayList<>();
            var masterCharacteristic = ctx.accessory().getMasterCharacteristic();
            ctx.characteristics().stream()
                    .filter(ch -> masterCharacteristic == null || !masterCharacteristic.getType().equals(ch.characteristic().getType()))
                    .forEach(ch -> {
                        try {
                            items.add(new Item(ch.name(), ch.variable(), ch.characteristic().getValue().get()));
                        } catch (Exception ignored) {
                        }
                    });

            items.sort(Comparator.comparingInt(i -> i.name.length()));
            return buildVariablesDescription(items).toString();
        }
    }

    record Item(String name, String value, String icon, String color, String unit) {
        Item(String name, ContextVar.Variable v, Object value) {
            this(name, StringUtils.defaultIfEmpty(Objects.toString(value, ""), "N/A"), v.getIcon(), v.getIconColor(), Objects.toString(v.getUnit(), ""));
        }
    }
}