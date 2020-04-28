package org.touchhome.bundle.zigbee.converter.impl;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.ZclAttributeListener;
import com.zsmartsystems.zigbee.zcl.clusters.ZclColorControlCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclLevelControlCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclOnOffCluster;
import com.zsmartsystems.zigbee.zcl.clusters.colorcontrol.ColorCapabilitiesEnum;
import com.zsmartsystems.zigbee.zcl.clusters.colorcontrol.ColorModeEnum;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.NotImplementedException;
import org.touchhome.bundle.zigbee.converter.ZigBeeBaseChannelConverter;
import org.touchhome.bundle.zigbee.converter.impl.config.ZclLevelControlConfig;
import org.touchhome.bundle.zigbee.model.DecimalType;
import org.touchhome.bundle.zigbee.model.HSBType;
import org.touchhome.bundle.zigbee.model.OnOffType;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.zsmartsystems.zigbee.zcl.clusters.ZclColorControlCluster.ATTR_CURRENTHUE;
import static com.zsmartsystems.zigbee.zcl.clusters.ZclColorControlCluster.ATTR_CURRENTSATURATION;

/**
 * Converter for color control. Uses the {@link ZclColorControlCluster}, and may also use the
 * {@link ZclLevelControlCluster} and {@link ZclOnOffCluster} if available.
 */
@Log4j2
@ZigBeeConverter(name = "zigbee:color_color",
        clientClusters = {ZclOnOffCluster.CLUSTER_ID, ZclLevelControlCluster.CLUSTER_ID, ZclColorControlCluster.CLUSTER_ID})
public class ZigBeeConverterColorColor extends ZigBeeBaseChannelConverter implements ZclAttributeListener {
    private final Object colorUpdateSync = new Object();
    private final AtomicBoolean currentOnOffState = new AtomicBoolean(true);
    private HSBType lastHSB = new HSBType("0,0,100");
    private ZclColorControlCluster clusterColorControl;
    private ZclLevelControlCluster clusterLevelControl;
    private ZclOnOffCluster clusterOnOff;
    private boolean delayedColorChange = false; // Wait for brightness transition before changing color
    private ScheduledExecutorService colorUpdateScheduler;
    private ScheduledFuture<?> colorUpdateTimer = null;
    private boolean supportsHue = false;
    private int lastHue = -1;
    private int lastSaturation = -1;
    private boolean hueChanged = false;
    private boolean saturationChanged = false;
    private int lastX = -1;
    private int lastY = -1;
    private boolean xChanged = false;
    private boolean yChanged = false;
    private ColorModeEnum lastColorMode;
    private ZclLevelControlConfig configLevelControl;

