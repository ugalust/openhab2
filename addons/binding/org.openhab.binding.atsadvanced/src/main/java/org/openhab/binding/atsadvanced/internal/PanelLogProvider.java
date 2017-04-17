package org.openhab.binding.atsadvanced.internal;

import org.openhab.binding.atsadvanced.handler.PanelHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.servicestack.client.LogProvider;
import net.servicestack.client.LogType;

public class PanelLogProvider extends LogProvider {

    private Logger logger = LoggerFactory.getLogger(PanelHandler.class);

    @Override
    public void println(LogType type, Object message) {
        logger.trace(getPrefix() + logTypeString(type) + ": " + message);
    }

    @Override
    public boolean isDebugEnabled() {
        return true;
    }

}
