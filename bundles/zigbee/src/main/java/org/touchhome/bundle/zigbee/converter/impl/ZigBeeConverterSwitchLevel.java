package org.touchhome.bundle.zigbee.converter.impl;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.*;
import com.zsmartsystems.zigbee.zcl.clusters.ZclLevelControlCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclOnOffCluster;
import com.zsmartsystems.zigbee.zcl.clusters.levelcontrol.*;
import com.zsmartsystems.zigbee.zcl.clusters.onoff.*;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;
import lombok.extern.log4j.Log4j2;
import org.touchhome.bundle.zigbee.converter.ZigBeeBaseChannelConverter;
import org.touchhome.bundle.zigbee.converter.impl.config.ZclLevelControlConfig;
import org.touchhome.bundle.zigbee.model.DecimalType;
import org.touchhome.bundle.zigbee.model.OnOffType;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Level control converter uses both the {@link ZclLevelControlCluster} and the {@link ZclOnOffCluster}.
 * <p>
 * For the server side, if the {@link ZclOnOffCluster} has reported the device is OFF, then reports from
 * {@link ZclLevelControlCluster} are ignored. This is required as devices can report via the
 * {@link ZclLevelControlCluster} that they have a specified level, but still be OFF.
 */
@Log4j2
@ZigBeeConverter(name = "zigbee:switch_level",
        serverClusters = {ZclOnOffCluster.CLUSTER_ID, ZclLevelControlCluster.CLUSTER_ID},
        clientClusters = {ZclOnOffCluster.CLUSTER_ID, ZclLevelControlCluster.CLUSTER_ID})
