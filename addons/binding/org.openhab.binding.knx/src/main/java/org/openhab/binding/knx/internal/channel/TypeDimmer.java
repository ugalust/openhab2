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
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.types.Type;

import com.google.common.collect.Sets;

class TypeDimmer extends KNXChannelType {

    TypeDimmer() {
        super(CHANNEL_DIMMER,
                Sets.newHashSet(SWITCH_GA, STATUS_GA, INCREASE_DECREASE_GA, POSITION_GA, POSITION_STATUS_GA));
    }

    @Override
    public String getDPT(Configuration configuration, String addressKey) {

        if (super.getDPT(configuration, addressKey) == null) {
            switch ((addressKey != null) ? addressKey : DEFAULT_ADDRESS_KEY) {
                case SWITCH_GA:
                    return "1.001";
                case STATUS_GA:
                    return "1.001";
                case INCREASE_DECREASE_GA:
                    return "3.007";
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
        if (type instanceof OnOffType) {
            if (configuration.get(SWITCH_GA) != null) {
                return type;
            } else if (configuration.get(POSITION_GA) != null) {
                return ((OnOffType) type).as(PercentType.class);
            }
        }

        if (type instanceof PercentType) {
            if (configuration.get(POSITION_GA) != null) {
                return type;
            } else if (configuration.get(SWITCH_GA) != null) {
                return ((PercentType) type).as(OnOffType.class);
            }
        }

        return type;
    }
}
