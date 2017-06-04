/**
 * Copyright (c) 2010-2017 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.knx.internal.channel;

import static org.openhab.binding.knx.KNXBindingConstants.*;

import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.library.types.UpDownType;
import org.eclipse.smarthome.core.types.Type;

import com.google.common.collect.Sets;

class TypeRollershutter extends KNXChannelType {

    TypeRollershutter() {
        super(CHANNEL_ROLLERSHUTTER, Sets.newHashSet(UP_DOWN_GA, UP_DOWN_STATUS_GA, STOP_MOVE_GA, STOP_MOVE_STATUS_GA,
                POSITION_GA, POSITION_STATUS_GA));
    }

    @Override
    public String getDPT(Configuration configuration, String addressKey) {

        if (super.getDPT(configuration, addressKey) == null) {
            switch ((addressKey != null) ? addressKey : DEFAULT_ADDRESS_KEY) {
                case UP_DOWN_GA:
                    return "1.008";
                case UP_DOWN_STATUS_GA:
                    return "1.008";
                case STOP_MOVE_GA:
                    return "1.010";
                case STOP_MOVE_STATUS_GA:
                    return "1.010";
                case POSITION_GA:
                    return "5.001";
                case POSITION_STATUS_GA:
                    return "5.001";
                default:
                    return null;
            }
        } else {
            return super.getDPT(configuration, addressKey);
        }
    }

    @Override
    public Type convertType(Configuration configuration, Type type) {
        if (type instanceof UpDownType) {
            if (configuration.get(UP_DOWN_GA) != null) {
                return type;
            } else if (configuration.get(POSITION_GA) != null) {
                return ((UpDownType) type).as(PercentType.class);
            }
        }

        if (type instanceof PercentType) {
            if (configuration.get(POSITION_GA) != null) {
                return type;
            } else if (configuration.get(UP_DOWN_GA) != null) {
                return ((PercentType) type).as(UpDownType.class);
            }
        }

        return type;
    }
}
