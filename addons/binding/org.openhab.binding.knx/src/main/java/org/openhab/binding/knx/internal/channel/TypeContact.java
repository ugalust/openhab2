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

import java.util.Set;

import org.eclipse.smarthome.config.core.Configuration;
import org.openhab.binding.knx.internal.handler.Flag;

import com.google.common.collect.Sets;

class TypeContact extends KNXChannelType {

    TypeContact() {
        super(CHANNEL_CONTACT, Sets.newHashSet(GROUPADDRESS));
    }

    @Override
    public String getDPT(Configuration configuration, String addressKey) {
        return (getAddressKeys().contains(addressKey) && super.getDPT(configuration, addressKey) == null) ? "1.001"
                : super.getDPT(configuration, addressKey);
    }

    @Override
    public Set<Flag> getFlags(Configuration configuration, String addressKey) {
        return super.getFlags(configuration, addressKey).size() == 0 ? asSet(Flag.WRITE)
                : super.getFlags(configuration, addressKey);
    }
}