public class ZigBeeConverterSwitchLevel extends ZigBeeBaseChannelConverter
        implements ZclAttributeListener, ZclCommandListener {

    // The number of milliseconds between state updates into OH when handling level control changes at a rate
    private static final int STATE_UPDATE_RATE = 50;

    // The number of milliseconds after the last IncreaseDecreaseType is received before sending the Stop command
    private static final int INCREASEDECREASE_TIMEOUT = 200;
    private final AtomicBoolean currentOnOffState = new AtomicBoolean(true);
    private ZclOnOffCluster clusterOnOffClient;
    private ZclLevelControlCluster clusterLevelControlClient;
    private ZclOnOffCluster clusterOnOffServer;
    private ZclLevelControlCluster clusterLevelControlServer;
    private ZclAttribute attributeOnOff;
    private ZclAttribute attributeLevel;
    private ZclLevelControlConfig configLevelControl;
    private DecimalType lastLevel = DecimalType.HUNDRED;

    //  private Command lastCommand;

    private ScheduledExecutorService updateScheduler;
    private ScheduledFuture<?> updateTimer = null;

    @Override
    public boolean initializeDevice() {
        if (initializeDeviceServer()) {
            log.debug("{}: Level control device initialized as server", endpoint.getIeeeAddress());
            return true;
        }

        if (initializeDeviceClient()) {
            log.debug("{}: Level control device initialized as client", endpoint.getIeeeAddress());
            return true;
        }

        log.error("{}: Error opening device level controls", endpoint.getIeeeAddress());
        return false;
    }

    private boolean initializeDeviceServer() {
        ZclLevelControlCluster serverClusterLevelControl = (ZclLevelControlCluster) endpoint
                .getInputCluster(ZclLevelControlCluster.CLUSTER_ID);
        if (serverClusterLevelControl == null) {
            log.trace("{}: Error opening device level controls", endpoint.getIeeeAddress());
            return false;
        }

        try {
            CommandResult bindResponse = bind(serverClusterLevelControl).get();
            if (bindResponse.isSuccess()) {
                // Configure reporting
                updateServerPoolingPeriod(serverClusterLevelControl, ZclLevelControlCluster.ATTR_CURRENTLEVEL, false, 1);
            } else {
                pollingPeriod = POLLING_PERIOD_HIGH;
                log.debug("{}: Failed to bind level control cluster", endpoint.getIeeeAddress());
            }
        } catch (InterruptedException | ExecutionException e) {
            log.error(String.format("%s: Exception setting level control reporting ", endpoint.getIeeeAddress()), e);
            return false;
        }

        ZclOnOffCluster serverClusterOnOff = (ZclOnOffCluster) endpoint.getInputCluster(ZclOnOffCluster.CLUSTER_ID);
        if (serverClusterOnOff == null) {
            log.trace("{}: Error opening device level controls", endpoint.getIeeeAddress());
            return false;
        }

        try {
            CommandResult bindResponse = bind(serverClusterOnOff).get();
            if (bindResponse.isSuccess()) {
                // Configure reporting
                updateServerPoolingPeriod(serverClusterOnOff, ZclOnOffCluster.ATTR_ONOFF, false);
            } else {
                pollingPeriod = POLLING_PERIOD_HIGH;
                log.debug("{}: Failed to bind on off control cluster", endpoint.getIeeeAddress());
                return false;
            }
        } catch (InterruptedException | ExecutionException e) {
            log.error(String.format("%s: Exception setting on off reporting ", endpoint.getIeeeAddress()), e);
            return false;
        }

        return true;
    }

    private boolean initializeDeviceClient() {
        ZclLevelControlCluster clusterLevelControl = (ZclLevelControlCluster) endpoint.getOutputCluster(ZclLevelControlCluster.CLUSTER_ID);
        if (clusterLevelControl == null) {
            log.trace("{}: Error opening device level controls", endpoint.getIeeeAddress());
            return false;
        }

        try {
            CommandResult bindResponse = bind(clusterLevelControl).get();
            if (!bindResponse.isSuccess()) {
                log.error("{}: Error 0x{} setting client binding", endpoint.getIeeeAddress(), Integer.toHexString(bindResponse.getStatusCode()));
            }
        } catch (InterruptedException | ExecutionException e) {
            log.error(String.format("%s: Exception setting level control reporting ", endpoint.getIeeeAddress()), e);
            return false;
        }

        ZclOnOffCluster clusterOnOff = (ZclOnOffCluster) endpoint.getOutputCluster(ZclOnOffCluster.CLUSTER_ID);
        if (clusterOnOff == null) {
            log.trace("{}: Error opening device on off controls", endpoint.getIeeeAddress());
            return false;
        }

        try {
            CommandResult bindResponse = bind(clusterOnOff).get();
            if (!bindResponse.isSuccess()) {
                log.error("{}: Error 0x{} setting client binding", endpoint.getIeeeAddress(), Integer.toHexString(bindResponse.getStatusCode()));
            }
        } catch (InterruptedException | ExecutionException e) {
            log.error(String.format("%s: Exception setting on off reporting ", endpoint.getIeeeAddress()), e);
            return false;
        }

        return true;
    }

    @Override
    public synchronized boolean initializeConverter() {
        updateScheduler = Executors.newSingleThreadScheduledExecutor();

        if (initializeConverterServer()) {
            log.debug("{}: Level control initialized as server", endpoint.getIeeeAddress());
            return true;
        }

        if (initializeConverterClient()) {
            log.debug("{}: Level control initialized as client", endpoint.getIeeeAddress());
            return true;
        }

        log.error("{}: Error opening device level controls", endpoint.getIeeeAddress());
        return false;
    }

    private boolean initializeConverterServer() {
        clusterLevelControlServer = (ZclLevelControlCluster) endpoint
                .getInputCluster(ZclLevelControlCluster.CLUSTER_ID);
        if (clusterLevelControlServer == null) {
            log.trace("{}: Error opening device level controls", endpoint.getIeeeAddress());
            return false;
        }

        clusterOnOffServer = (ZclOnOffCluster) endpoint.getInputCluster(ZclOnOffCluster.CLUSTER_ID);
        if (clusterOnOffServer == null) {
            log.trace("{}: Error opening device on off controls", endpoint.getIeeeAddress());
            return false;
        }

        attributeOnOff = clusterOnOffServer.getAttribute(ZclOnOffCluster.ATTR_ONOFF);
        attributeLevel = clusterLevelControlServer.getAttribute(ZclLevelControlCluster.ATTR_CURRENTLEVEL);

        // Add a listeners
        clusterOnOffServer.addAttributeListener(this);
        clusterLevelControlServer.addAttributeListener(this);

        // Set the currentOnOffState to ON. This will ensure that we only ignore levelControl reports AFTER we have
        // really received an OFF report, thus confirming ON_OFF reporting is working
        currentOnOffState.set(true);

        // Create a configuration handler and get the available options
        configLevelControl = new ZclLevelControlConfig();
        configLevelControl.initialize(clusterLevelControlServer);

        //configOptions = new ArrayList<>();
        //configOptions.addAll(configReporting.getConfiguration());
        //configOptions.addAll(configLevelControl.getConfiguration());

        return true;
    }

    private boolean initializeConverterClient() {
        clusterLevelControlClient = (ZclLevelControlCluster) endpoint
                .getOutputCluster(ZclLevelControlCluster.CLUSTER_ID);
        if (clusterLevelControlClient == null) {
            log.trace("{}: Error opening device level controls", endpoint.getIeeeAddress());
            return false;
        }

        clusterOnOffClient = (ZclOnOffCluster) endpoint.getOutputCluster(ZclOnOffCluster.CLUSTER_ID);
        if (clusterOnOffClient == null) {
            log.trace("{}: Error opening device on off controls", endpoint.getIeeeAddress());
            return false;
        }

        // Add a listeners
        clusterOnOffClient.addCommandListener(this);
        clusterLevelControlClient.addCommandListener(this);

        // Set the currentOnOffState to ON. This will ensure that we only ignore levelControl reports AFTER we have
        // really received an OFF report, thus confirming ON_OFF reporting is working
        currentOnOffState.set(true);

        //configOptions = new ArrayList<>();

        return true;
    }

    @Override
    public void disposeConverter() {
        if (clusterOnOffClient != null) {
            clusterOnOffClient.removeCommandListener(this);
        }
        if (clusterLevelControlClient != null) {
            clusterLevelControlClient.removeCommandListener(this);
        }
        if (clusterOnOffServer != null) {
            clusterOnOffServer.removeAttributeListener(this);
        }
        if (clusterLevelControlServer != null) {
            clusterLevelControlServer.removeAttributeListener(this);
        }

        stopTransitionTimer();
        updateScheduler.shutdownNow();
    }

    @Override
    public int getPollingPeriod() {
        return zigBeeDevice.getZigBeeDeviceEntity().getPoolingPeriod();
    }

    @Override
    protected void handleRefresh() {
        if (attributeOnOff != null) {
            attributeOnOff.readValue(0);
        }
        if (attributeLevel != null) {
            attributeLevel.readValue(0);
        }
    }

  /*  @Override
    public void handleCommand(final ZigBeeCommand command) {
        if (command instanceof OnOffType) {
            handleOnOffCommand((OnOffType) command);
        } else if (command instanceof DecimalType) {
            handlePercentCommand((DecimalType) command);
        } else if (command instanceof IncreaseDecreaseType) {
            handleIncreaseDecreaseCommand((IncreaseDecreaseType) command);
        } else {
            log.warn("{}: Level converter only accepts DecimalType, IncreaseDecreaseType and OnOffType - not {}",
                    endpoint.getIeeeAddress(), command.getClass().getSimpleName());
        }

        // Some functionality (eg IncreaseDecrease) requires that we know the last command received
        lastCommand = command;
    }*/

    /* *//**
     * If we support the OnOff cluster then we should perform the same function as the SwitchOnoffConverter. Otherwise,
     * interpret ON commands as moving to level 100%, and OFF commands as moving to level 0%.
     *//*
    private void handleOnOffCommand(OnOffType cmdOnOff) {
        if (clusterOnOffServer != null) {
            if (cmdOnOff == OnOffType.ON) {
                clusterOnOffServer.onCommand();
            } else {
                clusterOnOffServer.offCommand();
            }
        } else {
            if (cmdOnOff == OnOffType.ON) {
                moveToLevel(DecimalType.HUNDRED);
            } else {
                moveToLevel(DecimalType.ZERO);
            }
        }
    }*/

   /* private void handlePercentCommand(DecimalType cmdPercent) {
        moveToLevel(cmdPercent);
    }*/

    /*private void moveToLevel(DecimalType percent) {
        if (clusterOnOffServer != null) {
            if (percent.equals(DecimalType.ZERO)) {
                clusterOnOffServer.offCommand();
            } else {
                clusterLevelControlServer.moveToLevelWithOnOffCommand(percentToLevel(percent),
                        configLevelControl.getDefaultTransitionTime());
            }
        } else {
            clusterLevelControlServer.moveToLevelCommand(percentToLevel(percent),
                    configLevelControl.getDefaultTransitionTime());
        }
    }*/

    /**
     * The IncreaseDecreaseType in openHAB is defined as a STEP command. however we want to use this for the Move/Stop
     * command which is not available in openHAB.
     * When the first IncreaseDecreaseType is received, we send the Move command and start a timer to send the Stop
     * command when no further IncreaseDecreaseType commands are received.
     * We use the lastCommand to check if the current command is the same IncreaseDecreaseType, and if so we just
     * restart the timer.
     * When the timer times out and sends the Stop command, it also sets lastCommand to null.
     * <p>
     * // * @param cmdIncreaseDecrease the command received
     */
   /* private void handleIncreaseDecreaseCommand(IncreaseDecreaseType cmdIncreaseDecrease) {
        if (!cmdIncreaseDecrease.equals(lastCommand)) {
            switch (cmdIncreaseDecrease) {
                case INCREASE:
                    clusterLevelControlServer.moveWithOnOffCommand(0, 50);
                    break;
                case DECREASE:
                    clusterLevelControlServer.moveWithOnOffCommand(1, 50);
                    break;
                default:
                    break;
            }
        }
        startStopTimer(INCREASEDECREASE_TIMEOUT);
    }*/
    @Override
    public boolean acceptEndpoint(ZigBeeEndpoint endpoint) {
        if (endpoint.getInputCluster(ZclLevelControlCluster.CLUSTER_ID) == null
                && endpoint.getOutputCluster(ZclLevelControlCluster.CLUSTER_ID) == null) {
            log.trace("{}: Level control cluster not found", endpoint.getIeeeAddress());
            return false;
        }

        return true;
    }

    @Override
    public void updateConfiguration() {
        try {
            updateServerPoolingPeriod(clusterLevelControlServer, ZclOnOffCluster.ATTR_ONOFF, true);
            updateServerPoolingPeriod(clusterLevelControlServer, ZclLevelControlCluster.ATTR_CURRENTLEVEL, true, 1);
        } catch (InterruptedException | ExecutionException e) {
            log.debug("{}: Level control exception setting reporting", endpoint.getIeeeAddress(), e);
        }

        if (configLevelControl != null) {
            configLevelControl.updateConfiguration();
        }
    }

    @Override
    public synchronized void attributeUpdated(ZclAttribute attribute, Object val) {
        log.debug("{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), endpoint.getEndpointId(), attribute);
        if (attribute.getCluster() == ZclClusterType.LEVEL_CONTROL
                && attribute.getId() == ZclLevelControlCluster.ATTR_CURRENTLEVEL) {
            //lastLevel = levelToPercent((Integer) val);
            if (currentOnOffState.get()) {
                // Note that state is only updated if the current On/Off state is TRUE (ie ON)
                //   updateChannelState(lastLevel);
            }
        } else if (attribute.getCluster() == ZclClusterType.ON_OFF && attribute.getId() == ZclOnOffCluster.ATTR_ONOFF) {
            if (attribute.getLastValue() != null) {
                currentOnOffState.set((Boolean) val);
                updateChannelState(currentOnOffState.get() ? lastLevel : OnOffType.OFF);
            }
        }
    }

    @Override
    public boolean commandReceived(ZclCommand command) {
        log.debug("{}: ZigBee command received {}", endpoint.getIeeeAddress(), endpoint.getEndpointId(), command);

        // OnOff Cluster Commands
        if (command instanceof OnCommand) {
            currentOnOffState.set(true);
            //lastLevel = DecimalType.HUNDRED;
            //updateChannelState(lastLevel);
            clusterOnOffClient.sendDefaultResponse(command, ZclStatus.SUCCESS);
            return true;
        }
        if (command instanceof OnWithTimedOffCommand) {
            currentOnOffState.set(true);
            OnWithTimedOffCommand timedCommand = (OnWithTimedOffCommand) command;
            //lastLevel = DecimalType.HUNDRED;
            //updateChannelState(lastLevel);
            clusterOnOffClient.sendDefaultResponse(command, ZclStatus.SUCCESS);
            startOffTimer(timedCommand.getOnTime() * 100);
            return true;
        }
        if (command instanceof OffCommand) {
            currentOnOffState.set(false);
            //lastLevel = DecimalType.ZERO;
            //updateChannelState(lastLevel);
            return true;
        }
        if (command instanceof ToggleCommand) {
            currentOnOffState.set(!currentOnOffState.get());
            //lastLevel = currentOnOffState.get() ? DecimalType.HUNDRED : DecimalType.ZERO;
            //updateChannelState(lastLevel);
            clusterOnOffClient.sendDefaultResponse(command, ZclStatus.SUCCESS);
            return true;
        }
        if (command instanceof OffWithEffectCommand) {
            OffWithEffectCommand offEffect = (OffWithEffectCommand) command;
            startOffEffect(offEffect.getEffectIdentifier(), offEffect.getEffectVariant());
            clusterOnOffClient.sendDefaultResponse(command, ZclStatus.SUCCESS);
            return true;
        }

        // LevelControl Cluster Commands
        if (command instanceof MoveToLevelCommand || command instanceof MoveToLevelWithOnOffCommand) {
            int time;
            int level;

            if (command instanceof MoveToLevelCommand) {
                MoveToLevelCommand levelCommand = (MoveToLevelCommand) command;
                time = levelCommand.getTransitionTime();
                level = levelCommand.getLevel();
            } else {
                MoveToLevelWithOnOffCommand levelCommand = (MoveToLevelWithOnOffCommand) command;
                time = levelCommand.getTransitionTime();
                level = levelCommand.getLevel();
            }
            clusterLevelControlClient.sendDefaultResponse(command, ZclStatus.SUCCESS);
            //startTransitionTimer(time * 100, levelToPercent(level));
            return true;
        }
        if (command instanceof MoveCommand || command instanceof MoveWithOnOffCommand) {
            int mode;
            int rate;

            if (command instanceof MoveCommand) {
                MoveCommand levelCommand = (MoveCommand) command;
                mode = levelCommand.getMoveMode();
                rate = levelCommand.getRate();
            } else {
                MoveWithOnOffCommand levelCommand = (MoveWithOnOffCommand) command;
                mode = levelCommand.getMoveMode();
                rate = levelCommand.getRate();
            }

            clusterLevelControlClient.sendDefaultResponse(command, ZclStatus.SUCCESS);

            // Get percent change per step period
            double stepRatePerSecond = levelToPercent(rate).doubleValue();
            double distance;

            if (mode == 0) {
                distance = 100.0 - lastLevel.doubleValue();
            } else {
                distance = lastLevel.doubleValue();
            }
            int transitionTime = (int) (distance / stepRatePerSecond * 1000);

            startTransitionTimer(transitionTime, mode == 0 ? 100.0 : 0.0);
            return true;
        }
        if (command instanceof StepCommand || command instanceof StepWithOnOffCommand) {
            int mode;
            int step;
            int time;

            if (command instanceof StepCommand) {
                StepCommand levelCommand = (StepCommand) command;
                mode = levelCommand.getStepMode();
                step = levelCommand.getStepSize();
                time = levelCommand.getTransitionTime();
            } else {
                StepWithOnOffCommand levelCommand = (StepWithOnOffCommand) command;
                mode = levelCommand.getStepMode();
                step = levelCommand.getStepSize();
                time = levelCommand.getTransitionTime();
            }

            double value;
            if (mode == 0) {
                value = lastLevel.doubleValue() + levelToPercent(step).doubleValue();
            } else {
                value = lastLevel.doubleValue() - levelToPercent(step).doubleValue();
            }
            if (value < 0.0) {
                value = 0.0;
            } else if (value > 100.0) {
                value = 100.0;
            }

            clusterLevelControlClient.sendDefaultResponse(command, ZclStatus.SUCCESS);
            startTransitionTimer(time * 100, value);
            return true;
        }
        if (command instanceof StopCommand || command instanceof StopWithOnOffCommand) {
            clusterLevelControlClient.sendDefaultResponse(command, ZclStatus.SUCCESS);
            stopTransitionTimer();
            return true;
        }

        return false;
    }

    private void stopTransitionTimer() {
        if (updateTimer != null) {
            updateTimer.cancel(true);
            updateTimer = null;
        }
    }

    /**
     * Starts a timer to transition to finalState with transitionTime milliseconds. The state will be updated every
     * STATE_UPDATE_RATE milliseconds.
     *
     * @param transitionTime the number of milliseconds to move the finalState
     * @param finalState     the final level to move to
     */
    private void startTransitionTimer(int transitionTime, double finalState) {
        stopTransitionTimer();

        log.debug("{}: Level transition move to {} in {}ms", endpoint.getIeeeAddress(), finalState, transitionTime);
        final int steps = transitionTime / STATE_UPDATE_RATE;
        if (steps == 0) {
            log.debug("{}: Level transition timer has 0 steps. Setting to {}.", endpoint.getIeeeAddress(),
                    finalState);
            lastLevel = new DecimalType((int) finalState);
            currentOnOffState.set(finalState != 0);
            // updateChannelState(lastLevel);
            return;
        }
        final double start = lastLevel.doubleValue();
        final double step = (finalState - lastLevel.doubleValue()) / steps;

        updateTimer = updateScheduler.scheduleAtFixedRate(new Runnable() {
            private int count = 0;
            private double state = start;

            @Override
            public void run() {
                state += step;
                if (state < 0.0) {
                    state = 0.0;
                } else if (state > 100.0) {
                    state = 100.0;
                }
                lastLevel = new DecimalType((int) state);
                log.debug("{}: Level transition timer {}/{} updating to {}", endpoint.getIeeeAddress(), endpoint.getEndpointId(), count, steps,
                        lastLevel);
                currentOnOffState.set(state != 0);
                // updateChannelState(lastLevel);

                if (state == 0.0 || state == 100.0 || ++count == steps) {
                    log.debug("{}: Level transition timer complete", endpoint.getIeeeAddress());
                    updateTimer.cancel(true);
                    updateTimer = null;
                }
            }
        }, 0, STATE_UPDATE_RATE, TimeUnit.MILLISECONDS);
    }

    /**
     * Starts a timer after which the state will be set to OFF
     *
     * @param delay the number of milliseconds to wait before setting the value to OFF
     */
    private void startOffTimer(int delay) {
        stopTransitionTimer();

        updateTimer = updateScheduler.schedule(new Runnable() {
            @Override
            public void run() {
                log.debug("{}: OnOff auto OFF timer expired", endpoint.getIeeeAddress());
                lastLevel = DecimalType.ZERO;
                currentOnOffState.set(false);
                updateChannelState(OnOffType.OFF);
                updateTimer = null;
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    /**
     * Starts a timer to perform the off effect
     *
     * @param effectId      the effect type
     * @param effectVariant the effect variant
     */
    private void startOffEffect(int effectId, int effectVariant) {
        stopTransitionTimer();

        int effect = effectId << 8 + effectVariant;

        switch (effect) {
            case 0x0002:
                // 50% dim down in 0.8 seconds then fade to off in 12 seconds
                break;

            case 0x0100:
                // 20% dim up in 0.5s then fade to off in 1 second
                break;

            default:
                log.debug("{}: Off effect {} unknown", endpoint.getIeeeAddress(), String.format("%04", effect));

            case 0x0000:
                // Fade to off in 0.8 seconds
            case 0x0001:
                // No fade
                startTransitionTimer(800, 0.0);
                break;
        }
    }

    /**
     * Starts a timer after which the Stop command will be sent
     *
     * @param delay the number of milliseconds to wait before setting the value to OFF
     */
    /*private void startStopTimer(int delay) {
        stopTransitionTimer();

        updateTimer = updateScheduler.schedule(new Runnable() {
            @Override
            public void run() {
                log.debug("{}: IncreaseDecrease Stop timer expired", endpoint.getIeeeAddress());
                clusterLevelControlServer.stopWithOnOffCommand();
                // lastCommand = null;
                updateTimer = null;
            }
        }, delay, TimeUnit.MILLISECONDS);
    }*/
}
