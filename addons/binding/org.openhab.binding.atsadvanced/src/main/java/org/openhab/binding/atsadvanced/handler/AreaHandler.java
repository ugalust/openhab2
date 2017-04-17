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
import org.openhab.binding.atsadvanced.ATSadvancedBindingConstants.AreaStatusFlags;
import org.openhab.binding.atsadvanced.internal.PanelClient.MessageResponse;
import org.openhab.binding.atsadvanced.internal.PanelClient.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AreaHandler extends BaseThingHandler implements PanelStatusListener {

    // List of Configuration constants
    public static final String NUMBER = "number";
    public static final String NAME = "name";

    private Logger logger = LoggerFactory.getLogger(AreaHandler.class);

    private PanelHandler bridgeHandler;
    private ArrayList<AreaStatusFlags> previousStatus = new ArrayList<AreaStatusFlags>();
    private ArrayList<AreaStatusFlags> lastStatus = new ArrayList<AreaStatusFlags>();
    private boolean inErrorState = false;

    public AreaHandler(Thing thing) {
        super(thing);
        // TODO Auto-generated constructor stub
    }

    @Override
    public void initialize() {
        previousStatus = new ArrayList<AreaStatusFlags>();
        lastStatus = new ArrayList<AreaStatusFlags>();

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
        } else {
            if (channelUID.getId().equals(ATSadvancedBindingConstants.SET)) {
                if (command instanceof OnOffType) {
                    if (command == OnOffType.ON) {
                        panel.setArea(((BigDecimal) getConfig().get(NUMBER)).intValue());
                    } else {
                        panel.unsetArea(((BigDecimal) getConfig().get(NUMBER)).intValue());
                    }
                    onChangedStatus();
                }
            }
        }
    }

    @Override
    public void onBridgeDisconnected(PanelHandler bridge) {
        if (getThing().getStatus() == ThingStatus.ONLINE) {
            logger.debug("Updating thing '{}' status to OFFLINE.", getThing().getUID());
            updateStatus(ThingStatus.OFFLINE);
        }
    }

    @Override
    public void onBridgeConnected(PanelHandler bridge) {
        if (getThing().getStatus() == ThingStatus.OFFLINE) {
            logger.debug("Updating thing '{}' status to ONLINE.", this.getThing().getUID());
            updateStatus(ThingStatus.ONLINE);
        }
        updateName();
        onChangedStatus();
    }

    public boolean isAlarm() {
        return lastStatus.contains(AreaStatusFlags.AREV_ALARM);
    }

    public boolean isSet() {
        return lastStatus.contains(AreaStatusFlags.AREV_FULLSET);
    }

    public boolean wasAlarm() {
        return previousStatus.contains(AreaStatusFlags.AREV_ALARM);
    }

    public boolean wasSet() {
        return previousStatus.contains(AreaStatusFlags.AREV_FULLSET);
    }

    public boolean isExit() {
        return lastStatus.contains(AreaStatusFlags.AREV_EXIT);
    }

    public boolean wasExit() {
        return previousStatus.contains(AreaStatusFlags.AREV_EXIT);
    }

    @Override
    public void onChangedStatus() {
        if (getThing().getStatus() == ThingStatus.ONLINE) {
            previousStatus = lastStatus;
            ArrayList<AreaStatusFlags> result = null;
            if (!inErrorState) {
                result = getBridgeHandler().getAreaStatus(((BigDecimal) getConfig().get(NUMBER)).intValue());
            } else {
                logger.warn("Area '{}' is in an error state, do you have access?", getConfig().get(NAME));
            }

            if (result != null) {

                lastStatus = result;

                logger.debug("Area '{}' has changed status from '{}' to '{}'", new Object[] {
                        (String) getConfig().get(NAME), getPreviousStatus().toString(), getLastStatus().toString() });

                if (isSet() && !wasSet()) {
                    updateState(new ChannelUID(getThing().getUID(), ATSadvancedBindingConstants.SET), OnOffType.ON);
                }

                if (!isSet() && wasSet()) {
                    updateState(new ChannelUID(getThing().getUID(), ATSadvancedBindingConstants.SET), OnOffType.OFF);
                }

                if (!isSet() && !wasSet()) {
                    updateState(new ChannelUID(getThing().getUID(), ATSadvancedBindingConstants.SET), OnOffType.OFF);
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

                if (isExit() && !wasExit()) {
                    updateState(new ChannelUID(getThing().getUID(), ATSadvancedBindingConstants.EXIT), OnOffType.ON);
                }

                if (!isExit() && wasExit()) {
                    updateState(new ChannelUID(getThing().getUID(), ATSadvancedBindingConstants.EXIT), OnOffType.OFF);
                }

                if (!isExit() && !wasExit()) {
                    updateState(new ChannelUID(getThing().getUID(), ATSadvancedBindingConstants.EXIT), OnOffType.OFF);
                }
            } else {
                inErrorState = true;
            }
        }
    }

    public ArrayList<AreaStatusFlags> getPreviousStatus() {
        return previousStatus;
    }

    public ArrayList<AreaStatusFlags> getLastStatus() {
        return lastStatus;
    }

    private void updateName() {
        if ((String) getConfig().get(NAME) == null && getThing().getStatus() == ThingStatus.ONLINE) {
            PanelHandler panel = getBridgeHandler();

            MessageResponse result = panel.getAreaNamesChunk(((BigDecimal) getConfig().get(NUMBER)).intValue());

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

}