    @Override
    public boolean initializeDevice() {
        ZclColorControlCluster serverClusterColorControl = (ZclColorControlCluster) endpoint
                .getInputCluster(ZclColorControlCluster.CLUSTER_ID);
        if (serverClusterColorControl == null) {
            log.error("{}: Error opening device color controls", endpoint.getIeeeAddress());
            return false;
        }

        ZclLevelControlCluster serverClusterLevelControl = (ZclLevelControlCluster) endpoint
                .getInputCluster(ZclLevelControlCluster.CLUSTER_ID);
        if (serverClusterLevelControl == null) {
            log.warn("{}: Device does not support level control", endpoint.getIeeeAddress());
            return false;
        }

        ZclOnOffCluster serverClusterOnOff = (ZclOnOffCluster) endpoint.getInputCluster(ZclOnOffCluster.CLUSTER_ID);
        if (serverClusterOnOff == null) {
            log.debug("{}: Device does not support on/off control", endpoint.getIeeeAddress());
            return false;
        }

        if (!discoverSupportedColorCommands(serverClusterColorControl)) {
            return false;
        }

        // Bind to attribute reports, add listeners, then request the status
        // Configure reporting - no faster than once per second - no slower than 10 minutes.
        try {
            CommandResult bindResponse = bind(serverClusterColorControl).get();
            if (bindResponse.isSuccess()) {
                CommandResult reportingResponse;
                if (supportsHue) {
                    reportingResponse = serverClusterColorControl
                            .setReporting(serverClusterColorControl.getAttribute(ATTR_CURRENTHUE), 1,
                                    REPORTING_PERIOD_DEFAULT_MAX, 1)
                            .get();
                    handleReportingResponseHight(reportingResponse);

                    reportingResponse = serverClusterColorControl
                            .setReporting(serverClusterColorControl.getAttribute(ATTR_CURRENTSATURATION), 1,
                                    REPORTING_PERIOD_DEFAULT_MAX, 1)
                            .get();
                    handleReportingResponseHight(reportingResponse);
                } else {
                    reportingResponse = serverClusterColorControl
                            .setCurrentXReporting(1, REPORTING_PERIOD_DEFAULT_MAX, 1).get();
                    handleReportingResponseHight(reportingResponse);

                    reportingResponse = serverClusterColorControl
                            .setCurrentYReporting(1, REPORTING_PERIOD_DEFAULT_MAX, 1).get();
                    handleReportingResponseHight(reportingResponse);
                }
            } else {
                log.error("{}: Error 0x{} setting server binding", endpoint.getIeeeAddress(),
                        Integer.toHexString(bindResponse.getStatusCode()));
                pollingPeriod = POLLING_PERIOD_HIGH;
                return false;
            }
        } catch (ExecutionException | InterruptedException e) {
            log.debug("{}: Exception configuring color reporting", endpoint.getIeeeAddress(), e);
        }

        try {
            CommandResult bindResponse = bind(serverClusterLevelControl).get();
            if (!bindResponse.isSuccess()) {
                pollingPeriod = POLLING_PERIOD_HIGH;
            }
            CommandResult reportingResponse = serverClusterLevelControl
                    .setCurrentLevelReporting(1, REPORTING_PERIOD_DEFAULT_MAX, 1).get();
            handleReportingResponseHight(reportingResponse);
        } catch (ExecutionException | InterruptedException e) {
            log.debug("{}: Exception configuring level reporting", endpoint.getIeeeAddress(), e);
        }

        try {
            CommandResult bindResponse = bind(serverClusterOnOff).get();
            if (!bindResponse.isSuccess()) {
                pollingPeriod = POLLING_PERIOD_HIGH;
            }
            CommandResult reportingResponse = serverClusterOnOff.setOnOffReporting(1, REPORTING_PERIOD_DEFAULT_MAX)
                    .get();
            handleReportingResponseHight(reportingResponse);
        } catch (ExecutionException | InterruptedException e) {
            log.debug("{}: Exception configuring on/off reporting", endpoint.getIeeeAddress(), e);
            return false;
        }

        try {
            ZclAttribute colorModeAttribute = serverClusterColorControl
                    .getAttribute(ZclColorControlCluster.ATTR_COLORMODE);
            CommandResult reportingResponse = serverClusterColorControl
                    .setReporting(colorModeAttribute, 1, REPORTING_PERIOD_DEFAULT_MAX, 1).get();
            handleReportingResponseHight(reportingResponse);
        } catch (ExecutionException | InterruptedException e) {
            log.debug("{}: Exception configuring color mode reporting", endpoint.getIeeeAddress(), e);
            return false;
        }

        return true;
    }

