/**
 * Copyright (c) 2010-2017 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.knx.handler;

import static org.openhab.binding.knx.KNXBindingConstants.*;
import static org.openhab.binding.knx.internal.handler.DeviceConstants.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingStatusInfo;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.eclipse.smarthome.core.types.State;
import org.eclipse.smarthome.core.types.Type;
import org.openhab.binding.knx.GroupAddressListener;
import org.openhab.binding.knx.IndividualAddressListener;
import org.openhab.binding.knx.internal.channel.KNXChannelSelector;
import org.openhab.binding.knx.internal.channel.KNXChannelType;
import org.openhab.binding.knx.internal.handler.BasicConfig;
import org.openhab.binding.knx.internal.handler.Firmware;
import org.openhab.binding.knx.internal.handler.Flag;
import org.openhab.binding.knx.internal.handler.Manufacturer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tuwien.auto.calimero.DataUnitBuilder;
import tuwien.auto.calimero.DeviceDescriptor;
import tuwien.auto.calimero.DeviceDescriptor.DD0;
import tuwien.auto.calimero.GroupAddress;
import tuwien.auto.calimero.IndividualAddress;
import tuwien.auto.calimero.datapoint.CommandDP;
import tuwien.auto.calimero.datapoint.Datapoint;
import tuwien.auto.calimero.exception.KNXException;
import tuwien.auto.calimero.exception.KNXFormatException;
import tuwien.auto.calimero.mgmt.PropertyAccess.PID;

/**
 * The {@link KNXBasicThingHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Karel Goderis - Initial contribution
 */
public class KNXBasicThingHandler extends BaseThingHandler implements IndividualAddressListener, GroupAddressListener {

    private final Random random = new Random();

    private final Logger logger = LoggerFactory.getLogger(KNXBasicThingHandler.class);

    // the physical address of the KNX actor represented by this Thing
    protected IndividualAddress address;

    // group addresses the handler is monitoring
    protected Set<GroupAddress> groupAddresses = new HashSet<GroupAddress>();

    // group addresses read out from the device's firmware tables
    protected Set<GroupAddress> foundGroupAddresses = new HashSet<GroupAddress>();

    private Map<GroupAddress, ScheduledFuture<?>> readFutures = new HashMap<>();
    private ScheduledFuture<?> pollingJob;
    private ScheduledFuture<?> descriptionJob;
    private ScheduledExecutorService knxScheduler;
    private BasicConfig config;

    private static final long OPERATION_TIMEOUT = 5000;
    private static final long OPERATION_INTERVAL = 2000;
    private boolean filledDescription = false;
    private Set<ChannelUID> blockedChannels = new HashSet<ChannelUID>();

    public KNXBasicThingHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        config = getConfigAs(BasicConfig.class);

        initializeGroupAddresses();

