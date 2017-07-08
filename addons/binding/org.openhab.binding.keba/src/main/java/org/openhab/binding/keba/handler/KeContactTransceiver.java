/**
 * Copyright (c) 2010-2017 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.keba.handler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.PortUnreachableException;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link KeContacTransceiver} is responsible for receiving UDP broadcast messages sent by the KEBA Charging
 * Stations. {@link KeContactHandler} willing to receive these messages have to register themselves with the
 * {@link KeContacTransceiver}
 *
 * @author Karel Goderis - Initial contribution
 */

class KeContacTransceiver {

    public static final int LISTENER_PORT_NUMBER = 7090;
    public static final int REMOTE_PORT_NUMBER = 7090;
    public static final int LISTENING_INTERVAL = 100;
    public static final int BUFFER_SIZE = 1024;
    public static final String IP_ADDRESS = "ipAddress";
    public static final String POLLING_REFRESH_INTERVAL = "refreshInterval";

    private DatagramChannel broadcastChannel;
    private SelectionKey broadcastKey;
    private Selector selector;
    private Thread transceiverThread;
    private boolean isStarted = false;
    private Set<KeContactHandler> listeners = Collections.synchronizedSet(new HashSet<KeContactHandler>());
    private Map<KeContactHandler, DatagramChannel> datagramChannels = Collections
            .synchronizedMap(new HashMap<KeContactHandler, DatagramChannel>());
    private Map<KeContactHandler, ByteBuffer> buffers = Collections
            .synchronizedMap(new HashMap<KeContactHandler, ByteBuffer>());

    private final Logger logger = LoggerFactory.getLogger(KeContacTransceiver.class);

    public void start() {
        if (!isStarted) {
            logger.debug("Starting the the KEBA KeContact transceiver");
            try {
                selector = Selector.open();

                if (transceiverThread == null) {
                    transceiverThread = new Thread(transceiverRunnable, "ESH-Keba-Transceiver");
                    transceiverThread.start();
                }

                broadcastChannel = DatagramChannel.open();
                broadcastChannel.socket().bind(new InetSocketAddress(LISTENER_PORT_NUMBER));
                broadcastChannel.configureBlocking(false);

                logger.info("Listening for incoming data on {}", broadcastChannel.getLocalAddress());

                synchronized (selector) {
                    selector.wakeup();
                    broadcastKey = broadcastChannel.register(selector, broadcastChannel.validOps());
                }

                for (KeContactHandler listener : listeners) {
                    establishConnection(listener);
                }

                isStarted = true;
            } catch (ClosedSelectorException | CancelledKeyException | IOException e) {
                logger.error("An exception occurred while registering the selector: {}", e.getMessage());
            }
        }
    }

    public void stop() {
        if (isStarted) {
            for (KeContactHandler listener : listeners) {
                this.removeConnection(listener);
            }

            try {
                broadcastChannel.close();
            } catch (IOException e) {
                logger.error("An exception occurred while closing the broadcast channel on port number {} : '{}'",
                        LISTENER_PORT_NUMBER, e.getMessage(), e);
            }

            try {
                selector.close();
            } catch (IOException e) {
                logger.error("An exception occurred while closing the selector: '{}'", e.getMessage(), e);
            }

            logger.debug("Stopping the the KEBA KeContact transceiver");
            if (transceiverThread != null) {
                transceiverThread.interrupt();
            }

            isStarted = false;
        }
    }

    private void reset() {
        stop();
        isStarted = false;
        start();
    }

    public void registerHandler(KeContactHandler handler) {
        if (handler != null) {
            listeners.add(handler);

            if (logger.isTraceEnabled()) {
                logger.trace("There are now {} KEBA KeContact handlers registered with the transceiver",
                        listeners.size());
            }

            if (listeners.size() == 1) {
                start();
            }

            if (!isConnected(handler)) {
                establishConnection(handler);
            }
        }
    }

    public void unRegisterHandler(KeContactHandler handler) {
        if (handler != null) {
            listeners.remove(handler);

            if (logger.isTraceEnabled()) {
                logger.trace("There are now {} KEBA KeContact handlers registered with the transceiver",
                        listeners.size());
            }

            if (listeners.size() == 0) {
                stop();
            }
        }
    }