    @Override
    public boolean initializeConverter() {
        colorUpdateScheduler = Executors.newSingleThreadScheduledExecutor();

        clusterColorControl = (ZclColorControlCluster) endpoint.getInputCluster(ZclColorControlCluster.CLUSTER_ID);
        if (clusterColorControl == null) {
            log.error("{}: Error opening device color controls", endpoint.getIeeeAddress());
            return false;
        }

        clusterLevelControl = (ZclLevelControlCluster) endpoint.getInputCluster(ZclLevelControlCluster.CLUSTER_ID);
        if (clusterLevelControl == null) {
            log.warn("{}: Device does not support level control", endpoint.getIeeeAddress());
        }

        clusterOnOff = (ZclOnOffCluster) endpoint.getInputCluster(ZclOnOffCluster.CLUSTER_ID);
        if (clusterOnOff == null) {
            log.debug("{}: Device does not support on/off control", endpoint.getIeeeAddress());
        }

        if (!discoverSupportedColorCommands(clusterColorControl)) {
            return false;
        }

        // Create a configuration handler and get the available options
        configLevelControl = new ZclLevelControlConfig();
        configLevelControl.initialize(clusterLevelControl);

        clusterColorControl.addAttributeListener(this);
        clusterLevelControl.addAttributeListener(this);
        clusterOnOff.addAttributeListener(this);

        return true;
    }

    @Override
    public void disposeConverter() {
        // Stop the timer and shutdown the scheduler
        if (colorUpdateTimer != null) {
            colorUpdateTimer.cancel(true);
            colorUpdateTimer = null;
        }
        colorUpdateScheduler.shutdownNow();

        clusterColorControl.removeAttributeListener(this);

        if (clusterLevelControl != null) {
            clusterLevelControl.removeAttributeListener(this);
        }

        if (clusterOnOff != null) {
            clusterOnOff.removeAttributeListener(this);
        }

        synchronized (colorUpdateSync) {
            if (colorUpdateTimer != null) {
                colorUpdateTimer.cancel(true);
            }
        }
    }

    @Override
    protected void handleRefresh() {
        if (clusterOnOff != null) {
            clusterOnOff.getOnOff(0);
        }

        if (supportsHue) {
            clusterColorControl.getCurrentHue(0);
            clusterColorControl.getCurrentSaturation(0);
        } else {
            clusterColorControl.getCurrentX(0);
            clusterColorControl.getCurrentY(0);
        }

        if (clusterLevelControl != null) {
            clusterLevelControl.getCurrentLevel(0);
        }

        clusterColorControl.getColorMode(0);
    }

    private void changeOnOff(OnOffType onoff) throws InterruptedException, ExecutionException {
        boolean on = onoff == OnOffType.ON;

        if (clusterOnOff == null) {
            if (clusterLevelControl == null) {
                log.warn("{}: ignoring on/off command", endpoint.getIeeeAddress());
            } else {
                changeBrightness(on ? DecimalType.HUNDRED : DecimalType.ZERO);
            }
            return;
        }

        if (on) {
            clusterOnOff.onCommand().get();
        } else {
            clusterOnOff.offCommand().get();
        }
    }

    private void changeBrightness(DecimalType brightness) throws InterruptedException, ExecutionException {
        if (clusterLevelControl == null) {
            if (clusterOnOff == null) {
                log.warn("{}: ignoring brightness command", endpoint.getIeeeAddress());
            } else {
                changeOnOff(brightness.intValue() == 0 ? OnOffType.OFF : OnOffType.ON);
            }
            return;
        }

        int level = percentToLevel(brightness);

        if (clusterOnOff != null) {
            if (brightness.equals(DecimalType.ZERO)) {
                clusterOnOff.offCommand();
            } else {
                clusterLevelControl.moveToLevelWithOnOffCommand(level, configLevelControl.getDefaultTransitionTime())
                        .get();
            }
        } else {
            clusterLevelControl.moveToLevelCommand(level, configLevelControl.getDefaultTransitionTime()).get();
        }
    }

    private void changeColorHueSaturation(HSBType color) throws InterruptedException, ExecutionException {
        int hue = (int) (color.getHue().floatValue() * 254.0f / 360.0f + 0.5f);
        int saturation = percentToLevel(color.getSaturation());

        clusterColorControl
                .moveToHueAndSaturationCommand(hue, saturation, configLevelControl.getDefaultTransitionTime()).get();
    }