        knxScheduler = getBridgeHandler().getScheduler();
        try {
            if (StringUtils.isNotBlank(config.getAddress())) {
                address = new IndividualAddress(config.getAddress());

                long pollingInterval = config.getInterval().longValue();
                long initialDelay = Math.round(pollingInterval * random.nextFloat());

                if ((pollingJob == null || pollingJob.isCancelled())) {
                    logger.debug("'{}' will be polled every {}s", getThing().getUID(), pollingInterval);
                    pollingJob = knxScheduler.scheduleWithFixedDelay(() -> pollDeviceStatus(), initialDelay,
                            pollingInterval, TimeUnit.SECONDS);
                }
            } else {
                updateStatus(ThingStatus.ONLINE);
            }
        } catch (KNXFormatException e) {
            logger.error("An exception occurred while setting the individual address '{}': {}", config.getAddress(),
                    e.getMessage());
            if (logger.isDebugEnabled()) {
                logger.error("", e);
            }
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, e.getLocalizedMessage());
        }

        getBridgeHandler().registerGroupAddressListener(this);
        scheduleReadJobs();
    }

    private void initializeGroupAddresses() {
        forAllChannels((selector, channelConfiguration) -> {
            groupAddresses.addAll(selector.getAddresses(channelConfiguration));
        });
    }

    private KNXChannelType getKNXChannelType(Channel channel) {
        String channelID = channel.getChannelTypeUID().getId();
        KNXChannelType selector = KNXChannelSelector.getTypeFromChannelTypeId(channelID);
        return selector;
    }

    private KNXBridgeBaseThingHandler getBridgeHandler() {
        return (KNXBridgeBaseThingHandler) getBridge().getHandler();
    }

    @Override
    public void dispose() {
        if (pollingJob != null && !pollingJob.isCancelled()) {
            pollingJob.cancel(true);
            pollingJob = null;
        }

        if (descriptionJob != null && !descriptionJob.isCancelled()) {
            descriptionJob.cancel(true);
            descriptionJob = null;
        }

        cancelReadFutures();

        KNXBridgeBaseThingHandler bridgeHandler = getBridgeHandler();
        if (bridgeHandler != null) {
            bridgeHandler.unregisterGroupAddressListener(this);
        }
    }

    private void cancelReadFutures() {
        if (readFutures != null) {
            for (ScheduledFuture<?> future : readFutures.values()) {
                if (!future.isDone()) {
                    future.cancel(true);
                }
            }
        }
    }

    @FunctionalInterface
    private interface ChannelFunction {
        void apply(KNXChannelType channelType, Configuration configuration) throws KNXException;
    }

    private void withKNXType(ChannelUID channelUID, ChannelFunction function) {
        withKNXType(channelUID.getId(), function);
    }

    private void withKNXType(String channelId, ChannelFunction function) {
        Channel channel = getThing().getChannel(channelId);
        if (channel == null) {
            logger.warn("Channel '{}' does not exist on thing '{}'", channelId, getThing().getUID());
            return;
        }
        withKNXType(channel, function);
    }

    private void withKNXType(Channel channel, ChannelFunction function) {
        try {
            KNXChannelType selector = getKNXChannelType(channel);
            if (selector != null) {
                Configuration channelConfiguration = channel.getConfiguration();
                if (channelConfiguration != null) {
                    function.apply(selector, channelConfiguration);
                } else {
                    logger.warn("The configuration of channel {} is empty", channel.getUID());
                }
            } else {
                logger.warn("The KNX channel type {} is not implemented", channel.getChannelTypeUID().getId());
            }
        } catch (KNXException e) {
            logger.error("An error occurred on channel {}: {}", channel.getUID(), e.getMessage());
            if (logger.isDebugEnabled()) {
                logger.error("", e);
            }
        }
    }

    private void forAllChannels(ChannelFunction function) {
        for (Channel channel : getThing().getChannels()) {
            withKNXType(channel, function);
        }
    }

    @Override
    public void channelLinked(ChannelUID channelUID) {
        withKNXType(channelUID, (selector, configuration) -> {
            for (String addressKey : selector.getAddressKeys(configuration)) {
                if (selector.getGroupAddress(configuration, addressKey) != null) {
                    if (selector.getFlags(configuration, addressKey).contains(Flag.READ)
                            || selector.getReadInterval(configuration, addressKey) > 0) {
                        scheduleReadJob(selector.getGroupAddress(configuration, addressKey),
                                selector.getDPT(configuration, addressKey), true, 0);
                    }
                }
            }
        });
    }

    private void scheduleReadJobs() {
        cancelReadFutures();

        for (Channel channel : getThing().getChannels()) {
            if (isLinked(channel.getUID().getId())) {
                withKNXType(channel, (selector, configuration) -> {
                    for (String addressKey : selector.getAddressKeys(configuration)) {
                        if (selector.getGroupAddress(configuration, addressKey) != null) {
                            scheduleReadJob(selector.getGroupAddress(configuration, addressKey),
                                    selector.getDPT(configuration, addressKey),
                                    selector.getFlags(configuration, addressKey).contains(Flag.READ),
                                    selector.getReadInterval(configuration, addressKey));
                        }
                    }
                });
            }
        }
    }

    private void scheduleReadJob(GroupAddress groupAddress, String dpt, boolean immediate, int readInterval) {
        if (knxScheduler == null || dpt == null) {
            return;
        }

        boolean recurring = readInterval > 0 ? true : false;

        if (immediate) {
            knxScheduler.schedule(new ReadRunnable(groupAddress, dpt), 0, TimeUnit.SECONDS);
        }

        if (recurring) {
            ScheduledFuture<?> future = readFutures.get(groupAddress);
            if (future == null || future.isDone() || future.isCancelled()) {
                int initialDelay = immediate ? 0 : readInterval;
                future = knxScheduler.scheduleWithFixedDelay(new ReadRunnable(groupAddress, dpt), initialDelay,
                        readInterval, TimeUnit.SECONDS);
                readFutures.put(groupAddress, future);
            }
        }
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        super.bridgeStatusChanged(bridgeStatusInfo);

        if (bridgeStatusInfo.getStatus() == ThingStatus.ONLINE) {
            getBridgeHandler().registerGroupAddressListener(this);
            scheduleReadJobs();
            updateStatus(ThingStatus.ONLINE, ThingStatusDetail.NONE);
        } else if (bridgeStatusInfo.getStatus() == ThingStatus.OFFLINE) {
            cancelReadFutures();
            getBridgeHandler().unregisterGroupAddressListener(this);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
        }
    }

    @Override
    public boolean listensTo(IndividualAddress source) {
        if (address != null) {
            return address.equals(source);
        } else {
            return false;
        }
    }

    @Override
    public boolean listensTo(GroupAddress destination) {
        return groupAddresses.contains(destination) || foundGroupAddresses.contains(destination);
    }

    @Override
    public void handleUpdate(ChannelUID channelUID, State state) {
        if (blockedChannels.remove(channelUID)) {
            logger.trace("Skipping the Update '{}' for channel {}", state, channelUID);
        } else {
            logger.trace("Handling an Update '{}' for channel {}", state, channelUID);

            withKNXType(channelUID, (selector, channelConfiguration) -> {
                if (selector.isAutoUpdate(channelConfiguration)) {
                    blockedChannels.add(channelUID);
                }
            });
            sendToKNX(channelUID, state);
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (getBridgeHandler() == null) {
            logger.warn("KNX bridge handler not found. Cannot handle commands without bridge.");
        }
        logger.trace("Handling a Command '{}' for channel {}", command, channelUID);
        if (command instanceof RefreshType) {
            logger.debug("Refreshing channel {}", channelUID);
            withKNXType(channelUID, (selector, channelConfiguration) -> {
                for (String addressKey : selector.getAddressKeys(channelConfiguration)) {
                    scheduleReadJob(selector.getGroupAddress(channelConfiguration, addressKey),
                            selector.getDPT(channelConfiguration, addressKey), true, 0);
                }
            });
        } else {
            switch (channelUID.getId()) {
                case CHANNEL_RESET:
                    if (address != null) {
                        restart();
                    }
                    break;
                default:
                    withKNXType(channelUID, (selector, channelConfiguration) -> {
                        if (selector.isAutoUpdate(channelConfiguration)) {
                            logger.trace("Adding channel {} to list for which an Update has to be skipped", channelUID);
                            blockedChannels.add(channelUID);
                        }
                    });
                    sendToKNX(channelUID, command);
                    break;
            }
        }
    }

    private void sendToKNX(ChannelUID channelUID, Type type) {
        withKNXType(channelUID, (selector, channelConfiguration) -> {
            Type convertedType = selector.convertType(channelConfiguration, type);
            if (logger.isTraceEnabled()) {
                logger.trace("Sending to channel {} {} : {} -> {}", channelUID.getId(),
                        getThing().getChannel(channelUID.getId()).getAcceptedItemType(), type, convertedType);
            }
            if (convertedType != null) {
                for (String addressKey : selector.getAddressKeys(channelConfiguration)) {
                    if (selector.getFlags(channelConfiguration, addressKey).contains(Flag.WRITE) && getBridgeHandler()
                            .toDPTValue(convertedType, selector.getDPT(channelConfiguration, addressKey)) != null) {
                        getBridgeHandler().writeToKNX(selector.getGroupAddress(channelConfiguration, addressKey),
                                selector.getDPT(channelConfiguration, addressKey), convertedType);

                        // The assumption is that channels are correctly configured, and thus, after the conversion of
                        // the type parameter, only one telegram has/can be sent on the knx bus. In the event that
                        // multiple addressKeys contain Flag.WRITE, then the conversion should be as such that only one
                        // of matches the converted value Type, and thus emit the Telegram on the bus

                        return;
                    }
                }

                // TODO : investigate to what extend Calimero allows to send ReadResponses to actors with a
                // Flag.UPDATE in the configuration for the given GroupAddress. We are currently using a
                // ProcessCommunicator which AFAIK only provides a write() method that does not make the distinction
                // between a WriteRequest and ReadResponse that is put on the bus

            }
        });
    }

    @Override
    public void onGroupRead(KNXBridgeBaseThingHandler bridge, IndividualAddress source, GroupAddress destination,
            byte[] asdu) {

        logger.trace("Thing {} received a Group Read Request telegram from '{}' for destination '{}'",
                getThing().getUID(), source, destination);

        // Nothing to do here - Software representations of physical actors should not respond to GroupRead requests, as
        // the physical device will be responding to these instead
    }

    @Override
    public void onGroupReadResponse(KNXBridgeBaseThingHandler bridge, IndividualAddress source,
            GroupAddress destination, byte[] asdu) {

        logger.trace("Thing {} received a Group Read Response telegram from '{}' for destination '{}'",
                getThing().getUID(), source, destination);

        for (Channel channel : getThing().getChannels()) {
            withKNXType(channel, (selector, channelConfiguration) -> {
                for (String addressKey : selector.getAddressKeys(channelConfiguration)) {
                    if (selector.getFlags(channelConfiguration, addressKey).contains(Flag.UPDATE)
                            || selector.getFlags(channelConfiguration, addressKey).contains(Flag.READ)) {
                        logger.trace(
                                "Thing {} processes a Group Read Response telegram for destination '{}' for channel '{}'",
                                getThing().getUID(), destination, channel.getUID());
                        processDataReceived(bridge, destination, asdu,
                                selector.getDPT(channelConfiguration, addressKey), channel.getUID());
                    }
                }
            });
        }
    }

    @Override
    public void onGroupWrite(KNXBridgeBaseThingHandler bridge, IndividualAddress source, GroupAddress destination,
            byte[] asdu) {

        logger.trace("Thing {} received a Group Write Request telegram from '{}' for destination '{}'",
                getThing().getUID(), source, destination);

        for (Channel channel : getThing().getChannels()) {
            withKNXType(channel, (selector, channelConfiguration) -> {
                for (String addressKey : selector.getAddressKeys(channelConfiguration)) {
                    if (selector.getFlags(channelConfiguration, addressKey).contains(Flag.TRANSMIT)) {
                        logger.trace(
                                "Thing {} processes a Group Write Request telegram for destination '{}' for channel '{}'",
                                getThing().getUID(), destination, channel.getUID());
                        processDataReceived(bridge, destination, asdu,
                                selector.getDPT(channelConfiguration, addressKey), channel.getUID());
                    }
                }
            });
        }
    }

    private void processDataReceived(KNXBridgeBaseThingHandler bridge, GroupAddress destination, byte[] asdu,
            String dpt, ChannelUID channelUID) {

        if (dpt != null) {

            if (!bridge.isDPTSupported(dpt)) {
                logger.warn("DPT {} is not supported by the KNX binding.", dpt);
                return;
            }

            Type type = bridge.getType(destination, dpt, asdu);

            if (type != null) {
                withKNXType(channelUID, (selector, channelConfiguration) -> {
                    if (selector.isAutoUpdate(channelConfiguration)) {
                        logger.trace("Adding channel {} to list for which an Update has to be skipped", channelUID);
                        blockedChannels.add(channelUID);
                        updateState(channelUID, (State) type);
                    } else {
                        postCommand(channelUID, (Command) type);
                    }
                });
            }
        } else {
            final char[] hexCode = "0123456789ABCDEF".toCharArray();
            StringBuilder sb = new StringBuilder(2 + asdu.length * 2);
            sb.append("0x");
            for (byte b : asdu) {
                sb.append(hexCode[(b >> 4) & 0xF]);
                sb.append(hexCode[(b & 0xF)]);
            }

            if (logger.isWarnEnabled()) {
                Datapoint datapoint = new CommandDP(destination, getThing().getUID().toString(), 0, dpt);
                logger.warn(
                        "Ignoring KNX bus data: couldn't transform to an openHAB type (not supported). Destination='{}', datapoint='{}', data='{}'",
                        destination, datapoint, sb);
            }
            return;
        }

    }

    public void restart() {
        if (address != null) {
            getBridgeHandler().restartNetworkDevice(address);
        }
    }

    class ReadRunnable implements Runnable {

        private GroupAddress address;
        private String dpt;

        ReadRunnable(GroupAddress address, String dpt) {
            this.address = address;
            this.dpt = dpt;
        }

        @Override
        public void run() {
            if (getThing().getStatus() == ThingStatus.ONLINE && getBridge().getStatus() == ThingStatus.ONLINE) {
                if (!getBridgeHandler().isDPTSupported(dpt)) {
                    logger.warn("DPT '{}' is not supported by the KNX binding", dpt);
                    return;
                }
                Datapoint datapoint = new CommandDP(address, getThing().getUID().toString(), 0, dpt);
                getBridgeHandler().readDatapoint(datapoint, getBridgeHandler().getReadRetriesLimit());
            }
        }
    };

    private void pollDeviceStatus() {
        try {
            if (address != null && getBridge().getStatus() == ThingStatus.ONLINE) {
                logger.debug("Polling the individual address {}", address.toString());
                boolean isReachable = getBridgeHandler().isReachable(address);
                if (isReachable) {
                    updateStatus(ThingStatus.ONLINE);
                    if (!filledDescription && config.getFetch()) {
                        if (descriptionJob == null || descriptionJob.isCancelled()) {
                            descriptionJob = knxScheduler.schedule(descriptionRunnable, 0, TimeUnit.MILLISECONDS);
                        }
                    }
                } else {
                    updateStatus(ThingStatus.OFFLINE);
                }
            }
        } catch (KNXException e) {
            logger.error("An error occurred while testing the reachability of a thing '{}' : {}", getThing().getUID(),
                    e.getLocalizedMessage());
            if (logger.isDebugEnabled()) {
                logger.error("", e);
            }
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getLocalizedMessage());
        }
    }

    private Runnable descriptionRunnable = new Runnable() {

        @Override
        public void run() {
            try {
                if (getBridge().getStatus() == ThingStatus.ONLINE) {
                    logger.debug("Fetching device information for address {}", address.toString());

                    Thread.sleep(OPERATION_INTERVAL);
                    byte[] data = getBridgeHandler().readDeviceDescription(address, 0, false, OPERATION_TIMEOUT);

                    if (data != null) {
                        final DD0 dd = DeviceDescriptor.DD0.fromType0(data);

                        Map<String, String> properties = editProperties();
                        properties.put(FIRMWARE_TYPE, Firmware.getName(dd.getFirmwareType()));
                        properties.put(FIRMWARE_VERSION, Firmware.getName(dd.getFirmwareVersion()));
                        properties.put(FIRMWARE_SUBVERSION, Firmware.getName(dd.getSubcode()));
                        try {
                            updateProperties(properties);
                        } catch (Exception e) {
                            // TODO : ignore for now, but for Things created through the DSL, this should also NOT throw
                            // an exception! See forum discussions
                        }
                        logger.info("The device with address {} is of type {}, version {}, subversion {}", address,
                                Firmware.getName(dd.getFirmwareType()), Firmware.getName(dd.getFirmwareVersion()),
                                Firmware.getName(dd.getSubcode()));
                    } else {
                        logger.warn("The KNX Actor with address {} does not expose a Device Descriptor", address);
                    }

                    // check if there is a Device Object in the KNX Actor
                    Thread.sleep(OPERATION_INTERVAL);
                    byte[] elements = getBridgeHandler().readDeviceProperties(address, DEVICE_OBJECT, PID.OBJECT_TYPE,
                            0, 1, false, OPERATION_TIMEOUT);
                    if ((elements == null ? 0 : toUnsigned(elements)) == 1) {

                        Thread.sleep(OPERATION_INTERVAL);
                        String ManufacturerID = Manufacturer
                                .getName(toUnsigned(getBridgeHandler().readDeviceProperties(address, DEVICE_OBJECT,
                                        PID.MANUFACTURER_ID, 1, 1, false, OPERATION_TIMEOUT)));
                        Thread.sleep(OPERATION_INTERVAL);
                        String serialNo = DataUnitBuilder.toHex(getBridgeHandler().readDeviceProperties(address,
                                DEVICE_OBJECT, PID.SERIAL_NUMBER, 1, 1, false, OPERATION_TIMEOUT), "");
                        Thread.sleep(OPERATION_INTERVAL);
                        String hardwareType = DataUnitBuilder.toHex(getBridgeHandler().readDeviceProperties(address,
                                DEVICE_OBJECT, HARDWARE_TYPE, 1, 1, false, OPERATION_TIMEOUT), " ");
                        Thread.sleep(OPERATION_INTERVAL);
                        String firmwareRevision = Integer
                                .toString(toUnsigned(getBridgeHandler().readDeviceProperties(address, DEVICE_OBJECT,
                                        PID.FIRMWARE_REVISION, 1, 1, false, OPERATION_TIMEOUT)));

                        Map<String, String> properties = editProperties();
                        properties.put(MANUFACTURER_NAME, ManufacturerID);
                        properties.put(MANUFACTURER_SERIAL_NO, serialNo);
                        properties.put(MANUFACTURER_HARDWARE_TYPE, hardwareType);
                        properties.put(MANUFACTURER_FIRMWARE_REVISION, firmwareRevision);
                        try {
                            updateProperties(properties);
                        } catch (Exception e) {
                            // TODO : ignore for now, but for Things created through the DSL, this should also NOT throw
                            // an exception! See forum discussions
                        }
                        logger.info("Identified device {} as a {}, type {}, revision {}, serial number {}", address,
                                ManufacturerID, hardwareType, firmwareRevision, serialNo);
                    } else {
                        logger.warn("The KNX Actor with address {} does not expose a Device Object", address);
                    }

                    // TODO : According to the KNX specs, devices should expose the PID.IO_LIST property in the DEVICE
                    // object, but it seems that a lot, if not all, devices do not do this. In this list we can find out
                    // what other kind of objects the device is exposing. Most devices do implement some set of objects,
                    // we will just go ahead and try to read them out irrespective of what is in the IO_LIST

                    Thread.sleep(OPERATION_INTERVAL);
                    byte[] tableaddress = getBridgeHandler().readDeviceProperties(address, ADDRESS_TABLE_OBJECT,
                            PID.TABLE_REFERENCE, 1, 1, false, OPERATION_TIMEOUT);

                    if (tableaddress != null) {
                        Thread.sleep(OPERATION_INTERVAL);
                        elements = getBridgeHandler().readDeviceMemory(address, toUnsigned(tableaddress), 1, false,
                                OPERATION_TIMEOUT);
                        if (elements != null) {
                            int numberOfElements = toUnsigned(elements);
                            logger.debug("The KNX Actor with address {} uses {} group addresses", address,
                                    numberOfElements - 1);

                            byte[] addressData = null;
                            while (addressData == null) {
                                Thread.sleep(OPERATION_INTERVAL);
                                addressData = getBridgeHandler().readDeviceMemory(address, toUnsigned(tableaddress) + 1,
                                        2, false, OPERATION_TIMEOUT);
                                if (addressData != null) {
                                    IndividualAddress individualAddress = new IndividualAddress(addressData);
                                    logger.debug(
                                            "The KNX Actor with address {} its real reported individual address is  {}",
                                            address, individualAddress);
                                }
                            }

                            for (int i = 1; i < numberOfElements; i++) {
                                addressData = null;
                                while (addressData == null) {
                                    Thread.sleep(OPERATION_INTERVAL);
                                    addressData = getBridgeHandler().readDeviceMemory(address,
                                            toUnsigned(tableaddress) + 1 + i * 2, 2, false, OPERATION_TIMEOUT);
                                    if (addressData != null) {
                                        GroupAddress groupAddress = new GroupAddress(addressData);
                                        foundGroupAddresses.add(groupAddress);
                                    }
                                }
                            }

                            for (GroupAddress anAddress : foundGroupAddresses) {
                                logger.debug("The KNX Actor with address {} uses Group Address {}", address, anAddress);
                            }
                        }
                    } else {
                        logger.warn("The KNX Actor with address {} does not expose a Group Address table", address);
                    }

                    Thread.sleep(OPERATION_INTERVAL);
                    byte[] objecttableaddress = getBridgeHandler().readDeviceProperties(address, GROUPOBJECT_OBJECT,
                            PID.TABLE_REFERENCE, 1, 1, true, OPERATION_TIMEOUT);

                    if (objecttableaddress != null) {
                        Thread.sleep(OPERATION_INTERVAL);
                        elements = getBridgeHandler().readDeviceMemory(address, toUnsigned(objecttableaddress), 1,
                                false, OPERATION_TIMEOUT);
                        if (elements != null) {
                            int numberOfElements = toUnsigned(elements);
                            logger.debug("The KNX Actor with address {} has {} objects", address, numberOfElements);

                            for (int i = 1; i < numberOfElements; i++) {
                                byte[] objectData = null;
                                while (objectData == null) {
                                    Thread.sleep(OPERATION_INTERVAL);
                                    objectData = getBridgeHandler().readDeviceMemory(address,
                                            toUnsigned(objecttableaddress) + 1 + (i * 3), 3, false, OPERATION_TIMEOUT);

                                    logger.debug("Byte 1 {}",
                                            String.format("%8s", Integer.toBinaryString(objectData[0] & 0xFF))
                                                    .replace(' ', '0'));
                                    logger.debug("Byte 2 {}",
                                            String.format("%8s", Integer.toBinaryString(objectData[1] & 0xFF))
                                                    .replace(' ', '0'));
                                    logger.debug("Byte 3 {}",
                                            String.format("%8s", Integer.toBinaryString(objectData[2] & 0xFF))
                                                    .replace(' ', '0'));
                                }
                            }
                        }
                    } else {
                        logger.warn("The KNX Actor with address {} does not expose a Group Object table", address);
                    }

                    filledDescription = true;
                }
            } catch (Exception e) {
                logger.error("An exception occurred while fetching the device description for a Thing '{}' : {}",
                        getThing().getUID(), e.getMessage(), e);
            }
        }

        private int toUnsigned(final byte[] data) {
            int value = data[0] & 0xff;
            if (data.length == 1) {
                return value;
            }
            value = value << 8 | data[1] & 0xff;
            if (data.length == 2) {
                return value;
            }
            value = value << 16 | data[2] & 0xff << 8 | data[3] & 0xff;
            return value;
        }
    };

}
