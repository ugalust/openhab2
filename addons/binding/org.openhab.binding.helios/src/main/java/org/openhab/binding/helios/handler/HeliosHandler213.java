/**
 * Copyright (c) 2010-2017 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.helios.handler;

import static org.openhab.binding.helios.HeliosBindingConstants.*;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.xml.bind.DatatypeConverter;

import org.eclipse.smarthome.core.library.types.DateTimeType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.thing.binding.builder.ChannelBuilder;
import org.eclipse.smarthome.core.thing.binding.builder.ThingBuilder;
import org.eclipse.smarthome.core.thing.type.ChannelTypeUID;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.eclipse.smarthome.core.types.UnDefType;
import org.openhab.binding.helios.internal.ws.rest.RESTError;
import org.openhab.binding.helios.internal.ws.rest.RESTEvent;
import org.openhab.binding.helios.internal.ws.rest.RESTPort;
import org.openhab.binding.helios.internal.ws.rest.RESTSubscribeResponse;
import org.openhab.binding.helios.internal.ws.rest.RESTSwitch;
import org.openhab.binding.helios.internal.ws.rest.RESTSystemInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The {@link HeliosHandler213} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Karel Goderis - Initial contribution
 */

public class HeliosHandler213 extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(HeliosHandler213.class);

    // List of Configuration constants
    public static final String IP_ADDRESS = "ipAddress";
    public static final String USERNAME = "username";
    public static final String PASSWORD = "password";

    // List of all REST API URI, commands, and JSON constants
    public static final String BASE_URI = "https://{ip}/api/";
    public static final String SYSTEM_PATH = "system/{cmd}";
    public static final String FIRMWARE_PATH = "firmware/{cmd}";
    public static final String LOG_PATH = "log/{cmd}";
    public static final String SWITCH_PATH = "switch/{cmd}";
    public static final String PORT_PATH = "io/{cmd}";

    public static final String INFO = "info";
    public static final String STATUS = "status";

    public static final String SUBSCRIBE = "subscribe";
    public static final String UNSUBSCRIBE = "unsubscribe";
    public static final String PULL = "pull";
    public static final String CAPABILITIES = "caps";
    public static final String CONTROL = "ctrl";

    public static final String DEVICESTATE = "DeviceState";
    public static final String AUDIOLOOPTEST = "AudioLoopTest";
    public static final String MOTIONDETECTED = "MotionDetected";
    public static final String KEYPRESSED = "KeyPressed";
    public static final String KEYRELEASED = "KeyReleased";
    public static final String CODEENTERED = "CodeEntered";
    public static final String CARDENTERED = "CardEntered";
    public static final String INPUTCHANGED = "InputChanged";
    public static final String OUTPUTCHANGED = "OutputChanged";
    public static final String CALLSTATECHANGED = "CallStateChanged";
    public static final String REGISTRATIONSTATECHANGED = "RegistrationStateChanged";

    // REST Client API variables
    private Client heliosClient;
    private WebTarget baseTarget;
    private WebTarget systemTarget;
    private WebTarget logTarget;
    private WebTarget switchTarget;
    private WebTarget portTarget;
    private String ipAddress;

    // JSON variables
    private JsonParser parser = new JsonParser();
    private Gson gson = new Gson();

    private ScheduledFuture<?> logJob;
    private static final long RESET_INTERVAL = 15;
    private static final long HELIOS_DURATION = 120;
    private static final long HELIOS_PULL_DURATION = 10;

    private long logSubscriptionID = 0;

    public HeliosHandler213(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        logger.debug("Initializing the Helios IP Vario handler for '{}'.", getThing().getUID().toString());

        ipAddress = (String) getConfig().get(IP_ADDRESS);
        String username = (String) getConfig().get(USERNAME);
        String password = (String) getConfig().get(PASSWORD);

        if (ipAddress != null && !ipAddress.isEmpty() && username != null && !username.isEmpty() && password != null
                && !password.isEmpty()) {

            SecureRestClientTrustManager secureRestClientTrustManager = new SecureRestClientTrustManager();
            SSLContext sslContext = null;
            try {
                sslContext = SSLContext.getInstance("SSL");
            } catch (NoSuchAlgorithmException e1) {
                logger.error("An exception occurred while requesting the SSL encryption algorithm : '{}'",
                        e1.getMessage(), e1);
            }
            try {
                if (sslContext != null) {
                    sslContext.init(null, new javax.net.ssl.TrustManager[] { secureRestClientTrustManager }, null);
                }
            } catch (KeyManagementException e1) {
                logger.error("An exception occurred while initialising the SSL context : '{}'", e1.getMessage(), e1);
            }

            heliosClient = ClientBuilder.newBuilder().sslContext(sslContext).hostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, javax.net.ssl.SSLSession sslSession) {
                    return true;
                }
            }).build();
            heliosClient.register(new Authenticator(username, password));

            baseTarget = heliosClient.target(BASE_URI);
            systemTarget = baseTarget.path(SYSTEM_PATH);
            switchTarget = baseTarget.path(LOG_PATH);
            switchTarget = baseTarget.path(SWITCH_PATH);

            Response response = null;
            try {
                response = systemTarget.resolveTemplate("ip", ipAddress).resolveTemplate("cmd", INFO)
                        .request(MediaType.APPLICATION_JSON_TYPE).get();
            } catch (NullPointerException e) {
                logger.debug("An exception occurred while fetching system info of the Helios IP Vario '{}' : '{}'",
                        getThing().getUID().toString(), e.getMessage(), e);
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, e.getMessage());
                scheduler.schedule(resetRunnable, RESET_INTERVAL, TimeUnit.SECONDS);
                return;
            }

            if (response == null) {
                logger.warn("There is a configuration problem for the Helios IP Vario '{}'",
                        getThing().getUID().toString());
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR);
                scheduler.schedule(resetRunnable, RESET_INTERVAL, TimeUnit.SECONDS);
                return;
            }

            JsonObject jsonObject = parser.parse(response.readEntity(String.class)).getAsJsonObject();

            if (jsonObject.get("success").toString().equals("false")) {
                RESTError error = gson.fromJson(jsonObject.get("error").toString(), RESTError.class);
                logger.debug(
                        "An error occurred while communicating with the Helios IP Vario '{}': code '{}', param '{}' : '{}'",
                        new Object[] { getThing().getUID().toString(), error.code, error.param, error.description });
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                        error.code + ":" + error.param + ":" + error.description);
                scheduler.schedule(resetRunnable, RESET_INTERVAL, TimeUnit.SECONDS);
                return;
            }

            if (jsonObject.get("success").toString().equals("true")) {

                scheduler.schedule(configureRunnable, 0, TimeUnit.SECONDS);

                if (logJob == null || logJob.isCancelled()) {
                    logJob = scheduler.scheduleWithFixedDelay(logRunnable, 0, 1, TimeUnit.SECONDS);
                }

                updateStatus(ThingStatus.ONLINE);

                postInitialize();
            }
        }
    }

    public void postInitialize() {

        List<RESTSwitch> switches = getSwitches();

        if (switches != null) {
            for (RESTSwitch aSwitch : switches) {
                if (aSwitch.enabled.equals("true")) {
                    logger.debug("Adding channels to the Helios IP Vario '{}' for the switch with id '{}'",
                            getThing().getUID().toString(), aSwitch.id);
                    ThingBuilder thingBuilder = editThing();
                    ChannelTypeUID enablerUID = new ChannelTypeUID(BINDING_ID, SWITCH_ENABLER);
                    ChannelTypeUID triggerUID = new ChannelTypeUID(BINDING_ID, SWITCH_TRIGGER);

                    Channel channel = ChannelBuilder
                            .create(new ChannelUID(getThing().getUID(), "switch" + aSwitch.id + "active"), "Switch")
                            .withType(enablerUID).build();
                    thingBuilder.withChannel(channel);
                    channel = ChannelBuilder
                            .create(new ChannelUID(getThing().getUID(), "switch" + aSwitch.id), "Switch")
                            .withType(triggerUID).build();
                    thingBuilder.withChannel(channel);
                    updateThing(thingBuilder.build());
                }
            }
        }

        List<RESTPort> ports = getPorts();

        if (ports != null) {
            for (RESTPort aPort : ports) {
                logger.debug("Adding a channel to the Helios IP Vario '{}' for the IO port with id '{}'",
                        getThing().getUID().toString(), aPort.port);
                ThingBuilder thingBuilder = editThing();
                ChannelTypeUID triggerUID = new ChannelTypeUID(BINDING_ID, IO_TRIGGER);

                Map<String, String> channelProperties = new HashMap<String, String>();
                channelProperties.put("type", aPort.type);

                Channel channel = ChannelBuilder
                        .create(new ChannelUID(getThing().getUID(), "io" + aPort.port), "Switch").withType(triggerUID)
                        .withProperties(channelProperties).build();
                thingBuilder.withChannel(channel);
                updateThing(thingBuilder.build());
            }
        }
    }

    @Override
    public void dispose() {
        logger.debug("Disposing the Helios IP Vario handler for '{}'.", getThing().getUID().toString());

        if (logSubscriptionID != 0) {
            unsubscribe();
        }

        if (logJob != null && !logJob.isCancelled()) {
            logJob.cancel(true);
            logJob = null;
        }

        if (heliosClient != null) {
            heliosClient.close();
            heliosClient = null;
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {

        if (!(command instanceof RefreshType)) {
            ChannelTypeUID triggerUID = new ChannelTypeUID(BINDING_ID, SWITCH_TRIGGER);
            ChannelTypeUID enablerUID = new ChannelTypeUID(BINDING_ID, SWITCH_ENABLER);
            ChannelTypeUID channelType = getThing().getChannel(channelUID.getId()).getChannelTypeUID();

            if (channelType.equals(triggerUID)) {
                String switchID = channelUID.getId().substring(6);
                triggerSwitch(switchID);
            }

            if (channelType.equals(enablerUID)) {
                String switchID = channelUID.getId().substring(6, channelUID.getId().lastIndexOf("active"));
                if (command instanceof OnOffType && command == OnOffType.OFF) {
                    enableSwitch(switchID, false);
                } else if (command instanceof OnOffType && command == OnOffType.ON) {
                    enableSwitch(switchID, true);
                }
            }
        }
    }

    private long subscribe() {

        if (getThing().getStatus() == ThingStatus.ONLINE) {
            switchTarget = baseTarget.path(LOG_PATH);

            Response response = null;
            try {
                response = switchTarget.resolveTemplate("ip", ipAddress).resolveTemplate("cmd", SUBSCRIBE)
                        .queryParam("include", "new").queryParam("duration", HELIOS_DURATION)
                        .request(MediaType.APPLICATION_JSON_TYPE).get();
            } catch (NullPointerException e) {
                logger.debug(
                        "An exception occurred while subscribing to the log entries of the Helios IP Vario '{}' : '{}'",
                        getThing().getUID().toString(), e.getMessage(), e);
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
                scheduler.schedule(resetRunnable, RESET_INTERVAL, TimeUnit.SECONDS);
                return 0;
            }

            if (response != null) {
                JsonObject jsonObject = parser.parse(response.readEntity(String.class)).getAsJsonObject();
                if (jsonObject.get("success").toString().equals("true")) {
                    RESTSubscribeResponse subscribeResponse = gson.fromJson(jsonObject.get("result").toString(),
                            RESTSubscribeResponse.class);
                    logger.debug("The subscription id to pull logs from the Helios IP Vario '{}' is '{}'",
                            getThing().getUID().toString(), subscribeResponse.id);
                    return subscribeResponse.id;
                } else {
                    RESTError error = gson.fromJson(jsonObject.get("error").toString(), RESTError.class);
                    logger.debug(
                            "An error occurred while communicating with the Helios IP Vario '{}': code '{}', param '{}' : '{}'",
                            getThing().getUID().toString(), error.code, error.param, error.description);
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                            error.code + ":" + error.param + ":" + error.description);
                    scheduler.schedule(resetRunnable, RESET_INTERVAL, TimeUnit.SECONDS);
                    return 0;
                }
            } else {
                logger.debug("An error occurred while subscribing to the log entries of the Helios IP Vario '{}'",
                        getThing().getUID().toString());
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR);
                scheduler.schedule(resetRunnable, RESET_INTERVAL, TimeUnit.SECONDS);
                return 0;
            }
        }

        return 0;
    }

    private void unsubscribe() {

        if (getThing().getStatus() == ThingStatus.ONLINE) {
            switchTarget = baseTarget.path(LOG_PATH);

            Response response = null;
            try {
                response = switchTarget.resolveTemplate("ip", ipAddress).resolveTemplate("cmd", UNSUBSCRIBE)
                        .queryParam("id", logSubscriptionID).request(MediaType.APPLICATION_JSON_TYPE).get();
            } catch (Exception e) {
                logger.debug(
                        "An exception occurred while unsubscribing from the log entries of the Helios IP Vario '{}' : {}",
                        getThing().getUID().toString(), e.getMessage(), e);
                logSubscriptionID = 0;
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
                scheduler.schedule(resetRunnable, RESET_INTERVAL, TimeUnit.SECONDS);
                return;
            }

            if (response != null) {
                JsonObject jsonObject = parser.parse(response.readEntity(String.class)).getAsJsonObject();
                if (jsonObject.get("success").toString().equals("true")) {
                    logger.debug("Successfully unsubscribed from the log entries of the Helios IP Vario '{}'",
                            getThing().getUID().toString());
                } else {
                    RESTError error = gson.fromJson(jsonObject.get("error").toString(), RESTError.class);
                    logger.debug(
                            "An error occurred while communicating with the Helios IP Vario '{}' : code '{}', param '{}' : '{}'",
                            getThing().getUID().toString(), error.code, error.param, error.description);
                    logSubscriptionID = 0;
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                            error.code + ":" + error.param + ":" + error.description);
                    scheduler.schedule(resetRunnable, RESET_INTERVAL, TimeUnit.SECONDS);
                    return;
                }
            } else {
                logger.debug("An error occurred while unsubscribing from the log entries of the Helios IP Vario '{}'",
                        getThing().getUID().toString());
                logSubscriptionID = 0;
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR);
                scheduler.schedule(resetRunnable, RESET_INTERVAL, TimeUnit.SECONDS);
                return;
            }
        }
    }

    private List<RESTEvent> pullLog(long logSubscriptionID) {
        if (getThing().getStatus() == ThingStatus.ONLINE && heliosClient != null) {
            logTarget = baseTarget.path(LOG_PATH);

            Response response = null;
            try {
                long now = System.currentTimeMillis();
                response = logTarget.resolveTemplate("ip", ipAddress).resolveTemplate("cmd", PULL)
                        .queryParam("id", logSubscriptionID).queryParam("timeout", HELIOS_PULL_DURATION)
                        .request(MediaType.APPLICATION_JSON_TYPE).get();
                logger.trace("Pulled logs in {} millseconds from {}", System.currentTimeMillis() - now,
                        getThing().getUID());
            } catch (NullPointerException e) {
                logger.debug("An exception occurred while pulling log entries from the Helios IP Vario '{}' : '{}'",
                        getThing().getUID().toString(), e.getMessage(), e);
                this.logSubscriptionID = 0;
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
                scheduler.schedule(resetRunnable, RESET_INTERVAL, TimeUnit.SECONDS);
                return null;
            }

            if (response != null) {
                JsonObject jsonObject = parser.parse(response.readEntity(String.class)).getAsJsonObject();
                if (jsonObject.get("success").toString().equals("true")) {
                    logger.trace("Successfully pulled log entries from the Helios IP Vario '{}'",
                            getThing().getUID().toString());
                    JsonObject js = (JsonObject) jsonObject.get("result");
                    RESTEvent[] eventArray = gson.fromJson(js.getAsJsonArray("events"), RESTEvent[].class);
                    return Arrays.asList(eventArray);
                } else {
                    RESTError error = gson.fromJson(jsonObject.get("error").toString(), RESTError.class);
                    logger.debug(
                            "An error occurred while communicating with the Helios IP Vario '{}' : code '{}', param '{}' : '{}'",
                            getThing().getUID().toString(), error.code, error.param, error.description);
                    this.logSubscriptionID = 0;
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                            error.code + ":" + error.param + ":" + error.description);
                    scheduler.schedule(resetRunnable, RESET_INTERVAL, TimeUnit.SECONDS);
                    return null;
                }
            } else {
                logger.debug("An error occurred while polling log entries from the Helios IP Vario '{}'",
                        getThing().getUID().toString());
                this.logSubscriptionID = 0;
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR);
                scheduler.schedule(resetRunnable, RESET_INTERVAL, TimeUnit.SECONDS);
                return null;
            }
        }

        return null;
    }

    private List<RESTSwitch> getSwitches() {
        switchTarget = baseTarget.path(SWITCH_PATH);

        Response response = null;
        try {
            response = switchTarget.resolveTemplate("ip", ipAddress).resolveTemplate("cmd", CAPABILITIES)
                    .request(MediaType.APPLICATION_JSON_TYPE).get();
        } catch (NullPointerException e) {
            logger.debug(
                    "An exception occurred while requesting switch capabilities from the Helios IP Vario '{}' : '{}'",
                    getThing().getUID().toString(), e.getMessage(), e);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
            scheduler.schedule(resetRunnable, RESET_INTERVAL, TimeUnit.SECONDS);
            return null;
        }

        if (response != null) {
            JsonObject jsonObject = parser.parse(response.readEntity(String.class)).getAsJsonObject();
            if (jsonObject.get("success").toString().equals("true")) {
                logger.debug("Successfully requested switch capabilities from the Helios IP Vario '{}'",
                        getThing().getUID().toString());
                String result = jsonObject.get("result").toString();
                result = result.replace("switch", "id");
                JsonObject js = parser.parse(result).getAsJsonObject();
                RESTSwitch[] switchArray = gson.fromJson(js.getAsJsonArray("ides"), RESTSwitch[].class);
                if (switchArray != null) {
                    return Arrays.asList(switchArray);
                }
            } else {
                RESTError error = gson.fromJson(jsonObject.get("error").toString(), RESTError.class);
                logger.debug(
                        "An error occurred while communicating with the Helios IP Vario '{}' : code '{}', param '{}' : '{}'",
                        getThing().getUID().toString(), error.code, error.param, error.description);
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                        error.code + ":" + error.param + ":" + error.description);
                scheduler.schedule(resetRunnable, RESET_INTERVAL, TimeUnit.SECONDS);
                return null;
            }
        } else {
            logger.debug("An error occurred while requesting switch capabilities from the Helios IP Vario '{}'",
                    getThing().getUID().toString());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR);
            scheduler.schedule(resetRunnable, RESET_INTERVAL, TimeUnit.SECONDS);
            return null;
        }

        return null;
    }

    private void triggerSwitch(String id) {
        if (getThing().getStatus() == ThingStatus.ONLINE) {

            switchTarget = baseTarget.path(SWITCH_PATH);

            Response response = null;
            try {
                response = switchTarget.resolveTemplate("ip", ipAddress).resolveTemplate("cmd", CONTROL)
                        .queryParam("switch", id).queryParam("action", "trigger")
                        .request(MediaType.APPLICATION_JSON_TYPE).get();
            } catch (NullPointerException e) {
                logger.debug("An exception occurred while triggering a switch  on the Helios IP Vario '{}' : '{}'",
                        getThing().getUID().toString(), e.getMessage(), e);
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
                scheduler.schedule(resetRunnable, RESET_INTERVAL, TimeUnit.SECONDS);
                return;
            }

            if (response != null) {
                JsonObject jsonObject = parser.parse(response.readEntity(String.class)).getAsJsonObject();
                if (jsonObject.get("success").toString().equals("true")) {
                    logger.debug("Successfully triggered a switch on the Helios IP Vario '{}'",
                            getThing().getUID().toString());
                } else {
                    RESTError error = gson.fromJson(jsonObject.get("error").toString(), RESTError.class);
                    logger.error(
                            "An error occurred while communicating with the Helios IP Vario '{}' : code '{}', param '{}' : '{}'",
                            getThing().getUID().toString(), error.code, error.param, error.description);
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                            error.code + ":" + error.param + ":" + error.description);
                    scheduler.schedule(resetRunnable, RESET_INTERVAL, TimeUnit.SECONDS);
                    return;
                }
            } else {
                logger.warn("An error occurred while triggering a switch on the Helios IP Vario '{}'",
                        getThing().getUID().toString());
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR);
                scheduler.schedule(resetRunnable, RESET_INTERVAL, TimeUnit.SECONDS);
                return;
            }
        }
    }

    private void enableSwitch(String id, boolean flag) {
        if (getThing().getStatus() == ThingStatus.ONLINE) {

            switchTarget = baseTarget.path(SWITCH_PATH);

            Response response = null;
            try {
                response = switchTarget.resolveTemplate("ip", ipAddress).resolveTemplate("cmd", CONTROL)
                        .queryParam("switch", id).queryParam("action", flag ? "on" : "off")
                        .request(MediaType.APPLICATION_JSON_TYPE).get();
            } catch (NullPointerException e) {
                logger.error("An exception occurred while dis/enabling a switch  on the Helios IP Vario '{}' : '{}'",
                        getThing().getUID().toString(), e.getMessage(), e);
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
                scheduler.schedule(resetRunnable, RESET_INTERVAL, TimeUnit.SECONDS);
                return;
            }

            if (response != null) {
                JsonObject jsonObject = parser.parse(response.readEntity(String.class)).getAsJsonObject();
                if (jsonObject.get("success").toString().equals("true")) {
                    logger.debug("Successfully dis/enabled a  switch on the Helios IP Vario '{}'",
                            getThing().getUID().toString());
                } else {
                    RESTError error = gson.fromJson(jsonObject.get("error").toString(), RESTError.class);
                    logger.error(
                            "An error occurred while communicating with the Helios IP Vario '{}': code '{}', param '{}' : '{}'",
                            getThing().getUID().toString(), error.code, error.param, error.description);
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                            error.code + ":" + error.param + ":" + error.description);
                    scheduler.schedule(resetRunnable, RESET_INTERVAL, TimeUnit.SECONDS);
                    return;
                }
            } else {
                logger.warn("An error occurred while dis/enabling a switch on the Helios IP Vario '{}'",
                        getThing().getUID().toString());
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR);
                scheduler.schedule(resetRunnable, RESET_INTERVAL, TimeUnit.SECONDS);
                return;
            }
        }
    }

    private List<RESTPort> getPorts() {
        portTarget = baseTarget.path(PORT_PATH);

        Response response = null;
        try {
            response = portTarget.resolveTemplate("ip", ipAddress).resolveTemplate("cmd", CAPABILITIES)
                    .request(MediaType.APPLICATION_JSON_TYPE).get();
        } catch (NullPointerException e) {
            logger.error(
                    "An exception occurred while requesting port capabilities from the Helios IP Vario '{}' : '{}'",
                    getThing().getUID().toString(), e.getMessage(), e);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
            scheduler.schedule(resetRunnable, RESET_INTERVAL, TimeUnit.SECONDS);
            return null;
        }

        if (response != null) {
            JsonObject jsonObject = parser.parse(response.readEntity(String.class)).getAsJsonObject();
            if (jsonObject.get("success").toString().equals("true")) {
                logger.debug("Successfully requested port capabilities from the Helios IP Vario '{}'",
                        getThing().getUID().toString());
                JsonObject js = (JsonObject) jsonObject.get("result");
                RESTPort[] portArray = gson.fromJson(js.getAsJsonArray("ports"), RESTPort[].class);
                if (portArray != null) {
                    return Arrays.asList(portArray);
                }
            } else {
                RESTError error = gson.fromJson(jsonObject.get("error").toString(), RESTError.class);
                logger.error(
                        "An error occurred while communicating with the Helios IP Vario '{}': code '{}', param '{}' : '{}'",
                        getThing().getUID().toString(), error.code, error.param, error.description);
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                        error.code + ":" + error.param + ":" + error.description);
                scheduler.schedule(resetRunnable, RESET_INTERVAL, TimeUnit.SECONDS);
                return null;
            }
        } else {
            logger.warn("An error occurred while requesting port capabilities from the Helios IP Vario '{}'",
                    getThing().getUID().toString());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR);
            scheduler.schedule(resetRunnable, RESET_INTERVAL, TimeUnit.SECONDS);
            return null;
        }

        return null;
    }

    protected Runnable configureRunnable = new Runnable() {

        @Override
        public void run() {
            logger.debug("Fetching the configuration of the Helios IP Vario '{}' ", getThing().getUID().toString());

            Response response = null;
            try {
                response = systemTarget.resolveTemplate("ip", ipAddress).resolveTemplate("cmd", INFO)
                        .request(MediaType.APPLICATION_JSON_TYPE).get();
            } catch (NullPointerException e) {
                logger.error("An exception occurred while fetching system info of the Helios IP Vario '{}'",
                        getThing().getUID().toString(), e);
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR);
                scheduler.schedule(resetRunnable, RESET_INTERVAL, TimeUnit.SECONDS);
                return;
            }

            if (response != null) {
                JsonObject jsonObject = parser.parse(response.readEntity(String.class)).getAsJsonObject();

                RESTSystemInfo systemInfo = gson.fromJson(jsonObject.get("result").toString(), RESTSystemInfo.class);

                Map<String, String> properties = editProperties();
                properties.put(VARIANT, systemInfo.variant);
                properties.put(SERIAL_NUMBER, systemInfo.serialNumber);
                properties.put(HW_VERSION, systemInfo.hwVersion);
                properties.put(SW_VERSION, systemInfo.swVersion);
                properties.put(BUILD_TYPE, systemInfo.buildType);
                properties.put(DEVICE_NAME, systemInfo.deviceName);
                updateProperties(properties);
            }

            List<RESTSwitch> switches = getSwitches();

            if (switches != null) {
                for (RESTSwitch aSwitch : switches) {
                    if (aSwitch.enabled.equals("true")) {
                        logger.debug("Adding a channel to the Helios IP Vario '{}' for the switch with id '{}'",
                                getThing().getUID().toString(), aSwitch.id);
                        ThingBuilder thingBuilder = editThing();
                        ChannelTypeUID enablerUID = new ChannelTypeUID(BINDING_ID, SWITCH_ENABLER);
                        ChannelTypeUID triggerUID = new ChannelTypeUID(BINDING_ID, SWITCH_TRIGGER);

                        Channel channel = ChannelBuilder
                                .create(new ChannelUID(getThing().getUID(), "switch" + aSwitch.id + "active"), "Switch")
                                .withType(enablerUID).build();
                        thingBuilder.withChannel(channel);
                        channel = ChannelBuilder
                                .create(new ChannelUID(getThing().getUID(), "switch" + aSwitch.id), "Switch")
                                .withType(triggerUID).build();
                        thingBuilder.withChannel(channel);
                        updateThing(thingBuilder.build());
                    }
                }
            }

            List<RESTPort> ports = getPorts();

            if (ports != null) {
                for (RESTPort aPort : ports) {
                    logger.debug("Adding a channel to the Helios IP Vario '{}' for the IO port with id '{}'",
                            getThing().getUID().toString(), aPort.port);
                    ThingBuilder thingBuilder = editThing();
                    ChannelTypeUID triggerUID = new ChannelTypeUID(BINDING_ID, IO_TRIGGER);

                    Map<String, String> channelProperties = new HashMap<String, String>();
                    channelProperties.put("type", aPort.type);

                    Channel channel = ChannelBuilder
                            .create(new ChannelUID(getThing().getUID(), "io" + aPort.port), "Switch")
                            .withType(triggerUID).withProperties(channelProperties).build();
                    thingBuilder.withChannel(channel);
                    updateThing(thingBuilder.build());
                }
            }
        }
    };

    protected Runnable resetRunnable = new Runnable() {
        @Override
        public void run() {
            logger.debug("Resetting the Helios IP Vario handler for '{}'", getThing().getUID());
            dispose();
            initialize();
        }
    };

    protected Runnable logRunnable = new Runnable() {

        @Override
        public void run() {
            if (getThing().getStatus() == ThingStatus.ONLINE) {

                if (logSubscriptionID == 0) {
                    logSubscriptionID = subscribe();
                }

                SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

                while (logSubscriptionID != 0) {
                    List<RESTEvent> events = pullLog(logSubscriptionID);

                    for (RESTEvent event : events) {
                        Date date = new Date(Long.valueOf(event.utcTime));
                        DateTimeType stampType = new DateTimeType(dateFormatter.format(date));

                        logger.debug("Received the event for Helios IP Vario '{}' with ID '{}' of type '{}' on {}",
                                getThing().getUID().toString(), event.id, event.event, dateFormatter.format(date));

                        switch (event.event) {
                            case DEVICESTATE: {
                                StringType valueType = new StringType(event.params.get("state").getAsString());
                                updateState(new ChannelUID(getThing().getUID(), DEVICE_STATE), valueType);
                                updateState(new ChannelUID(getThing().getUID(), DEVICE_STATE_STAMP), stampType);
                                break;
                            }
                            case AUDIOLOOPTEST: {
                                if (event.params.get("result").getAsString().equals("passed")) {
                                    updateState(new ChannelUID(getThing().getUID(), AUDIO_LOOP_TEST), OnOffType.ON);
                                } else if (event.params.get("result").getAsString().equals("failed")) {
                                    updateState(new ChannelUID(getThing().getUID(), AUDIO_LOOP_TEST), OnOffType.OFF);
                                } else {
                                    updateState(new ChannelUID(getThing().getUID(), AUDIO_LOOP_TEST), UnDefType.UNDEF);
                                }

                                updateState(new ChannelUID(getThing().getUID(), AUDIO_LOOP_TEST_STAMP), stampType);
                                break;
                            }
                            case MOTIONDETECTED: {
                                if (event.params.get("state").getAsString().equals("in")) {
                                    updateState(new ChannelUID(getThing().getUID(), MOTION), OnOffType.ON);
                                } else if (event.params.get("state").getAsString().equals("out")) {
                                    updateState(new ChannelUID(getThing().getUID(), MOTION), OnOffType.OFF);
                                } else {
                                    updateState(new ChannelUID(getThing().getUID(), MOTION), UnDefType.UNDEF);
                                }

                                updateState(new ChannelUID(getThing().getUID(), MOTION_STAMP), stampType);
                                break;
                            }
                            case KEYPRESSED: {
                                StringType valueType = new StringType(event.params.get("key").getAsString());
                                updateState(new ChannelUID(getThing().getUID(), KEY_PRESSED), valueType);

                                updateState(new ChannelUID(getThing().getUID(), KEY_PRESSED_STAMP), stampType);
                                break;
                            }
                            case KEYRELEASED: {
                                StringType valueType = new StringType(event.params.get("key").getAsString());
                                updateState(new ChannelUID(getThing().getUID(), KEY_RELEASED), valueType);

                                updateState(new ChannelUID(getThing().getUID(), KEY_RELEASED_STAMP), stampType);
                                break;
                            }
                            case CODEENTERED: {
                                StringType valueType = new StringType(event.params.get("code").getAsString());
                                updateState(new ChannelUID(getThing().getUID(), CODE), valueType);

                                if (event.params.get("valid").getAsString().equals("true")) {
                                    updateState(new ChannelUID(getThing().getUID(), CODE_VALID), OnOffType.ON);
                                } else if (event.params.get("valid").getAsString().equals("false")) {
                                    updateState(new ChannelUID(getThing().getUID(), CODE_VALID), OnOffType.OFF);
                                } else {
                                    updateState(new ChannelUID(getThing().getUID(), CODE_VALID), UnDefType.UNDEF);
                                }

                                updateState(new ChannelUID(getThing().getUID(), CODE_STAMP), stampType);
                                break;
                            }
                            case CARDENTERED: {
                                StringType valueType = new StringType(event.params.get("uid").getAsString());
                                updateState(new ChannelUID(getThing().getUID(), CARD), valueType);

                                if (event.params.get("valid").getAsString().equals("true")) {
                                    updateState(new ChannelUID(getThing().getUID(), CARD_VALID), OnOffType.ON);
                                } else if (event.params.get("valid").getAsString().equals("false")) {
                                    updateState(new ChannelUID(getThing().getUID(), CARD_VALID), OnOffType.OFF);
                                } else {
                                    updateState(new ChannelUID(getThing().getUID(), CARD_VALID), UnDefType.UNDEF);
                                }

                                updateState(new ChannelUID(getThing().getUID(), CARD_STAMP), stampType);
                                break;
                            }
                            case INPUTCHANGED: {
                                ChannelUID inputChannel = new ChannelUID(getThing().getUID(),
                                        "io" + event.params.get("port").getAsString());

                                if (event.params.get("state").getAsString().equals("true")) {
                                    updateState(inputChannel, OnOffType.ON);
                                } else if (event.params.get("state").getAsString().equals("false")) {
                                    updateState(inputChannel, OnOffType.OFF);
                                } else {
                                    updateState(inputChannel, UnDefType.UNDEF);
                                }
                                break;
                            }
                            case OUTPUTCHANGED: {
                                ChannelUID inputChannel = new ChannelUID(getThing().getUID(),
                                        "io" + event.params.get("port").getAsString());

                                if (event.params.get("state").getAsString().equals("true")) {
                                    updateState(inputChannel, OnOffType.ON);
                                } else if (event.params.get("state").getAsString().equals("false")) {
                                    updateState(inputChannel, OnOffType.OFF);
                                } else {
                                    updateState(inputChannel, UnDefType.UNDEF);
                                }
                                break;
                            }
                            case CALLSTATECHANGED: {
                                StringType valueType = new StringType(event.params.get("state").getAsString());
                                updateState(new ChannelUID(getThing().getUID(), CALL_STATE), valueType);

                                valueType = new StringType(event.params.get("direction").getAsString());
                                updateState(new ChannelUID(getThing().getUID(), CALL_DIRECTION), valueType);

                                updateState(new ChannelUID(getThing().getUID(), CALL_STATE_STAMP), stampType);
                                break;
                            }
                            case REGISTRATIONSTATECHANGED: {
                                break;
                            }
                            default: {
                                logger.debug("Unrecognised event type : '{}'", event.event);
                            }
                        }
                    }
                }
            }
        }
    };

    protected class Authenticator implements ClientRequestFilter {

        private final String user;
        private final String password;

        public Authenticator(String user, String password) {
            this.user = user;
            this.password = password;
        }

        @Override
        public void filter(ClientRequestContext requestContext) throws IOException {
            MultivaluedMap<String, Object> headers = requestContext.getHeaders();
            final String basicAuthentication = getBasicAuthentication();
            headers.add("Authorization", basicAuthentication);
        }

        private String getBasicAuthentication() {
            String token = this.user + ":" + this.password;
            try {
                return "Basic " + DatatypeConverter.printBase64Binary(token.getBytes("UTF-8"));
            } catch (UnsupportedEncodingException ex) {
                throw new IllegalStateException("Cannot encode with UTF-8", ex);
            }
        }
    }

    public class SecureRestClientTrustManager implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

        public boolean isClientTrusted(X509Certificate[] arg0) {
            return true;
        }

        public boolean isServerTrusted(X509Certificate[] arg0) {
            return true;
        }
    }
}