    private void changeColorXY(HSBType color) throws InterruptedException, ExecutionException {
        DecimalType xy[] = color.toXY();

        log.debug("{}: Change Color HSV ({}, {}, {}) -> XY ({}, {})", endpoint.getIeeeAddress(), color.getHue(),
                color.getSaturation(), color.getBrightness(), xy[0], xy[1]);
        int x = (int) (xy[0].floatValue() / 100.0f * 65536.0f + 0.5f); // up to 65279
        int y = (int) (xy[1].floatValue() / 100.0f * 65536.0f + 0.5f); // up to 65279

        clusterColorControl.moveToColorCommand(x, y, configLevelControl.getDefaultTransitionTime()).get();
    }

    /*@Override
    public void handleCommand(final ZigBeeCommand command) {
        try {
            if (command instanceof HSBType) {
                HSBType color = (HSBType) command;
                DecimalType brightness = color.getBrightness();

                changeBrightness(brightness);

                if (delayedColorChange && brightness.intValue() != lastHSB.getBrightness().intValue()) {
                    Thread.sleep(1100);
                }

                if (supportsHue) {
                    changeColorHueSaturation(color);
                } else {
                    changeColorXY(color);
                }
            } else if (command instanceof DecimalType) {
                changeBrightness((DecimalType) command);
            } else if (command instanceof OnOffType) {
                changeOnOff((OnOffType) command);
            }
        } catch (InterruptedException | ExecutionException e) {
            log.warn("{}: Exception processing command", endpoint.getIeeeAddress(), e);
        }
    }*/

    @Override
    public boolean acceptEndpoint(ZigBeeEndpoint endpoint) {
        ZclColorControlCluster clusterColorControl = (ZclColorControlCluster) endpoint
                .getInputCluster(ZclColorControlCluster.CLUSTER_ID);
        if (clusterColorControl == null) {
            log.trace("{}: Color control cluster not found", endpoint.getIeeeAddress());
            return false;
        }

        try {
            if (!clusterColorControl.discoverAttributes(false).get()) {
                // Device is not supporting attribute reporting - instead, just read the attributes
                Integer capabilities = clusterColorControl.getColorCapabilities(Long.MAX_VALUE);
                if (capabilities == null && clusterColorControl.getCurrentX(Long.MAX_VALUE) == null
                        && clusterColorControl.getCurrentHue(Long.MAX_VALUE) == null) {
                    log.trace("{}: Color control XY and Hue returned null", endpoint.getIeeeAddress());
                    return false;
                }
                if (capabilities != null && ((capabilities & (ColorCapabilitiesEnum.HUE_AND_SATURATION.getKey()
                        | ColorCapabilitiesEnum.XY_ATTRIBUTE.getKey())) == 0)) {
                    // No support for hue or XY
                    log.trace("{}: Color control XY and Hue capabilities not supported", endpoint.getIeeeAddress());
                    return false;
                }
            } else if (clusterColorControl.isAttributeSupported(ZclColorControlCluster.ATTR_COLORCAPABILITIES)) {
                // If the device is reporting its capabilities, then use this over attribute detection
                // The color control cluster is required to always support XY attributes, so a non-color bulb is still
                // detected as a color bulb in this case.
                Integer capabilities = clusterColorControl.getColorCapabilities(Long.MAX_VALUE);
                if ((capabilities != null) && (capabilities & (ColorCapabilitiesEnum.HUE_AND_SATURATION.getKey()
                        | ColorCapabilitiesEnum.XY_ATTRIBUTE.getKey())) == 0) {
                    // No support for hue or XY
                    log.trace("{}: Color control XY and Hue capabilities not supported", endpoint.getIeeeAddress());
                    return false;
                }
            } else if (!clusterColorControl.isAttributeSupported(ZclColorControlCluster.ATTR_CURRENTHUE)
                    && !clusterColorControl.isAttributeSupported(ZclColorControlCluster.ATTR_CURRENTX)) {
                log.trace("{}: Color control XY and Hue attributes not supported", endpoint.getIeeeAddress());
                return false;
            }
        } catch (InterruptedException | ExecutionException e) {
            log.warn("{}: Exception discovering attributes in color control cluster", endpoint.getIeeeAddress(), e);
        }
        return true;
    }