    protected ByteBuffer send(String message, KeContactHandler handler) {
        DatagramChannel theChannel = datagramChannels.get(handler);

        if (theChannel != null) {
            SelectionKey theSelectionKey = theChannel.keyFor(selector);

            synchronized (selector) {
                try {
                    selector.selectNow();
                } catch (IOException e) {
                    logger.error("An exception occurred while selecting: {}", e.getMessage());
                }
            }

            Iterator<SelectionKey> it = selector.selectedKeys().iterator();
            while (it.hasNext()) {
                SelectionKey selKey = it.next();
                it.remove();
                if (selKey.isValid() && selKey.isWritable() && selKey.equals(theSelectionKey)) {
                    boolean error = false;

                    try {
                        ByteBuffer buffer = ByteBuffer.allocate(message.getBytes().length);
                        buffer.put(message.getBytes("ASCII"));
                        buffer.rewind();

                        logger.debug("Sending '{}' on the channel '{}'->'{}'",
                                new Object[] { new String(buffer.array()), theChannel.getLocalAddress(),
                                        theChannel.getRemoteAddress() });
                        buffers.put(handler, ByteBuffer.allocate(BUFFER_SIZE));
                        theChannel.write(buffer);
                        synchronized (buffers.get(handler)) {
                            buffers.get(handler).wait();
                        }
                        return buffers.remove(handler);
                    } catch (NotYetConnectedException e) {
                        handler.updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                                "The remote host is not yet connected");
                        error = true;
                    } catch (ClosedChannelException e) {
                        handler.updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                                "The connection to the remote host is closed");
                        error = true;
                    } catch (IOException e) {
                        handler.updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                                "An IO exception occurred");
                        error = true;
                    } catch (InterruptedException e) {
                        handler.updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                                "An IO exception occurred");
                        error = true;
                    }

