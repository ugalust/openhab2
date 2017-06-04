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

import com.google.common.collect.Sets;

class TypeOperatingHours extends KNXChannelType {

    TypeOperatingHours() {
        super(CHANNEL_OPERATING_HOURS, Sets.newHashSet(OPERATING_HOURS_GA));
    }
    //
    // @Override
    // public String getDPT(Configuration configuration, GroupAddress groupAddress) {
    // return "7.001";
    // }
    //
    // @Override
    // protected Set<String> getReadAddressKeys() {
    // return asSet(OPERATING_HOURS_GA);
    // }

}