    private void updateOnOff(boolean on) {
        currentOnOffState.set(on);

        if (lastColorMode != ColorModeEnum.COLOR_TEMPERATURE) {
            // Extra temp variable to avoid thread sync concurrency issues on lastHSB
            HSBType oldHSB = lastHSB;
            HSBType newHSB = on ? lastHSB : new HSBType(oldHSB.getHue(), oldHSB.getSaturation(), DecimalType.ZERO);
            updateChannelState(newHSB);
        } else if (!on) {
            updateChannelState(OnOffType.OFF);
        }
    }

    private void updateBrightness(DecimalType brightness) {
        // Extra temp variable to avoid thread sync concurrency issues on lastHSB
        HSBType oldHSB = lastHSB;
        HSBType newHSB = new HSBType(oldHSB.getHue(), oldHSB.getSaturation(), brightness);
        lastHSB = newHSB;
        if (currentOnOffState.get() && lastColorMode != ColorModeEnum.COLOR_TEMPERATURE) {
            updateChannelState(newHSB);
        }
    }

    private void updateColorHSB(DecimalType hue, DecimalType saturation) {
        // Extra temp variable to avoid thread sync concurrency issues on lastHSB
        HSBType oldHSB = lastHSB;
        HSBType newHSB = new HSBType(hue, saturation, oldHSB.getBrightness());
        lastHSB = newHSB;
        if (currentOnOffState.get() && lastColorMode != ColorModeEnum.COLOR_TEMPERATURE) {
            updateChannelState(newHSB);
        }
    }

    private void updateColorXY(DecimalType x, DecimalType y) {
        HSBType color = HSBType.fromXY(x.floatValue() / 100.0f, y.floatValue() / 100.0f);
        log.debug("{}: Update Color XY ({}, {}) -> HSV ({}, {}, {})", endpoint.getIeeeAddress(), x.toString(),
                y.toString(), color.getHue(), color.getSaturation(), lastHSB.getBrightness());
        updateColorHSB(color.getHue(), color.getSaturation());
    }

    private void updateColorHSB() {
        float hueValue = lastHue * 360.0f / 254.0f;
        float saturationValue = lastSaturation * 100.0f / 254.0f;
        DecimalType hue = new DecimalType(hueValue);
        DecimalType saturation = new DecimalType(saturationValue);
        updateColorHSB(hue, saturation);
        hueChanged = false;
        saturationChanged = false;
    }

