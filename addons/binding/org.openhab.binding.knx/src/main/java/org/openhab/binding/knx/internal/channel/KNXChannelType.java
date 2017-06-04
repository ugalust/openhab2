/**
 * Copyright (c) 2010-2017 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.knx.internal.channel;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.types.Type;
import org.openhab.binding.knx.internal.handler.Flag;

import com.google.common.collect.Sets;

import tuwien.auto.calimero.GroupAddress;
import tuwien.auto.calimero.exception.KNXFormatException;

/**
 * The {@link KNXChannelType} is the base class to define KNX Channel Types. Each Channel can be linked to KNX Group
 * Addresses by means of 'address keys' that are specific for each Channel Type. The configuration of the Group Address
 * is as follows:
 *
 * <DPT>:<Group Address>:<Flags>:<Interval>
 *
 * with
 *
 * <DPT> the DPT of the Group Adrress in x.y notation, for example "1.001" for a binary switch
 *
 * <Group Address> the Group Address itself in x/y/z notation
 *
 * <Flags> the KNX (!) flags for the Group Address, i.e. R/W/T/U for Read/Write/Transmit/Update. For example, "RT" to
 * indicate that the given GA can be read on the bus, and to indicate that the actor, on which the the Channel is
 * defined, will (T)ransmit updates for the GA by means of WriteRequets
 *
 * <Interval> [If the R Flag is defined"] the interval that the runtime will read out the GA on the KNX bus
 *
 * Only the <Group Address> part is mandatory. For example, statusGA="1/1/1" and statusGA="1.001:1/1/1:RT:3600" are
 * equivalent definitions. Depending on the Channel Type, a default <DPT>, <Flags> and <Interval> will be provided
 *
 * In short, this means that 'address keys' will behave as follows:
 *
 * R -> A KNX ReadRequest is put on the bus every <Interval> seconds, and the KNX ReadRequestResponse, that is normally
 * received later on, will update the status of the Channel
 * W -> if handleCommand() is called on the ThingHandler, then a KNX WriteRequest is put on the bus
 * T -> if a KNX WriteRequest is received, then the status of the Channel is updated
 * U -> if a KNX ReadRequestResponse is received, then the status of the Channel is updated
 *
 *
 * @author Simon Kaufmann - Initial contribution
 * @author Karel Goderis - Generalize configuration format for Group Addresses
 */
public class KNXChannelType {

    private static final int DEFAULT_READ_INTERVAL = 3600;
    private final String channelTypeID;
    private final Set<String> addressKeys;

    public KNXChannelType(String channelTypeID, Set<String> addressKeys) {
        this.channelTypeID = channelTypeID;
        this.addressKeys = addressKeys;
    }

    public final String getChannelID() {
        return channelTypeID;
    }

    public final Set<GroupAddress> getAddresses(Configuration configuration) throws KNXFormatException {
        Set<GroupAddress> ret = new HashSet<>();
        for (String addressKey : getAddressKeys(configuration)) {
            if (configuration != null && configuration.get(addressKey) != null) {
                ret.add(new GroupAddress(getGroupAddressAsString(configuration, addressKey)));
            }
        }
        return ret;
    }

    public Set<String> getAddressKeys(Configuration configuration) {

        Set<String> configuredAddressKeys = Sets.newHashSet();

        for (String addressKey : addressKeys) {
            if (configuration.get(addressKey) != null) {
                configuredAddressKeys.add(addressKey);
            }
        }

        return configuredAddressKeys;
    }

    public Set<String> getAddressKeys() {
        return addressKeys;
    }

    protected final Set<String> asSet(String... values) {
        return Sets.newHashSet(values);
    }

    protected final Set<Flag> asSet(Flag... values) {
        return Sets.newHashSet(values);
    }

    public Type convertType(Configuration configuration, Type type) {
        return type;
    }

    @Override
    public String toString() {
        return channelTypeID;
    }

    private boolean hasOnlyGroupAddress(Configuration configuration, String addressKey) {
        String config = (String) configuration.get(addressKey);
        String[] configParts = config.split(":");
        return configParts.length == 1 ? true : false;
    }

    private boolean isGroupAddress(String address) {
        String[] addressParts = address.split("/");
        return addressParts.length == 3 ? true : false;
    }

    private boolean hasDPT(Configuration configuration, String addressKey) {
        String config = (String) configuration.get(addressKey);
        String[] configParts = config.split(":");
        if (configParts.length >= 2) {
            return isGroupAddress(configParts[1]) ? true : false;
        } else {
            return false;
        }
    }

    private boolean hasFlags(Configuration configuration, String addressKey) {
        String config = (String) configuration.get(addressKey);
        String[] configParts = config.split(":");
        if (configParts.length == 4) {
            return isGroupAddress(configParts[configParts.length - 3]) ? true : false;
        } else if (configParts.length > 1) {
            return isGroupAddress(configParts[configParts.length - 2]) ? true : false;
        } else {
            return false;
        }
    }

    public String getGroupAddressAsString(Configuration configuration, String addressKey) {
        String config = (String) configuration.get(addressKey);
        if (hasOnlyGroupAddress(configuration, addressKey)) {
            return config;
        } else {
            String[] configParts = config.split(":");
            return isGroupAddress(configParts[1]) ? configParts[1] : null;
        }
    }

    public GroupAddress getGroupAddress(Configuration configuration, String addressKey) throws KNXFormatException {
        return new GroupAddress(getGroupAddressAsString(configuration, addressKey));
    }

    public String getDPT(Configuration configuration, String addressKey) {
        if (hasDPT(configuration, addressKey)) {
            String config = (String) configuration.get(addressKey);
            return config.split(":")[0];
        } else {
            return null;
        }
    }

    public Set<Flag> getFlags(Configuration configuration, String addressKey) {

        Set<Flag> asFlags = Sets.newHashSet();

        if (hasFlags(configuration, addressKey)) {
            String config = (String) configuration.get(addressKey);
            String[] configParts = config.split(":");
            String flags;

            if (configParts.length == 4) {
                flags = configParts[configParts.length - 2];
            } else {
                flags = configParts[configParts.length - 1];
            }

            for (char c : flags.toCharArray()) {
                asFlags.add(Flag.getFlag(c));
            }
        } else {
            asFlags = Collections.emptySet();
        }

        return asFlags;
    }

    public int getReadInterval(Configuration configuration, String addressKey) {
        if (hasFlags(configuration, addressKey)) {
            String config = (String) configuration.get(addressKey);
            String[] configParts = config.split(":");

            if (getFlags(configuration, addressKey).contains(Flag.READ)) {
                if (configParts.length == 4) {
                    return Integer.parseInt(configParts[configParts.length - 1]);
                } else {
                    return getDefaultReadInterval();
                }
            }
        }

        return -1;
    }

    protected int getDefaultReadInterval() {
        return DEFAULT_READ_INTERVAL;
    }

    public boolean isAutoUpdate(Configuration channelConfiguration) {
        return false;
    }
}
