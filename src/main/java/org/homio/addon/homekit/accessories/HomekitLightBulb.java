package org.homio.addon.homekit.accessories;

import io.github.hapjava.accessories.LightbulbAccessory;
import io.github.hapjava.characteristics.HomekitCharacteristicChangeCallback;
import io.github.hapjava.characteristics.impl.common.OnCharacteristic;
import io.github.hapjava.characteristics.impl.lightbulb.BrightnessCharacteristic;
import io.github.hapjava.characteristics.impl.lightbulb.HueCharacteristic;
import io.github.hapjava.characteristics.impl.lightbulb.SaturationCharacteristic;
import io.github.hapjava.services.impl.LightbulbService;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.homekit.HomekitEndpointContext;
import org.homio.api.ContextVar;
import org.homio.api.state.StringType;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.homio.addon.homekit.enums.HomekitCharacteristicType.Hue;

@Log4j2
public class HomekitLightBulb extends AbstractHomekitAccessory<OnCharacteristic> implements LightbulbAccessory {
    public HomekitLightBulb(@NotNull HomekitEndpointContext ctx) {
        super(ctx, OnCharacteristic.class, LightbulbService.class);
        String hue = getEndpoint().getHue();
        String saturation = getEndpoint().getSaturation();
        String brightness = getEndpoint().getBrightness();
        String combinedColor = getEndpoint().getCombinedColor();
        if (hue.isEmpty() && saturation.isEmpty() && brightness.isEmpty() && !combinedColor.isEmpty()) {
            var v = ctx.getVariable(combinedColor);
            addCharacteristic(Hue, createHueCharacteristic(ctx, v), v);
            addCharacteristic(Hue, createSaturationCharacteristic(ctx, v), v);
            addCharacteristic(Hue, createBrightnessCharacteristic(ctx, v), v);
        }
        log.info("[{}]: {} Created HomekitLightBulb accessory", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
    }

    private static @NotNull HueCharacteristic createHueCharacteristic(HomekitEndpointContext c, ContextVar.Variable v) {
        return new HueCharacteristic(() -> {
            float[] hsbValues = getHsbValuesFromHex(v.getValue().stringValue());
            return completedFuture(hsbValues[0] * 360D);
        }, newHue -> {
            float[] hsb = getHsbValuesFromHex(v.getValue().stringValue());
            v.set(new StringType(hsbToHexString(newHue, hsb[1] * 100f, hsb[2] * 100f)));
        }, callback -> v.addListener("hue-" + c.endpoint().getEntityID(),
                state -> v.addListener("hue-" + c.endpoint().getEntityID(), state1 -> callback.changed())),
                () -> v.removeListener("hue-" + c.endpoint().getEntityID()));
    }

    private static @NotNull SaturationCharacteristic createSaturationCharacteristic(HomekitEndpointContext c, ContextVar.Variable v) {
        return new SaturationCharacteristic(
                () -> {
                    float[] hsb = getHsbValuesFromHex(v.getValue().stringValue());
                    return completedFuture(hsb[1] * 100D);
                },
                newSaturation -> {
                    float[] hsb = getHsbValuesFromHex(v.getValue().stringValue());
                    v.set(new StringType(hsbToHexString(hsb[0] * 360f, newSaturation, hsb[2] * 100f)));
                },
                callback -> v.addListener("saturation-" + c.endpoint().getEntityID(),
                        state -> callback.changed()),
                () -> v.removeListener("saturation-" + c.endpoint().getEntityID())
        );
    }

    private static @NotNull BrightnessCharacteristic createBrightnessCharacteristic(HomekitEndpointContext c, ContextVar.Variable v) {
        return new BrightnessCharacteristic(
                () -> {
                    float[] hsb = getHsbValuesFromHex(v.getValue().stringValue());
                    return completedFuture((int) Math.round(hsb[2] * 100D));
                },
                newBrightness -> {
                    float[] hsb = getHsbValuesFromHex(v.getValue().stringValue());
                    v.set(new StringType(hsbToHexString(hsb[0] * 360f, hsb[1] * 100f, newBrightness)));
                },
                callback -> v.addListener("brightness-" + c.endpoint().getEntityID(),
                        state -> callback.changed()),
                () -> v.removeListener("brightness-" + c.endpoint().getEntityID())
        );
    }

    public static String hsbToHexString(double hue, double saturation, double brightness) {
        float h = (float) (hue / 360f);
        float s = (float) (saturation / 100f);
        float b = (float) (brightness / 100f);

        var color = java.awt.Color.getHSBColor(h, s, b);
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }

    private static float[] getHsbValuesFromHex(String hexColor) {
        var color = java.awt.Color.decode(hexColor);
        int r = color.getRed();
        int g = color.getGreen();
        int b = color.getBlue();
        return java.awt.Color.RGBtoHSB(r, g, b, null);
    }

    @Override
    public CompletableFuture<Boolean> getLightbulbPowerState() {
        log.debug("[{}]: {} Getting lightbulb power state", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
        CompletableFuture<Boolean> future = masterCharacteristic.getValue();
        future.whenComplete((state, ex) -> {
            if (ex != null) {
                log.error("[{}]: {} Failed to get lightbulb power state", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType(), ex);
            } else {
                log.info("[{}]: {} Lightbulb power state: {}", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType(), state);
            }
        });
        return future;
    }

    @SneakyThrows
    @Override
    public CompletableFuture<Void> setLightbulbPowerState(boolean value) {
        log.info("[{}]: {} Setting lightbulb power state to: {}", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType(), value);
        masterCharacteristic.setValue(value);
        return completedFuture(null);
    }

    @Override
    public void subscribeLightbulbPowerState(HomekitCharacteristicChangeCallback callback) {
        log.info("[{}]: {} Subscribing to lightbulb power state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
        masterCharacteristic.subscribe(callback);
    }

    @Override
    public void unsubscribeLightbulbPowerState() {
        log.info("[{}]: {} Unsubscribing from lightbulb power state changes", ctx.owner().getEntityID(), ctx.endpoint().getAccessoryType());
        masterCharacteristic.unsubscribe();
    }
}