    private void updateColorXY() {
        float xValue = lastX / 65536.0f;
        float yValue = lastY / 65536.0f;
        DecimalType x = new DecimalType(Float.valueOf(xValue * 100.0f));
        DecimalType y = new DecimalType(Float.valueOf(yValue * 100.0f));
        updateColorXY(x, y);
        xChanged = false;
        yChanged = false;
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute, Object val) {
        log.debug("{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), endpoint.getEndpointId(), attribute);

        synchronized (colorUpdateSync) {
            try {
                if (attribute.getCluster().getId() == ZclOnOffCluster.CLUSTER_ID) {
                    if (attribute.getId() == ZclOnOffCluster.ATTR_ONOFF) {
                        Boolean value = (Boolean) val;
                        updateOnOff(value);
                    }
                } else if (attribute.getCluster().getId() == ZclLevelControlCluster.CLUSTER_ID) {
                    if (attribute.getId() == ZclLevelControlCluster.ATTR_CURRENTLEVEL) {
                        DecimalType brightness = levelToPercent((Integer) val);
                        updateBrightness(brightness);
                    }
                } else if (attribute.getCluster().getId() == ZclColorControlCluster.CLUSTER_ID) {
                    if (attribute.getId() == ZclColorControlCluster.ATTR_CURRENTHUE) {
                        int hue = (Integer) val;
                        if (hue != lastHue) {
                            lastHue = hue;
                            hueChanged = true;
                        }
                    } else if (attribute.getId() == ZclColorControlCluster.ATTR_CURRENTSATURATION) {
                        int saturation = (Integer) val;
                        if (saturation != lastSaturation) {
                            lastSaturation = saturation;
                            saturationChanged = true;
                        }
                    } else if (attribute.getId() == ZclColorControlCluster.ATTR_CURRENTX) {
                        int x = (Integer) val;
                        if (x != lastX) {
                            lastX = x;
                            xChanged = true;
                        }
                    } else if (attribute.getId() == ZclColorControlCluster.ATTR_CURRENTY) {
                        int y = (Integer) val;
                        if (y != lastY) {
                            lastY = y;
                            yChanged = true;
                        }
                    } else if (attribute.getId() == ZclColorControlCluster.ATTR_COLORMODE) {
                        Integer colorMode = (Integer) val;
                        lastColorMode = ColorModeEnum.getByValue(colorMode);
                        if (lastColorMode == ColorModeEnum.COLOR_TEMPERATURE) {
                            updateChannelState(null);
                        } else if (currentOnOffState.get()) {
                            updateChannelState(lastHSB);
                        }
                    }
                }

                if (hueChanged || saturationChanged || xChanged || yChanged) {
                    if (colorUpdateTimer != null) {
                        colorUpdateTimer.cancel(true);
                        colorUpdateTimer = null;
                    }

                    if (hueChanged && saturationChanged) {
                        updateColorHSB();
                    } else if (xChanged && yChanged) {
                        updateColorXY();
                    } else {
                        // Wait some time and update anyway if only one attribute in each pair is updated
                        colorUpdateTimer = colorUpdateScheduler.schedule(() -> {
                            synchronized (colorUpdateSync) {
                                try {
                                    if ((hueChanged || saturationChanged) && lastHue >= 0.0f
                                            && lastSaturation >= 0.0f) {
                                        updateColorHSB();
                                    } else if ((xChanged || yChanged) && lastX >= 0.0f && lastY >= 0.0f) {
                                        updateColorXY();
                                    }
                                } catch (Exception e) {
                                    log.debug("{}: Exception in deferred attribute update",
                                            endpoint.getIeeeAddress(), e);
                                }

                                colorUpdateTimer = null;
                            }
                        }, 500, TimeUnit.MILLISECONDS);
                    }
                }
            } catch (Exception e) {
                log.debug("{}: Exception in attribute update", endpoint.getIeeeAddress(), e);
            }
        }
    }

    private boolean discoverSupportedColorCommands(ZclColorControlCluster serverClusterColorControl) {
        // Discover whether the device supports HUE/SAT or XY color set of commands
        try {
            if (!serverClusterColorControl.discoverAttributes(false).get()) {
                log.warn("{}: Cannot determine whether device supports RGB color. Assuming it supports HUE/SAT",
                        endpoint.getIeeeAddress());
                supportsHue = true;
            } else if (serverClusterColorControl.getSupportedAttributes()
                    .contains(ZclColorControlCluster.ATTR_CURRENTHUE)) {
                log.debug("{}: Device supports Hue/Saturation color set of commands", endpoint.getIeeeAddress());
                supportsHue = true;
            } else if (serverClusterColorControl.getSupportedAttributes()
                    .contains(ZclColorControlCluster.ATTR_CURRENTX)) {
                log.debug("{}: Device supports XY color set of commands", endpoint.getIeeeAddress());
                supportsHue = false;
                delayedColorChange = true; // For now, only for XY lights till this is configurable
            } else {
                log.warn("{}: Device supports neither RGB color nor XY color", endpoint.getIeeeAddress());
                pollingPeriod = POLLING_PERIOD_HIGH;
                return false;
            }
        } catch (InterruptedException | ExecutionException e) {
            log.warn(
                    "{}: Exception checking whether device endpoint supports RGB color. Assuming it supports HUE/SAT",
                    endpoint.getIeeeAddress(), e);
            supportsHue = true;
        }

        return true;
    }

}