                    if (error) {
                        removeConnection(handler);
                        establishConnection(handler);
                    }
                }
            }
        }
        return null;
    }

    public Runnable transceiverRunnable = new Runnable() {

        @Override
        public void run() {
            while (true) {
                try {
                    synchronized (selector) {
                        try {
                            selector.selectNow();
                        } catch (IOException e) {
                            logger.error("An exception occurred while selecting: {}", e.getMessage());
                        }
                    }

                    Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                    while (it.hasNext()) {
                        SelectionKey selKey = it.next();
                        it.remove();
                        if (selKey.isValid() && selKey.isReadable()) {
                            int numberBytesRead = 0;
                            InetSocketAddress clientAddress = null;
                            boolean error = false;
                            ByteBuffer readBuffer = null;

                            if (selKey.equals(broadcastKey)) {
                                try {
                                    readBuffer = ByteBuffer.allocate(BUFFER_SIZE);
                                    clientAddress = (InetSocketAddress) broadcastChannel.receive(readBuffer);
                                    logger.debug("Received {} from {} on the transceiver listener port ",
                                            new String(readBuffer.array()), clientAddress);
                                    numberBytesRead = readBuffer.position();
                                } catch (IOException e) {
                                    logger.error(
                                            "An exception occurred while receiving data on the transceiver listener port: '{}'",
                                            e.getMessage(), e);
                                    error = true;
                                }

                                if (numberBytesRead == -1) {
                                    error = true;
                                }

                                if (!error) {
                                    readBuffer.flip();

                                    if (readBuffer != null && readBuffer.remaining() > 0) {
                                        for (KeContactHandler handler : listeners) {
                                            if (clientAddress != null && handler.getIPAddress()
                                                    .equals(clientAddress.getAddress().getHostAddress())) {
                                                ByteBuffer prevBuffer = buffers.get(handler);
                                                if (prevBuffer != null) {
                                                    buffers.put(handler, readBuffer);
                                                    synchronized (prevBuffer) {
                                                        prevBuffer.notifyAll();
                                                    }
                                                } else {
                                                    handler.onData(readBuffer);
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    listeners.forEach(listener -> listener.updateStatus(ThingStatus.OFFLINE,
                                            ThingStatusDetail.COMMUNICATION_ERROR, "The transceiver is offline"));
                                    reset();
                                }
                            } else {
                                DatagramChannel theChannel = (DatagramChannel) selKey.channel();
                                KeContactHandler theListener = null;

                                for (KeContactHandler listener : listeners) {
                                    if (datagramChannels.get(listener).equals(theChannel)) {
                                        theListener = listener;
                                        break;
                                    }
                                }

                                if (theListener != null) {
                                    try {
                                        readBuffer = buffers.get(theListener);
                                        numberBytesRead = theChannel.read(readBuffer);
                                    } catch (NotYetConnectedException e) {
                                        theListener.updateStatus(ThingStatus.OFFLINE,
                                                ThingStatusDetail.COMMUNICATION_ERROR,
                                                "The remote host is not yet connected");
                                        error = true;
                                    } catch (PortUnreachableException e) {
                                        theListener.updateStatus(ThingStatus.OFFLINE,
                                                ThingStatusDetail.CONFIGURATION_ERROR,
                                                "The remote host is probably not a KEBA KeContact");
                                        error = true;
                                    } catch (IOException e) {
                                        theListener.updateStatus(ThingStatus.OFFLINE,
                                                ThingStatusDetail.COMMUNICATION_ERROR, "An IO exception occurred");
                                        error = true;
                                    }

                                    if (numberBytesRead == -1) {
                                        error = true;
                                    }
                                }

                                if (!error) {
                                    if (readBuffer != null) {
                                        readBuffer.flip();
                                        synchronized (readBuffer) {
                                            readBuffer.notifyAll();
                                        }
                                    }
                                } else {
                                    removeConnection(theListener);
                                    establishConnection(theListener);
                                }
                            }
                        }
                    }

                    if (!Thread.currentThread().isInterrupted()) {
                        Thread.sleep(LISTENING_INTERVAL);
                    } else {
                        return;
                    }
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    };

    private void establishConnection(KeContactHandler handler) {
        if (handler.getThing().getStatusInfo().getStatusDetail() != ThingStatusDetail.CONFIGURATION_ERROR
                && handler.getConfig().get(IP_ADDRESS) != null && !handler.getConfig().get(IP_ADDRESS).equals("")) {
            logger.debug("Establishing the connection to the KEBA KeContact '{}'", handler.getThing().getUID());

            DatagramChannel datagramChannel = null;
            try {
                datagramChannel = DatagramChannel.open();
            } catch (Exception e2) {
                handler.updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                        "An exception occurred while opening a datagram channel");
            }

            if (datagramChannel != null) {
                datagramChannels.put(handler, datagramChannel);

                try {
                    datagramChannel.configureBlocking(false);
                } catch (IOException e2) {
                    handler.updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                            "An exception occurred while configuring a datagram channel");
                }

                synchronized (selector) {
                    selector.wakeup();
                    int interestSet = SelectionKey.OP_READ | SelectionKey.OP_WRITE;
                    try {
                        datagramChannel.register(selector, interestSet);
                    } catch (ClosedChannelException e1) {
                        handler.updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                                "An exception occurred while registering a selector");
                    }

                    InetSocketAddress remoteAddress = new InetSocketAddress(
                            (String) handler.getConfig().get(IP_ADDRESS), REMOTE_PORT_NUMBER);

                    try {
                        if (logger.isTraceEnabled()) {
                            logger.trace("Connecting the channel for {} ", remoteAddress);
                        }
                        datagramChannel.connect(remoteAddress);

                        handler.updateStatus(ThingStatus.ONLINE, ThingStatusDetail.NONE, "");
                    } catch (Exception e) {
                        logger.error("An exception occurred while connecting connecting to '{}:{}' : {}", new Object[] {
                                (String) handler.getConfig().get(IP_ADDRESS), REMOTE_PORT_NUMBER, e.getMessage() });
                        handler.updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                                "An exception occurred while connecting");
                    }
                }
            }
        } else {
            handler.updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    handler.getThing().getStatusInfo().getDescription());
        }
    }

    private void removeConnection(KeContactHandler handler) {
        logger.debug("Tearing down the connection to the KEBA KeContact '{}'", handler.getThing().getUID());
        DatagramChannel datagramChannel = datagramChannels.remove(handler);

        if (datagramChannel != null) {
            try {
                datagramChannel.keyFor(selector).cancel();
                datagramChannel.close();
                handler.updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.NONE, "");
            } catch (Exception e) {
                logger.error("An exception occurred while closing the datagramchannel for '{}': {}",
                        handler.getThing().getUID(), e.getMessage());
                handler.updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                        "An exception occurred while closing the datagramchannel");
            }
        }
    }

    private boolean isConnected(KeContactHandler handler) {
        return datagramChannels.get(handler) != null ? true : false;
    }
}
