package org.openhab.binding.atsadvanced.handler;

import java.math.BigDecimal;
import java.util.ArrayList;

import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.openhab.binding.atsadvanced.ATSadvancedBindingConstants;
import org.openhab.binding.atsadvanced.PanelStatusListener;
import org.openhab.binding.atsadvanced.ATSadvancedBindingConstants.ZoneStatusFlags;
import org.openhab.binding.atsadvanced.internal.PanelClient.MessageResponse;
import org.openhab.binding.atsadvanced.internal.PanelClient.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZoneHandler extends BaseThingHandler implements PanelStatusListener {

    // List of Configuration constants
    public static final String NUMBER = "number";
    public static final String NAME = "name";

    private Logger logger = LoggerFactory.getLogger(ZoneHandler.class);

    private PanelHandler bridgeHandler;
    private ArrayList<ZoneStatusFlags> previousStatus = new ArrayList<ZoneStatusFlags>();
    private ArrayList<ZoneStatusFlags> lastStatus = new ArrayList<ZoneStatusFlags>();
    private boolean inErrorState = false;

    public ZoneHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        previousStatus = new ArrayList<ZoneStatusFlags>();
        lastStatus = new ArrayList<ZoneStatusFlags>();

        updateStatus(ThingStatus.OFFLINE);

        getBridgeHandler().registerStatusListener(this);
    }

    @Override
    public void dispose() {
        getBridgeHandler().unregisterStatusListener(this);
    }

    private synchronized PanelHandler getBridgeHandler() {
        if (this.bridgeHandler == null) {
            Bridge bridge = getBridge();
            if (bridge == null) {
                return null;
            }
            ThingHandler handler = bridge.getHandler();
            if (handler instanceof PanelHandler) {
                this.bridgeHandler = (PanelHandler) handler;
            } else {
                return null;
            }
        }
        return this.bridgeHandler;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {

        PanelHandler panel = getBridgeHandler();

        if (panel == null) {
            logger.warn("ATS Advanced Panel handler not found. Cannot handle command without bridge.");
            return;
        }

        if (command instanceof RefreshType) {
            onChangedStatus();
        }

    }

    @Override
    public void onBridgeDisconnected(PanelHandler bridge) {
        updateStatus(ThingStatus.OFFLINE);
    }

    @Override
    public void onBridgeConnected(PanelHandler bridge) {
        updateStatus(ThingStatus.ONLINE);
        updateName();
        onChangedStatus();
    }

    private void updateName() {

        if ((String) getConfig().get(NAME) == null && getThing().getStatus() == ThingStatus.ONLINE) {
            PanelHandler panel = getBridgeHandler();

            MessageResponse result = panel.getZoneNamesChunk(((BigDecimal) getConfig().get(NUMBER)).intValue());

            if (result != null) {
                for (Property property : result.getProperties()) {
                    if (property.getId().equals("name")) {
                        if (!(((String) property.getValue()).equals(""))) {
                            getThing().getConfiguration().put(NAME, property.getValue());
                        }
                        break;
                    }
                }
            }
        }
    }

    public boolean isActive() {
        return lastStatus.contains(ZoneStatusFlags.ZNEV_ACTIVE);
    }

    public boolean isAlarm() {
        return lastStatus.contains(ZoneStatusFlags.ZNEV_ALARM);
    }

    public boolean wasActive() {
        return previousStatus.contains(ZoneStatusFlags.ZNEV_ACTIVE);
    }

    public boolean wasAlarm() {
        return previousStatus.contains(ZoneStatusFlags.ZNEV_ALARM);
    }

    @Override
    public void onChangedStatus() {
        if (getThing().getStatus() == ThingStatus.ONLINE) {
            previousStatus = lastStatus;
            ArrayList<ZoneStatusFlags> result = null;
            if (!inErrorState) {
                result = getBridgeHandler().getZoneStatus(((BigDecimal) getConfig().get(NUMBER)).intValue());
            } else {
                logger.warn("Zone '{}' is in an error state, do you have access?", getConfig().get(NAME));
            }

            if (result != null) {

                lastStatus = result;

                logger.debug("Zone '{}' has changed status from '{}' to '{}'", new Object[] {
                        (String) getConfig().get(NAME), getPreviousStatus().toString(), getLastStatus().toString() });

                if (isActive() && !wasActive()) {
                    updateState(new ChannelUID(getThing().getUID(), ATSadvancedBindingConstants.ACTIVE), OnOffType.ON);
                }

                if (!isActive() && wasActive()) {
                    updateState(new ChannelUID(getThing().getUID(), ATSadvancedBindingConstants.ACTIVE), OnOffType.OFF);
                }

                if (!isActive() && !wasActive()) {
                    updateState(new ChannelUID(getThing().getUID(), ATSadvancedBindingConstants.ACTIVE), OnOffType.OFF);
                }

                if (isAlarm() && !wasAlarm()) {
                    updateState(new ChannelUID(getThing().getUID(), ATSadvancedBindingConstants.ALARM), OnOffType.ON);
                }

                if (!isAlarm() && wasAlarm()) {
                    updateState(new ChannelUID(getThing().getUID(), ATSadvancedBindingConstants.ALARM), OnOffType.OFF);
                }

                if (!isAlarm() && !wasAlarm()) {
                    updateState(new ChannelUID(getThing().getUID(), ATSadvancedBindingConstants.ALARM), OnOffType.OFF);
                }
            } else {
                inErrorState = true;
            }
        }
    }

    public ArrayList<ZoneStatusFlags> getPreviousStatus() {
        return previousStatus;
    }

    public ArrayList<ZoneStatusFlags> getLastStatus() {
        return lastStatus;
    }

}