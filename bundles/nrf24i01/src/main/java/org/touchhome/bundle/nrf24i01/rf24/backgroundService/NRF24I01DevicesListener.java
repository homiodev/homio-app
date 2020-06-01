package org.touchhome.bundle.nrf24i01.rf24.backgroundService;

import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.thread.BackgroundProcessService;
import org.touchhome.bundle.api.util.ApplicationContextHolder;
import org.touchhome.bundle.nrf24i01.rf24.NRF24I01Bundle;
import org.touchhome.bundle.nrf24i01.rf24.command.RF24CommandPlugin;
import org.touchhome.bundle.nrf24i01.rf24.communication.RF24Message;
import org.touchhome.bundle.nrf24i01.rf24.communication.ReadListener;
import org.touchhome.bundle.nrf24i01.rf24.communication.SendCommand;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import static org.touchhome.bundle.nrf24i01.rf24.Command.SET_PIN_VALUE_ON_HANDLER_REQUEST_COMMAND;

/**
 * Why this is service!!!!
 */
public class NRF24I01DevicesListener extends BackgroundProcessService<Void> {

    private final NRF24I01Bundle rf24Service;
    private final Map<Byte, RF24CommandPlugin> rf24CommandPlugins;

    public NRF24I01DevicesListener() {
        super(NRF24I01DevicesListener.class.getSimpleName());
        rf24Service = ApplicationContextHolder.getBean(NRF24I01Bundle.class);
        rf24CommandPlugins = ApplicationContextHolder.getBean("rf24CommandPlugins", Map.class);

        if (EntityContext.isTestEnvironment()) {
            Timer timer = new Timer();

            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        /*ReportAttributesCommand command = new ReportAttributesCommand();
                        command.setReports(new ArrayList<>());
                        AttributeReport attributeReport = new AttributeReport();
                        command.getReports().add(attributeReport);
                        command.setClusterId(ZclClusterType.ILLUMINANCE_MEASUREMENT.getId());
                        attributeReport.setAttributeDataType(1);
                        attributeReport.setAttributeValue((int) (Math.random() * 100));

                        ZigBeeDeviceEntity entity = manager.getEntity("zb_5149013370325916");
                        ZigBeeDevicePlugin devicePlugin = manager.getDevicePlugin(entity);

                        RemoteCommand remoteCommand = devicePlugin.buildRemoteCommandFromReportAttributeCommand(command);
                        if (remoteCommand != null) {
                            Set<ScratchUpdateValue> scratchUpdateValues = new HashSet<>();

                            manager.getScratchPlugin(entity.getScratch()).handleRequestPinValueEvent(
                                    new ScratchPlugin.HandleRequestPinValueEventEntity(manager, entity.getScratch(), entity, remoteCommand, scratchUpdateValues, null, false));

                            Constants.WsNotifications.sendScratchUpdates(scratchUpdateValues);
                        }
                        */

                        RF24CommandPlugin commandPlugin = rf24CommandPlugins.get((byte) SET_PIN_VALUE_ON_HANDLER_REQUEST_COMMAND.getValue());
                        ByteBuffer payloadBuffer = ByteBuffer.allocate(50);
                        payloadBuffer.put((byte) 1); // handlerID
                        Random random = new Random();
                        payloadBuffer.put((byte) random.nextInt(255)); // value
                        payloadBuffer.flip();

                        RF24Message rf24Message = new RF24Message((byte) 1, (short) 2, commandPlugin, payloadBuffer);
                        rf24Service.executeCommand(rf24Message);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }, 0, 10 * 1000); // every minute
        }

        rf24Service.subscribeForReading(new ReadListener() {
            @Override
            public boolean canReceive(RF24Message rf24Message) {
                return rf24Message.getCommandPlugin().canReceiveGeneral();
            }

            @Override
            public void received(RF24Message rf24Message) {
                SendCommand sendCommand = rf24Service.executeCommand(rf24Message);
                if (sendCommand != null) {
                    rf24Service.scheduleGlobalSend(sendCommand, rf24Message);
                }
            }

            @Override
            public void notReceived() {
                throw new IllegalStateException("Must be not called!!!");
            }

            @Override
            public String getId() {
                return "Listener";
            }
        });
    }

    @Override
    public Void runInternal() {
        return null;
    }

    @Override
    public boolean onException(Exception ex) {
        return false;
    }

    @Override
    public long getPeriod() {
        return 0;
    }

    @Override
    public boolean canWork() {
        return rf24Service.isNrf24L01Works() || EntityContext.isTestEnvironment();
    }

    @Override
    protected boolean isAutoStart() {
        return true;
    }

    @Override
    public boolean shouldStartNow() {
        return true;
    }
}
