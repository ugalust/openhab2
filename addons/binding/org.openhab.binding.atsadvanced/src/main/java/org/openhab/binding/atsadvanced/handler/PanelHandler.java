/**
 * Copyright (c) 2014 openHAB UG (haftungsbeschraenkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.atsadvanced.handler;

import static org.openhab.binding.atsadvanced.ATSadvancedBindingConstants.MAX_NUMBER_AREAS;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebParam.Mode;
import javax.jws.WebResult;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.jws.soap.SOAPBinding.ParameterStyle;
import javax.jws.soap.SOAPBinding.Style;
import javax.jws.soap.SOAPBinding.Use;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;
import javax.xml.ws.Endpoint;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.LogOutputStream;
import org.apache.commons.exec.ProcessDestroyer;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.ShutdownHookProcessDestroyer;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.lang.StringUtils;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.binding.BaseBridgeHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.UnDefType;
import org.openhab.binding.atsadvanced.ATSadvancedBindingConstants;
import org.openhab.binding.atsadvanced.ATSadvancedBindingConstants.AreaStatusFlags;
import org.openhab.binding.atsadvanced.ATSadvancedBindingConstants.ControlSessionState;
import org.openhab.binding.atsadvanced.ATSadvancedBindingConstants.ZoneStatusFlags;
import org.openhab.binding.atsadvanced.internal.ATSAdvancedException;
import org.openhab.binding.atsadvanced.webservices.client.ISyncReply;
import org.openhab.binding.atsadvanced.webservices.client.ProgramConfigureGateway;
import org.openhab.binding.atsadvanced.webservices.client.ProgramConfigureGatewayResponse;
import org.openhab.binding.atsadvanced.webservices.client.ProgramConfigurePanel;
import org.openhab.binding.atsadvanced.webservices.client.ProgramConfigurePanelResponse;
import org.openhab.binding.atsadvanced.webservices.client.ProgramSendMessage;
import org.openhab.binding.atsadvanced.webservices.client.ProgramSendMessageResponse;
import org.openhab.binding.atsadvanced.webservices.client.SyncReply;
import org.openhab.binding.atsadvanced.webservices.datacontract.ArrayOfProgramProperty;
import org.openhab.binding.atsadvanced.webservices.datacontract.ProgramProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link PanelHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Karel Goderis - Initial contribution
 */
public class PanelHandler extends BaseBridgeHandler {

    // List of Configuration constants
    public static final String IP_ADDRESS = "ipAddress";
    public static final String OPENHAB_HOST = "openhab";
    public static final String PORT_NUMBER = "portNumber";
    public static final String GATEWAY_PORT = "gatewayPortNumber";
    public static final String EVENT_LOGGER_PORT = "eventLoggerPortNumber";
    // public static final String GATEWAY_URL = "gatewayURL";
    // public static final String LOGGER_URL = "loggerURL";
    public static final String PIN = "pin";

    private Logger logger = LoggerFactory.getLogger(PanelHandler.class);

    private static final QName SERVICE_NAME = new QName("ATSAdvanced.DTO", "SyncReply");

    private static int CONNECTION_THREAD_INTERVAL = 5;
    private static int POLLING_THREAD_INTERVAL = 300;
    private static int HEART_BEAT = 1000;
    private static int RETRIES = 1;
    private static int TIME_OUT = 2000;

    private ScheduledFuture<?> connectionJob;
    private ScheduledFuture<?> pollingJob;
    private SyncReply syncReply;
    private Endpoint endPoint = null;
    private InetAddress localhost;
    private String lastError = "";
    private String loggerURL;
    private String gatewayURL;
    private String monoPath;
    private String atsPath;
    private boolean gatewayProcessStarted = false;
    private boolean panelConnected = false;
    private boolean gatewayConfigured = false;
    private boolean userLoggedIn = false;
    private boolean monitorStarted = false;
    private boolean logsOpened = false;
    private boolean webServicesSetUp = false;

    private final ProcessDestroyer shutdownHookProcessDestroyer = new LoggingShutdownHookProcessDestroyer();
    private final DefaultExecuteResultHandler resultHandler = new GatewayExecuteResultHandler();
    private final ExecuteWatchdog watchDog = new ExecuteWatchdog(ExecuteWatchdog.INFINITE_TIMEOUT);

    private List<PanelStatusListener> panelStatusListeners = new CopyOnWriteArrayList<>();

    public PanelHandler(Bridge bridge, String monoPath, String atsPath) {
        super(bridge);
        this.monoPath = monoPath;
        this.atsPath = atsPath;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // nothing to do here
    }

    @Override
    public void initialize() {
        logger.debug("Initializing ATS Advanced Panel handler.");
        super.initialize();

        onUpdate();

        updateStatus(ThingStatus.OFFLINE);
    }

    @Override
    public void dispose() {
        logger.debug("Disposing ATS Advanced Panel handler.");

        if (connectionJob != null && !connectionJob.isCancelled()) {
            connectionJob.cancel(true);
            connectionJob = null;
        }

        if (pollingJob != null && !pollingJob.isCancelled()) {
            pollingJob.cancel(true);
            pollingJob = null;
        }

        logout();

        stopGatewayProcess();

        super.dispose();
    }

    public boolean registerStatusListener(PanelStatusListener panelStatusListener) {
        if (panelStatusListener == null) {
            throw new NullPointerException("It's not allowed to pass a null PanelStatusListener.");
        }
        return panelStatusListeners.add(panelStatusListener);
    }

    public boolean unregisterStatusListener(PanelStatusListener panelStatusListener) {
        if (panelStatusListener == null) {
            throw new NullPointerException("It's not allowed to pass a null PanelStatusListener.");
        }
        return panelStatusListeners.remove(panelStatusListener);
    }

    private void onUpdate() {
        if (connectionJob == null || connectionJob.isCancelled()) {
            connectionJob = scheduler.scheduleWithFixedDelay(connectionRunnable, 0, CONNECTION_THREAD_INTERVAL,
                    TimeUnit.SECONDS);
        }

        if (pollingJob == null || pollingJob.isCancelled()) {
            pollingJob = scheduler.scheduleWithFixedDelay(pollingRunnable, POLLING_THREAD_INTERVAL,
                    POLLING_THREAD_INTERVAL, TimeUnit.SECONDS);
        }
    }

    public boolean isConnected() {
        return panelConnected;
    }

    public boolean isGatewayConfigured() {
        return gatewayConfigured;
    }

    public boolean isLoggedIn() {
        return userLoggedIn;
    }

    public boolean isMonitorStarted() {
        return monitorStarted;
    }

    public boolean isLogsOpened() {
        return logsOpened;
    }

    public boolean isWebServicesSetUp() {
        return webServicesSetUp;
    }

    public boolean isGatewayStarted() {
        return gatewayProcessStarted;
    }

    public String getLastError() {
        return lastError;
    }

    private Runnable pollingRunnable = new Runnable() {

        @Override
        public void run() {
            try {
                logger.debug("Polling the ATS Advanced Panel");
                if (getThing().getStatus() == ThingStatus.ONLINE) {
                    if (isGatewayStarted() && isWebServicesSetUp() && isGatewayConfigured() && isConnected()
                            && isLoggedIn() && isMonitorStarted() && isLogsOpened()) {
                        updateChangedZonesStatus();
                        updateChangedAreasStatus();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                logger.warn("An exception occurred while polling the ATS Advanced Panel: '{}'", e.getMessage());
            }

        }

    };

    private Runnable connectionRunnable = new Runnable() {

        @Override
        public void run() {
            try {

                if (getThing().getStatus() == ThingStatus.OFFLINE) {

                    if (!isGatewayStarted()) {
                        startGatewayProcess();
                    }

                    if (isGatewayStarted() && !isWebServicesSetUp()) {
                        setupWebServices();
                    }

                    if (!isConnected() && isWebServicesSetUp()) {
                        // instruct the gateway to connect to the ATS panel
                        establishConnection();
                    }

                    // configure the gateway so that it can find our log/monitor web service
                    if (!isGatewayConfigured() && isConnected()) {
                        configureGateway();
                    }

                    if (!isLoggedIn() && isConnected()) {
                        // login into the ATS panel with the PIN of a user that is authorised to monitor and control the
                        // panel
                        login();
                        if (!isLoggedIn()) {
                            logout();
                            onConnectionLost();
                        }
                    }

                    if (!isMonitorStarted() && isLoggedIn()) {
                        startMonitor();
                    }

                    if (!isLogsOpened() && isLoggedIn()) {
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                            logger.debug(
                                    "An exception occurred while putting the ATS Advanced execute thread to sleep");
                        }
                        openLogs();
                    }

                    if (isWebServicesSetUp() && isGatewayConfigured() && isConnected() && isLoggedIn()
                            && isMonitorStarted() && isLogsOpened()) {
                        onConnectionResumed();
                    }
                }
            } catch (Exception e) {
                logger.warn("An exception occurred while setting up the ATS Advanced Panel: '{}'", e.getMessage());
                e.printStackTrace();
                handleError(e.getMessage());
            }
        }
    };

    public void onConnectionLost() {

        if (endPoint != null) {
            endPoint.stop();
            endPoint = null;
        }

        // let's start over again
        webServicesSetUp = false;
        panelConnected = false;
        gatewayConfigured = false;
        userLoggedIn = false;
        monitorStarted = false;
        logsOpened = false;

        for (PanelStatusListener listener : panelStatusListeners) {
            listener.onBridgeDisconnected(this);
        }
        updateStatus(ThingStatus.OFFLINE);
    }

    public void onConnectionResumed() {
        updateStatus(ThingStatus.ONLINE);
        for (PanelStatusListener listener : panelStatusListeners) {
            listener.onBridgeConnected(this);
        }
    }

    private void startGatewayProcess() {

        if (!gatewayProcessStarted) {

            gatewayURL = "http://" + (String) getConfig().get(OPENHAB_HOST) + ":" + getConfig().get(GATEWAY_PORT)
                    + "/soap11";
            loggerURL = "http://" + (String) getConfig().get(OPENHAB_HOST) + ":" + getConfig().get(EVENT_LOGGER_PORT)
                    + "/eventlogger";

            logger.debug("The ATS Advanced Panel handler will contact the gateway via '{}'", gatewayURL);
            logger.debug("The ATS Advanced Panel gateway will contact the handler via '{}'", loggerURL);

            DefaultExecutor executor = new DefaultExecutor();
            executor.setExitValue(0);

            PumpStreamHandler psh = new PumpStreamHandler(new GatewayLogHangler(logger, 0),
                    new GatewayLogHangler(logger, 1));
            // PumpStreamHandler psh = new PumpStreamHandler(new GatewayLogHangler(logger, 0));
            executor.setStreamHandler(psh);
            executor.setProcessDestroyer(shutdownHookProcessDestroyer);
            executor.setExitValue(0);

            File file = new File(atsPath);
            executor.setWorkingDirectory(file);

            CommandLine commandLine = new CommandLine(monoPath);
            commandLine.addArgument("--debug");
            commandLine.addArgument("ATSAdvancedGateway.exe");
            commandLine.addArgument("-p");
            commandLine.addArgument(((BigDecimal) getConfig().get(GATEWAY_PORT)).toString());
            commandLine.addArgument("-d");
            commandLine.addArgument("ats.advanced.drv");
            Map<String, String> environment = new HashMap<String, String>();

            checkAndKillExistingProcessRunning(commandLine);

            try {
                logger.debug("Starting the ATS Advanced Panel gateway process : '{}'", commandLine.toString());
                executor.execute(commandLine, environment, resultHandler);
            } catch (IOException e) {
                logger.error("An exception occured while starting the gateway process : '{}'", e.getMessage());
            }
            gatewayProcessStarted = true;
        }
    }

    private void checkAndKillExistingProcessRunning(CommandLine commandline) {
        try {
            String line;
            DefaultExecutor executor = new DefaultExecutor();
            CommandLine psCmd = new CommandLine("ps");
            psCmd.addArgument("-A");

            ByteArrayOutputStream err = new ByteArrayOutputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PumpStreamHandler p = new PumpStreamHandler(out, err);
            executor.setStreamHandler(p);

            executor.execute(psCmd);

            BufferedReader input = new BufferedReader(
                    new InputStreamReader(new ByteArrayInputStream(out.toByteArray())));

            while ((line = input.readLine()) != null) {
                if (line.contains(commandline.toString())) {

                    String[] entry = StringUtils.split(line);

                    CommandLine commandLine = new CommandLine("/bin/kill");
                    commandLine.addArgument("-9");
                    commandLine.addArgument(entry[0]);
                    int result = executor.execute(commandLine);
                    if (result == 0) {
                        logger.info("Killed a running ATS Advanced Panel gateway process");
                    } else {
                        logger.warn("Killing a running ATS Advanced Panel gateway process exited with code : '{}'",
                                result);
                    }
                }
            }
            input.close();

        } catch (Exception err) {
            logger.warn(
                    "An exception occurred while checking and killing existing ATS Advanced Panel gateway processes: '{}'",
                    err.getMessage());
        }

    }

    private void stopGatewayProcess() {
        if (!gatewayProcessStarted) {
            logger.error("The gateway process was already stopped (or never started)");
        }

        logger.debug("Destroying the gateway process");
        watchDog.destroyProcess();

        // try {
        // // Safer to waitFor() after destroy()
        // resultHandler.waitFor();
        // } catch (InterruptedException e) {
        // logger.error("The gateway process was interrupted. This should not occur");
        // }

        gatewayProcessStarted = false;
    }

    private void setupWebServices() {

        // initialise the stub to access the webservices exposed by the gateway
        try {

            // syncReply = new SyncReply(new URL((String) getConfig().get(GATEWAY_URL)), SERVICE_NAME);
            syncReply = new SyncReply(new URL(gatewayURL), SERVICE_NAME);
            if (syncReply != null) {
                webServicesSetUp = true;
            } else {
                webServicesSetUp = false;
            }
        } catch (Exception e1) {
            logger.error("An exception occurred while setting up the gateway web service: {}", e1.getMessage(), e1);
            e1.printStackTrace();
            webServicesSetUp = false;
        }
    }

    private void configureGateway() {

        if (isWebServicesSetUp()) {

            String finalLoggerURL = loggerURL + System.currentTimeMillis();

            // set up the web service to receive monitor and logger events from the panel
            if (endPoint == null || !endPoint.isPublished()) {
                try {
                    endPoint = Endpoint.publish(finalLoggerURL, new EventLogger());
                } catch (Exception e) {
                    logger.error("An exception occurred while publishing the web services endpoint: {}",
                            e.getMessage());
                    gatewayConfigured = false;
                    return;
                }
            }

            ISyncReply port = syncReply.getBasicHttpBindingISyncReply();

            logger.debug("The gateway will be configured to log to {}", finalLoggerURL);
            ProgramConfigureGateway programConfigureGateway = new ProgramConfigureGateway();
            programConfigureGateway.setOpenHABURL(finalLoggerURL);
            ProgramConfigureGatewayResponse _programConfigureGateway__return = port
                    .programConfigureGateway(programConfigureGateway);
            logger.debug("The gateway returned: {}", _programConfigureGateway__return.getResult());
            if (!_programConfigureGateway__return.getResult().equals("1")) {
                handleError(_programConfigureGateway__return.getResult());
            } else {
                gatewayConfigured = true;
            }
        }
    }

    private void establishConnection() throws Exception {

        if (isWebServicesSetUp()) {

            try {
                ISyncReply port = syncReply.getBasicHttpBindingISyncReply();

                logger.debug("The gateway will connect to the panel at {}", getConfig().get(IP_ADDRESS));
                ProgramConfigurePanel programConfigurePanel = new ProgramConfigurePanel();
                programConfigurePanel.setHearbeat(HEART_BEAT);
                programConfigurePanel.setHostaddress((String) getConfig().get(IP_ADDRESS));
                programConfigurePanel.setPassword("0000");
                programConfigurePanel.setPort(((BigDecimal) getConfig().get(PORT_NUMBER)).intValue());
                programConfigurePanel.setRetries(RETRIES);
                programConfigurePanel.setTimeout(TIME_OUT);
                ProgramConfigurePanelResponse _programConfigurePanel__return = port
                        .programConfigurePanel(programConfigurePanel);
                if (_programConfigurePanel__return != null) {
                    if (_programConfigurePanel__return.getResult() != null) {
                        logger.debug("The gateway returned: {}", _programConfigurePanel__return.getResult());
                        if (!_programConfigurePanel__return.getResult().equals("1")) {
                            handleError(_programConfigurePanel__return.getResult());
                        } else {
                            panelConnected = true;
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("An exception occurred while establishing a connection to the ATS Advanced Panel: '{}'",
                        e.getMessage());
                panelConnected = false;
            }
        }

    }

    private void login() {

        if (isWebServicesSetUp() && isConnected()) {

            ISyncReply port = syncReply.getBasicHttpBindingISyncReply();

            logger.debug("The gateway will log into the panel with PIN: {}", getConfig().get(PIN));
            ProgramSendMessage programSendMessage = new ProgramSendMessage();
            ArrayOfProgramProperty programSendMessageProperties = new ArrayOfProgramProperty();
            List<ProgramProperty> programSendMessagePropertiesList = new ArrayList<ProgramProperty>();

            ProgramProperty ProgramProperty1 = new ProgramProperty();
            ProgramProperty1.setId("userPIN");
            ProgramProperty1.setIndex(0);
            java.lang.Object ProgramProperty1Value = ((BigDecimal) getConfig().get(PIN)).toString();
            ProgramProperty1.setValue(ProgramProperty1Value);
            programSendMessagePropertiesList.add(ProgramProperty1);

            ProgramProperty ProgramProperty2 = new ProgramProperty();
            ProgramProperty2.setId("userAction_LOGREAD");
            ProgramProperty2.setIndex(0);
            java.lang.Object ProgramProperty2Value = "1";
            ProgramProperty2.setValue(ProgramProperty2Value);
            programSendMessagePropertiesList.add(ProgramProperty2);

            ProgramProperty ProgramProperty3 = new ProgramProperty();
            ProgramProperty3.setId("userAction_CTRL");
            ProgramProperty3.setIndex(0);
            java.lang.Object ProgramProperty3Value = "1";
            ProgramProperty3.setValue(ProgramProperty3Value);
            programSendMessagePropertiesList.add(ProgramProperty3);

            ProgramProperty ProgramProperty4 = new ProgramProperty();
            ProgramProperty4.setId("userAction_MONITOR");
            ProgramProperty4.setIndex(0);
            java.lang.Object ProgramProperty4Value = "1";
            ProgramProperty4.setValue(ProgramProperty4Value);
            programSendMessagePropertiesList.add(ProgramProperty4);

            ProgramProperty ProgramProperty5 = new ProgramProperty();
            ProgramProperty5.setId("userAction_DIAG");
            ProgramProperty5.setIndex(0);
            java.lang.Object ProgramProperty5Value = "1";
            ProgramProperty5.setValue(ProgramProperty5Value);
            programSendMessagePropertiesList.add(ProgramProperty5);

            ProgramProperty ProgramProperty6 = new ProgramProperty();
            ProgramProperty6.setId("userAction_UPLOAD");
            ProgramProperty6.setIndex(0);
            java.lang.Object ProgramProperty6Value = "0";
            ProgramProperty6.setValue(ProgramProperty6Value);
            programSendMessagePropertiesList.add(ProgramProperty6);

            ProgramProperty ProgramProperty7 = new ProgramProperty();
            ProgramProperty7.setId("userAction_DOWNLOAD");
            ProgramProperty7.setIndex(0);
            java.lang.Object ProgramProperty7Value = "0";
            ProgramProperty7.setValue(ProgramProperty7Value);
            programSendMessagePropertiesList.add(ProgramProperty7);

            programSendMessageProperties.getProgramProperty().addAll(programSendMessagePropertiesList);
            programSendMessage.setProperties(programSendMessageProperties);
            programSendMessage.setName("device.getConnect");
            ProgramSendMessageResponse _programSendMessage__return = port.programSendMessage(programSendMessage);

            logger.debug("The gateway returned a message of type : {}", _programSendMessage__return.getName());

            for (ProgramProperty property : _programSendMessage__return.getProperties().getProgramProperty()) {
                logger.debug("\t Property {} : {} : {}",
                        new Object[] { property.getId(), property.getIndex(), property.getValue() });
            }

            // request get.UserInfo to check if login is successful
            if (_programSendMessage__return.getName().equals("return.error")) {
                handleError(
                        (String) _programSendMessage__return.getProperties().getProgramProperty().get(0).getValue());
            } else {
                userLoggedIn = true;
            }
        }
    }

    private void logout() {
        if (isWebServicesSetUp()) {

            ISyncReply port = syncReply.getBasicHttpBindingISyncReply();

            logger.debug("The gateway will logout");
            ProgramSendMessage programSendMessage = new ProgramSendMessage();
            ArrayOfProgramProperty programSendMessageProperties = new ArrayOfProgramProperty();
            List<ProgramProperty> programSendMessagePropertiesList = new ArrayList<ProgramProperty>();

            programSendMessageProperties.getProgramProperty().addAll(programSendMessagePropertiesList);
            programSendMessage.setProperties(programSendMessageProperties);
            programSendMessage.setName("device.disconnect");
            ProgramSendMessageResponse _programSendMessage__return = port.programSendMessage(programSendMessage);

            logger.debug("The gateway returned a message of type : {}", _programSendMessage__return.getName());

            for (ProgramProperty property : _programSendMessage__return.getProperties().getProgramProperty()) {
                logger.debug("\t Property {} : {} : {}",
                        new Object[] { property.getId(), property.getIndex(), property.getValue() });
            }

            if (_programSendMessage__return.getName().equals("return.error")) {
                String error = (String) _programSendMessage__return.getProperties().getProgramProperty().get(0)
                        .getValue();
                if (StringUtils.contains(error, "FAULT_NO_ACCESS")
                        || StringUtils.contains(error, "Object reference not set to an instance of an object")) {
                    userLoggedIn = false;
                    panelConnected = false;
                    gatewayConfigured = false;
                }
                handleError(error);
            } else {
                panelConnected = false;
                userLoggedIn = false;
                gatewayConfigured = false;
            }
        }
    }

    private void openLogs() {

        if (isWebServicesSetUp() && isConnected() && isLoggedIn()) {

            ISyncReply port = syncReply.getBasicHttpBindingISyncReply();

            logger.debug("The gateway will open the logs");
            ProgramSendMessage programSendMessage = new ProgramSendMessage();
            ArrayOfProgramProperty programSendMessageProperties = new ArrayOfProgramProperty();
            List<ProgramProperty> programSendMessagePropertiesList = new ArrayList<ProgramProperty>();

            programSendMessageProperties.getProgramProperty().addAll(programSendMessagePropertiesList);
            programSendMessage.setProperties(programSendMessageProperties);
            programSendMessage.setName("open.LOG");
            ProgramSendMessageResponse _programSendMessage__return = port.programSendMessage(programSendMessage);

            logger.debug("The gateway returned a message of type : {}", _programSendMessage__return.getName());

            for (ProgramProperty property : _programSendMessage__return.getProperties().getProgramProperty()) {
                logger.debug("\t Property {} : {} : {}",
                        new Object[] { property.getId(), property.getIndex(), property.getValue() });
            }

            if (_programSendMessage__return.getName().equals("return.error")) {
                handleError(
                        (String) _programSendMessage__return.getProperties().getProgramProperty().get(0).getValue());
            } else {
                logsOpened = true;
            }
        }
    }

    private void startMonitor() {

        if (isWebServicesSetUp() && isConnected() && isLoggedIn()) {

            ISyncReply port = syncReply.getBasicHttpBindingISyncReply();

            logger.debug("The gateway will start the monitor");
            ProgramSendMessage programSendMessage = new ProgramSendMessage();
            ArrayOfProgramProperty programSendMessageProperties = new ArrayOfProgramProperty();
            List<ProgramProperty> programSendMessagePropertiesList = new ArrayList<ProgramProperty>();

            programSendMessageProperties.getProgramProperty().addAll(programSendMessagePropertiesList);
            programSendMessage.setProperties(programSendMessageProperties);
            programSendMessage.setName("start.MONITOR");
            ProgramSendMessageResponse _programSendMessage__return = port.programSendMessage(programSendMessage);

            logger.debug("The gateway returned a message of type : {}", _programSendMessage__return.getName());

            for (ProgramProperty property : _programSendMessage__return.getProperties().getProgramProperty()) {
                logger.debug("\t Property {} : {} : {}",
                        new Object[] { property.getId(), property.getIndex(), property.getValue() });
            }

            if (_programSendMessage__return.getName().equals("return.bool")) {
                monitorStarted = true;
                // return((boolean) Boolean.parseBoolean(Long.toString((long)
                // _programSendMessage__return.getProperties().getProgramProperty().get(0).getValue())));
            } else {
                handleError(
                        (String) _programSendMessage__return.getProperties().getProgramProperty().get(0).getValue());
            }
        }
    }

    public boolean isAlive() {

        if (isWebServicesSetUp() && isConnected() && isLoggedIn()) {

            ISyncReply port = syncReply.getBasicHttpBindingISyncReply();

            logger.debug("The gateway will check if the panel is alive");
            ProgramSendMessage programSendMessage = new ProgramSendMessage();
            ArrayOfProgramProperty programSendMessageProperties = new ArrayOfProgramProperty();
            List<ProgramProperty> programSendMessagePropertiesList = new ArrayList<ProgramProperty>();

            programSendMessageProperties.getProgramProperty().addAll(programSendMessagePropertiesList);
            programSendMessage.setProperties(programSendMessageProperties);
            programSendMessage.setName("is.Alive");
            ProgramSendMessageResponse _programSendMessage__return = port.programSendMessage(programSendMessage);

            logger.debug("The gateway returned a message of type : {}", _programSendMessage__return.getName());

            for (ProgramProperty property : _programSendMessage__return.getProperties().getProgramProperty()) {
                logger.debug("\t Property {} : {} : {}",
                        new Object[] { property.getId(), property.getIndex(), property.getValue() });
            }

            if (_programSendMessage__return.getName().equals("return.void")) {
                return true;
            } else {
                handleError(
                        (String) _programSendMessage__return.getProperties().getProgramProperty().get(0).getValue());
            }
        }

        return false;

    }

    public void getUserPrivileges() {
        for (int i = 0; i < 9; i++) {
            getUserPrivilege(i);
        }
    }

    public boolean getUserPrivilege(int index) {
        if (isWebServicesSetUp() && isConnected() && isLoggedIn()) {

            ISyncReply port = syncReply.getBasicHttpBindingISyncReply();

            logger.debug("The gateway will verify the user privileges with index: {}", index);
            ProgramSendMessage programSendMessage = new ProgramSendMessage();
            ArrayOfProgramProperty programSendMessageProperties = new ArrayOfProgramProperty();
            List<ProgramProperty> programSendMessagePropertiesList = new ArrayList<ProgramProperty>();

            ProgramProperty ProgramProperty1 = new ProgramProperty();
            ProgramProperty1.setId("areaID");
            ProgramProperty1.setIndex(0);
            java.lang.Object ProgramProperty1Value = index;
            ProgramProperty1.setValue(ProgramProperty1Value);
            programSendMessagePropertiesList.add(ProgramProperty1);

            programSendMessageProperties.getProgramProperty().addAll(programSendMessagePropertiesList);
            programSendMessage.setProperties(programSendMessageProperties);
            programSendMessage.setName("get.privileges");
            ProgramSendMessageResponse _programSendMessage__return = port.programSendMessage(programSendMessage);

            logger.debug("The gateway returned a message of type : {}", _programSendMessage__return.getName());

            for (ProgramProperty property : _programSendMessage__return.getProperties().getProgramProperty()) {
                logger.debug("\t Property {} : {} : {}",
                        new Object[] { property.getId(), property.getIndex(), property.getValue() });
            }

            if (_programSendMessage__return.getName().equals("return.error")) {
                handleError(
                        (String) _programSendMessage__return.getProperties().getProgramProperty().get(0).getValue());
            } else {
                return true;
            }
        }

        return false;
    }

    private void updateChangedZonesStatus() {
        BitSet changedSet = getChangedZones();
        if (changedSet != null) {
            for (int i = 0; i < changedSet.length(); i++) {
                if (changedSet.get(i)) {
                    Collection<Thing> allThings = thingRegistry.getAll();
                    for (Thing aThing : allThings) {
                        if (aThing.getThingTypeUID().equals(ATSadvancedBindingConstants.THING_TYPE_ZONE)) {
                            if (((BigDecimal) aThing.getConfiguration().get(ZoneHandler.NUMBER)).intValue() == i + 1) {
                                ((ZoneHandler) aThing.getHandler()).onChangedStatus();
                            }
                        }
                    }
                }
            }
        }
    }

    private void updateChangedAreasStatus() {
        BitSet changedSet = getChangedAreas();
        if (changedSet != null) {
            for (int i = 0; i < changedSet.length(); i++) {
                if (changedSet.get(i)) {
                    Collection<Thing> allThings = thingRegistry.getAll();
                    for (Thing aThing : allThings) {
                        if (aThing.getThingTypeUID().equals(ATSadvancedBindingConstants.THING_TYPE_AREA)) {
                            if (((BigDecimal) aThing.getConfiguration().get(AreaHandler.NUMBER)).intValue() == i + 1) {
                                ((AreaHandler) aThing.getHandler()).onChangedStatus();
                            }
                        }
                    }
                }
            }
        }
    }

    public ArrayList<ZoneStatusFlags> getZoneStatus(int index) {

        if (isWebServicesSetUp() && isConnected() && isLoggedIn()) {

            ISyncReply port = syncReply.getBasicHttpBindingISyncReply();

            ProgramSendMessage programSendMessage = new ProgramSendMessage();
            ArrayOfProgramProperty programSendMessageProperties = new ArrayOfProgramProperty();
            List<ProgramProperty> programSendMessagePropertiesList = new ArrayList<ProgramProperty>();

            ProgramProperty ProgramProperty1 = new ProgramProperty();
            ProgramProperty1.setId("objectID");
            ProgramProperty1.setIndex(0);
            java.lang.Object ProgramProperty1Value = index;
            ProgramProperty1.setValue(ProgramProperty1Value);
            programSendMessagePropertiesList.add(ProgramProperty1);

            programSendMessageProperties.getProgramProperty().addAll(programSendMessagePropertiesList);
            programSendMessage.setProperties(programSendMessageProperties);
            programSendMessage.setName("getSTAT.ZONE");
            ProgramSendMessageResponse _programSendMessage__return = port.programSendMessage(programSendMessage);

            ArrayList<ZoneStatusFlags> status = new ArrayList<ZoneStatusFlags>();

            if (_programSendMessage__return.getName().equals("returnSTAT.ZONE")) {
                for (ProgramProperty property : _programSendMessage__return.getProperties().getProgramProperty()) {
                    if (!property.getId().equals("objectID")) {
                        if ((boolean) property.getValue()) {
                            status.add(ZoneStatusFlags.valueOf(property.getId()));
                        }
                    }
                }
            }

            if (_programSendMessage__return.getName().equals("return.error")) {
                handleError(
                        (String) _programSendMessage__return.getProperties().getProgramProperty().get(0).getValue());
                return null;
            } else {
                return status;
            }
        }

        return null;

    }

    public ArrayList<AreaStatusFlags> getAreaStatus(int index) {

        if (isWebServicesSetUp() && isConnected() && isLoggedIn()) {

            ISyncReply port = syncReply.getBasicHttpBindingISyncReply();

            ProgramSendMessage programSendMessage = new ProgramSendMessage();
            ArrayOfProgramProperty programSendMessageProperties = new ArrayOfProgramProperty();
            List<ProgramProperty> programSendMessagePropertiesList = new ArrayList<ProgramProperty>();

            ProgramProperty ProgramProperty1 = new ProgramProperty();
            ProgramProperty1.setId("objectID");
            ProgramProperty1.setIndex(0);
            java.lang.Object ProgramProperty1Value = index;
            ProgramProperty1.setValue(ProgramProperty1Value);
            programSendMessagePropertiesList.add(ProgramProperty1);

            programSendMessageProperties.getProgramProperty().addAll(programSendMessagePropertiesList);
            programSendMessage.setProperties(programSendMessageProperties);
            programSendMessage.setName("getSTAT.AREA");
            ProgramSendMessageResponse _programSendMessage__return = port.programSendMessage(programSendMessage);

            ArrayList<AreaStatusFlags> status = new ArrayList<AreaStatusFlags>();

            for (ProgramProperty property : _programSendMessage__return.getProperties().getProgramProperty()) {
                if (!property.getId().equals("objectID")) {
                    if ((boolean) property.getValue()) {
                        status.add(AreaStatusFlags.valueOf(property.getId()));
                    }
                }
            }

            if (_programSendMessage__return.getName().equals("return.error")) {
                handleError(
                        (String) _programSendMessage__return.getProperties().getProgramProperty().get(0).getValue());
                return null;
            } else {
                return status;
            }
        }

        return null;
    }

    public ProgramSendMessageResponse getZoneNamesChunk(int index) {

        if (isWebServicesSetUp() && isConnected() && isLoggedIn()) {

            ISyncReply port = syncReply.getBasicHttpBindingISyncReply();

            ProgramSendMessage programSendMessage = new ProgramSendMessage();
            ArrayOfProgramProperty programSendMessageProperties = new ArrayOfProgramProperty();
            List<ProgramProperty> programSendMessagePropertiesList = new ArrayList<ProgramProperty>();

            ProgramProperty ProgramProperty1 = new ProgramProperty();
            ProgramProperty1.setId("index");
            ProgramProperty1.setIndex(0);
            java.lang.Object ProgramProperty1Value = index;
            ProgramProperty1.setValue(ProgramProperty1Value);
            programSendMessagePropertiesList.add(ProgramProperty1);

            programSendMessageProperties.getProgramProperty().addAll(programSendMessagePropertiesList);
            programSendMessage.setProperties(programSendMessageProperties);
            programSendMessage.setName("select.ZoneNames");
            ProgramSendMessageResponse _programSendMessage__return = port.programSendMessage(programSendMessage);

            if (_programSendMessage__return.getName().equals("return.ZoneNames")) {
                return _programSendMessage__return;
            } else {
                handleError(
                        (String) _programSendMessage__return.getProperties().getProgramProperty().get(0).getValue());
            }
        }
        return null;

    }

    public ProgramSendMessageResponse getAreaNamesChunk(int index) {

        if (isWebServicesSetUp() && isConnected() && isLoggedIn()) {

            ISyncReply port = syncReply.getBasicHttpBindingISyncReply();

            ProgramSendMessage programSendMessage = new ProgramSendMessage();
            ArrayOfProgramProperty programSendMessageProperties = new ArrayOfProgramProperty();
            List<ProgramProperty> programSendMessagePropertiesList = new ArrayList<ProgramProperty>();

            ProgramProperty ProgramProperty1 = new ProgramProperty();
            ProgramProperty1.setId("index");
            ProgramProperty1.setIndex(0);
            java.lang.Object ProgramProperty1Value = index;
            ProgramProperty1.setValue(ProgramProperty1Value);
            programSendMessagePropertiesList.add(ProgramProperty1);

            programSendMessageProperties.getProgramProperty().addAll(programSendMessagePropertiesList);
            programSendMessage.setProperties(programSendMessageProperties);
            programSendMessage.setName("select.AreaNames");
            ProgramSendMessageResponse _programSendMessage__return = port.programSendMessage(programSendMessage);

            if (_programSendMessage__return.getName().equals("return.AreaNames")) {
                return _programSendMessage__return;
            } else {
                handleError(
                        (String) _programSendMessage__return.getProperties().getProgramProperty().get(0).getValue());
            }
        }
        return null;
    }

    private BitSet getChangedAreas() {

        if (isWebServicesSetUp()) {

            ISyncReply port = syncReply.getBasicHttpBindingISyncReply();

            ProgramSendMessage programSendMessage = new ProgramSendMessage();
            ArrayOfProgramProperty programSendMessageProperties = new ArrayOfProgramProperty();
            List<ProgramProperty> programSendMessagePropertiesList = new ArrayList<ProgramProperty>();

            programSendMessageProperties.getProgramProperty().addAll(programSendMessagePropertiesList);
            programSendMessage.setProperties(programSendMessageProperties);
            programSendMessage.setName("getCOS.AREA");
            ProgramSendMessageResponse _programSendMessage__return = port.programSendMessage(programSendMessage);

            BitSet bitSet = null;

            for (ProgramProperty property : _programSendMessage__return.getProperties().getProgramProperty()) {
                if (property.getId().equals("bitSet")) {
                    bitSet = BitSet.valueOf((byte[]) property.getValue());
                }
            }

            if (_programSendMessage__return.getName().equals("returnCOS.AREA")) {
                return bitSet;
            } else if (_programSendMessage__return.getName().equals("return.void")) {
                return null;
            } else {
                handleError(
                        (String) _programSendMessage__return.getProperties().getProgramProperty().get(0).getValue());
            }
        }
        return null;

    }

    private BitSet getChangedZones() {

        if (isWebServicesSetUp()) {

            ISyncReply port = syncReply.getBasicHttpBindingISyncReply();

            ProgramSendMessage programSendMessage = new ProgramSendMessage();
            ArrayOfProgramProperty programSendMessageProperties = new ArrayOfProgramProperty();
            List<ProgramProperty> programSendMessagePropertiesList = new ArrayList<ProgramProperty>();

            programSendMessageProperties.getProgramProperty().addAll(programSendMessagePropertiesList);
            programSendMessage.setProperties(programSendMessageProperties);
            programSendMessage.setName("getCOS.ZONE");
            ProgramSendMessageResponse _programSendMessage__return = port.programSendMessage(programSendMessage);

            BitSet bitSet = null;

            for (ProgramProperty property : _programSendMessage__return.getProperties().getProgramProperty()) {
                if (property.getId().equals("bitSet")) {
                    bitSet = BitSet.valueOf((byte[]) property.getValue());
                }
            }

            if (_programSendMessage__return.getName().equals("returnCOS.ZONE")) {
                return bitSet;
            } else if (_programSendMessage__return.getName().equals("return.void")) {
                return null;
            } else {
                handleError(
                        (String) _programSendMessage__return.getProperties().getProgramProperty().get(0).getValue());
            }
        }
        return null;

    }

    private ProgramProperty createBooleanProperty(String id, boolean value) {
        ProgramProperty programProperty = new ProgramProperty();
        programProperty.setId(id);
        programProperty.setIndex(0);
        java.lang.Object ProgramProperty1Value = value;
        programProperty.setValue(ProgramProperty1Value);
        return programProperty;
    }

    private ProgramSendMessageResponse getLiveEvent(boolean next) {
        if (isWebServicesSetUp()) {

            ISyncReply port = syncReply.getBasicHttpBindingISyncReply();

            logger.debug("The gateway will fetch live events");
            ProgramSendMessage programSendMessage = new ProgramSendMessage();
            ArrayOfProgramProperty programSendMessageProperties = new ArrayOfProgramProperty();
            List<ProgramProperty> programSendMessagePropertiesList = new ArrayList<ProgramProperty>();

            programSendMessagePropertiesList.add(createBooleanProperty("area.1", true));
            programSendMessagePropertiesList.add(createBooleanProperty("area.2", true));
            programSendMessagePropertiesList.add(createBooleanProperty("area.3", true));
            programSendMessagePropertiesList.add(createBooleanProperty("area.4", true));
            programSendMessagePropertiesList.add(createBooleanProperty("area.5", true));
            programSendMessagePropertiesList.add(createBooleanProperty("area.6", true));
            programSendMessagePropertiesList.add(createBooleanProperty("area.7", true));
            programSendMessagePropertiesList.add(createBooleanProperty("area.8", true));
            programSendMessagePropertiesList.add(createBooleanProperty("evCatFAULT", true));
            programSendMessagePropertiesList.add(createBooleanProperty("evCatMAINS", true));
            programSendMessagePropertiesList.add(createBooleanProperty("evCatACTZN", true));
            programSendMessagePropertiesList.add(createBooleanProperty("evCatACT24H", true));
            programSendMessagePropertiesList.add(createBooleanProperty("evCatACTLCD", true));
            programSendMessagePropertiesList.add(createBooleanProperty("evCatACTDEV", true));
            programSendMessagePropertiesList.add(createBooleanProperty("evCatALARMS_NCNF", true));
            programSendMessagePropertiesList.add(createBooleanProperty("evCatFAULTS_CNF", true));
            programSendMessagePropertiesList.add(createBooleanProperty("evCatWALK_REQ", true));
            programSendMessagePropertiesList.add(createBooleanProperty("evCatWALK_OK", true));
            programSendMessagePropertiesList.add(createBooleanProperty("evCatSYSTEM", true));
            programSendMessagePropertiesList.add(createBooleanProperty("next", next));

            programSendMessageProperties.getProgramProperty().addAll(programSendMessagePropertiesList);
            programSendMessage.setProperties(programSendMessageProperties);
            programSendMessage.setName("get.liveEvents");
            ProgramSendMessageResponse _programSendMessage__return = port.programSendMessage(programSendMessage);

            logger.debug("The gateway returned a message of type : {}", _programSendMessage__return.getName());

            for (ProgramProperty property : _programSendMessage__return.getProperties().getProgramProperty()) {
                logger.debug("\t Property {} : {} : {}",
                        new Object[] { property.getId(), property.getIndex(), property.getValue() });
            }

            if (_programSendMessage__return.getName().equals("return.error")) {
                handleError(
                        (String) _programSendMessage__return.getProperties().getProgramProperty().get(0).getValue());
            } else {
                return _programSendMessage__return;
            }
        }
        return null;

    }

    public boolean getLiveEvents() throws ATSAdvancedException {

        if (isWebServicesSetUp()) {

            ProgramSendMessageResponse _programSendMessage__return = getLiveEvent(false);

            while (_programSendMessage__return.getName().equals("return.sysevent")) {
                _programSendMessage__return = getLiveEvent(true);
            }

            return true;

        } else {
            return false;
        }
    }

    public boolean unsetArea(int area) {

        int sessionID = initiateUnsetControlSession(area);

        ControlSessionState state = getControlSessionState(sessionID);

        if (state == ControlSessionState.CSMS_UC_Ready) {
            unArmAreas(sessionID);
        } else {
            // wrong state
            return false;
        }

        state = getControlSessionState(sessionID);

        while (state == ControlSessionState.CSMS_UC_Unsetting) {
            state = getControlSessionState(sessionID);
        }

        if (state == ControlSessionState.CSMS_UC_CnfAlarms) {
            skipAlarms(sessionID);
        }

        if (state == ControlSessionState.CSMS_UC_CnfFaults) {
            skipFaults(sessionID);
        }

        if (state == ControlSessionState.CSMS_UC_Unset) {
            finishControlSession(sessionID);
            return true;
        }

        return false;

    }

    public boolean setArea(int area) {

        int sessionID = initiateSetControlSession(area);

        if (sessionID == 0) {
            if (StringUtils.contains(lastError, "FAULT_CC_BUSY_AREAS")) {
                unsetArea(area);
            }

            return false;
        } else {

            ControlSessionState state = getControlSessionState(sessionID);

            if (state == ControlSessionState.CSMS_FC_Ready) {
                armAreas(sessionID);
            } else {
                // wrong state
                return false;
            }

            state = getControlSessionState(sessionID);

            while (state != ControlSessionState.CSMS_FC_Setting) {

                if (state == ControlSessionState.CSMS_FC_Faults) {
                    logger.debug("There are detected faults in the system that prevents setting selected areas");
                    getFaults(sessionID);
                }

                if (state == ControlSessionState.CSMS_FC_ActiveStates) {
                    logger.debug(
                            "There are detected active states in zones or devices that prevents setting selected areas");
                    getActiveZones(sessionID);
                }

                state = getControlSessionState(sessionID);

            }

            finishControlSession(sessionID);

            return true;
        }

    }

    private void getActiveZones(int sessionID) {

        if (isWebServicesSetUp()) {

            ProgramSendMessageResponse _programSendMessage__return = getActiveZones(sessionID, false);

            while (_programSendMessage__return.getName().equals("return.sysevent")) {

                // inhibit the fault
                // TODO add parameter to switch enable this - we might want to skip setting if there are active zones
                for (ProgramProperty property : _programSendMessage__return.getProperties().getProgramProperty()) {
                    if (property.getId().equals("eventUniqueID")) {
                        inhibitActiveZone(sessionID, (int) property.getValue());
                    }
                }

                _programSendMessage__return = getActiveZones(sessionID, true);
            }

        }
    }

    private boolean inhibitActiveZone(int sessionID, int eventID) {

        if (isWebServicesSetUp()) {

            ISyncReply port = syncReply.getBasicHttpBindingISyncReply();

            logger.debug("The gateway will inhibit the fault with ID {} for session : {}", eventID, sessionID);
            ProgramSendMessage programSendMessage = new ProgramSendMessage();
            ArrayOfProgramProperty programSendMessageProperties = new ArrayOfProgramProperty();
            List<ProgramProperty> programSendMessagePropertiesList = new ArrayList<ProgramProperty>();

            ProgramProperty ProgramProperty1 = new ProgramProperty();
            ProgramProperty1.setId("sessionID");
            ProgramProperty1.setIndex(0);
            java.lang.Object ProgramProperty1Value = sessionID;
            ProgramProperty1.setValue(ProgramProperty1Value);
            programSendMessagePropertiesList.add(ProgramProperty1);

            ProgramProperty ProgramProperty2 = new ProgramProperty();
            ProgramProperty2.setId("eventUniqueID");
            ProgramProperty2.setIndex(0);
            java.lang.Object ProgramProperty2Value = eventID;
            ProgramProperty2.setValue(ProgramProperty2Value);
            programSendMessagePropertiesList.add(ProgramProperty2);

            programSendMessageProperties.getProgramProperty().addAll(programSendMessagePropertiesList);
            programSendMessage.setProperties(programSendMessageProperties);
            programSendMessage.setName("fnCC.A_SET_INHACTIVE");
            ProgramSendMessageResponse _programSendMessage__return = port.programSendMessage(programSendMessage);

            logger.debug("The gateway returned a message of type : {}", _programSendMessage__return.getName());

            for (ProgramProperty property : _programSendMessage__return.getProperties().getProgramProperty()) {
                logger.debug("\t Property {} : {} : {}",
                        new Object[] { property.getId(), property.getIndex(), property.getValue() });
            }

            if (_programSendMessage__return.getName().equals("return.error")) {
                handleError(
                        (String) _programSendMessage__return.getProperties().getProgramProperty().get(0).getValue());
            } else {
                for (ProgramProperty property : _programSendMessage__return.getProperties().getProgramProperty()) {
                    if (property.getId().equals("result")) {
                        return (boolean) property.getValue();
                    }
                }
            }
        }

        return false;
    }

    private ProgramSendMessageResponse getActiveZones(int sessionID, boolean next) {

        if (isWebServicesSetUp()) {

            ISyncReply port = syncReply.getBasicHttpBindingISyncReply();

            logger.debug("The gateway will fetch the faults for session : {}", sessionID);
            ProgramSendMessage programSendMessage = new ProgramSendMessage();
            ArrayOfProgramProperty programSendMessageProperties = new ArrayOfProgramProperty();
            List<ProgramProperty> programSendMessagePropertiesList = new ArrayList<ProgramProperty>();

            ProgramProperty ProgramProperty1 = new ProgramProperty();
            ProgramProperty1.setId("sessionID");
            ProgramProperty1.setIndex(0);
            java.lang.Object ProgramProperty1Value = sessionID;
            ProgramProperty1.setValue(ProgramProperty1Value);
            programSendMessagePropertiesList.add(ProgramProperty1);

            programSendMessagePropertiesList.add(createBooleanProperty("next", next));

            programSendMessageProperties.getProgramProperty().addAll(programSendMessagePropertiesList);
            programSendMessage.setProperties(programSendMessageProperties);
            programSendMessage.setName("fnCC.A_SET_GETACTIVE");
            ProgramSendMessageResponse _programSendMessage__return = port.programSendMessage(programSendMessage);

            logger.debug("The gateway returned a message of type : {}", _programSendMessage__return.getName());

            for (ProgramProperty property : _programSendMessage__return.getProperties().getProgramProperty()) {
                logger.debug("\t Property {} : {} : {}",
                        new Object[] { property.getId(), property.getIndex(), property.getValue() });
            }

            if (_programSendMessage__return.getName().equals("return.error")) {
                handleError(
                        (String) _programSendMessage__return.getProperties().getProgramProperty().get(0).getValue());
            } else {
                return _programSendMessage__return;
            }
        }

        return null;

    }

    private void getFaults(int sessionID) {

        if (isWebServicesSetUp()) {

            ProgramSendMessageResponse _programSendMessage__return = getFaults(sessionID, false);

            while (_programSendMessage__return.getName().equals("return.sysevent")) {

                for (ProgramProperty property : _programSendMessage__return.getProperties().getProgramProperty()) {
                    if (property.getId().equals("eventUniqueID")) {
                        inhibitFault(sessionID, (int) property.getValue());
                    }
                }

                _programSendMessage__return = getFaults(sessionID, true);
            }

        }
    }

    private boolean inhibitFault(int sessionID, int eventID) {

        if (isWebServicesSetUp()) {

            ISyncReply port = syncReply.getBasicHttpBindingISyncReply();

            logger.debug("The gateway will inhibit the fault with ID {} for session : {}", eventID, sessionID);
            ProgramSendMessage programSendMessage = new ProgramSendMessage();
            ArrayOfProgramProperty programSendMessageProperties = new ArrayOfProgramProperty();
            List<ProgramProperty> programSendMessagePropertiesList = new ArrayList<ProgramProperty>();

            ProgramProperty ProgramProperty1 = new ProgramProperty();
            ProgramProperty1.setId("sessionID");
            ProgramProperty1.setIndex(0);
            java.lang.Object ProgramProperty1Value = sessionID;
            ProgramProperty1.setValue(ProgramProperty1Value);
            programSendMessagePropertiesList.add(ProgramProperty1);

            ProgramProperty ProgramProperty2 = new ProgramProperty();
            ProgramProperty2.setId("eventUniqueID");
            ProgramProperty2.setIndex(0);
            java.lang.Object ProgramProperty2Value = eventID;
            ProgramProperty2.setValue(ProgramProperty2Value);
            programSendMessagePropertiesList.add(ProgramProperty2);

            programSendMessageProperties.getProgramProperty().addAll(programSendMessagePropertiesList);
            programSendMessage.setProperties(programSendMessageProperties);
            programSendMessage.setName("fnCC.A_SET_INHFAULT");
            ProgramSendMessageResponse _programSendMessage__return = port.programSendMessage(programSendMessage);

            logger.debug("The gateway returned a message of type : {}", _programSendMessage__return.getName());

            for (ProgramProperty property : _programSendMessage__return.getProperties().getProgramProperty()) {
                logger.debug("\t Property {} : {} : {}",
                        new Object[] { property.getId(), property.getIndex(), property.getValue() });
            }

            if (_programSendMessage__return.getName().equals("return.error")) {
                handleError(
                        (String) _programSendMessage__return.getProperties().getProgramProperty().get(0).getValue());
            } else {
                for (ProgramProperty property : _programSendMessage__return.getProperties().getProgramProperty()) {
                    if (property.getId().equals("result")) {
                        return (boolean) property.getValue();
                    }
                }
            }
        }

        return false;
    }

    private ProgramSendMessageResponse getFaults(int sessionID, boolean next) {

        if (isWebServicesSetUp()) {

            ISyncReply port = syncReply.getBasicHttpBindingISyncReply();

            logger.debug("The gateway will fetch the faults for session : {}", sessionID);
            ProgramSendMessage programSendMessage = new ProgramSendMessage();
            ArrayOfProgramProperty programSendMessageProperties = new ArrayOfProgramProperty();
            List<ProgramProperty> programSendMessagePropertiesList = new ArrayList<ProgramProperty>();

            ProgramProperty ProgramProperty1 = new ProgramProperty();
            ProgramProperty1.setId("sessionID");
            ProgramProperty1.setIndex(0);
            java.lang.Object ProgramProperty1Value = sessionID;
            ProgramProperty1.setValue(ProgramProperty1Value);
            programSendMessagePropertiesList.add(ProgramProperty1);

            programSendMessagePropertiesList.add(createBooleanProperty("next", next));

            programSendMessageProperties.getProgramProperty().addAll(programSendMessagePropertiesList);
            programSendMessage.setProperties(programSendMessageProperties);
            programSendMessage.setName("fnCC.A_SET_GETFAULT");
            ProgramSendMessageResponse _programSendMessage__return = port.programSendMessage(programSendMessage);

            logger.debug("The gateway returned a message of type : {}", _programSendMessage__return.getName());

            for (ProgramProperty property : _programSendMessage__return.getProperties().getProgramProperty()) {
                logger.debug("\t Property {} : {} : {}",
                        new Object[] { property.getId(), property.getIndex(), property.getValue() });
            }

            if (_programSendMessage__return.getName().equals("return.error")) {
                handleError(
                        (String) _programSendMessage__return.getProperties().getProgramProperty().get(0).getValue());
            } else {
                return _programSendMessage__return;
            }
        }

        return null;

    }

    private boolean armAreas(int sessionID) {

        if (isWebServicesSetUp()) {

            ISyncReply port = syncReply.getBasicHttpBindingISyncReply();

            logger.debug("The gateway will arm the areas for session : {}", sessionID);
            ProgramSendMessage programSendMessage = new ProgramSendMessage();
            ArrayOfProgramProperty programSendMessageProperties = new ArrayOfProgramProperty();
            List<ProgramProperty> programSendMessagePropertiesList = new ArrayList<ProgramProperty>();

            ProgramProperty ProgramProperty1 = new ProgramProperty();
            ProgramProperty1.setId("sessionID");
            ProgramProperty1.setIndex(0);
            java.lang.Object ProgramProperty1Value = sessionID;
            ProgramProperty1.setValue(ProgramProperty1Value);
            programSendMessagePropertiesList.add(ProgramProperty1);

            programSendMessageProperties.getProgramProperty().addAll(programSendMessagePropertiesList);
            programSendMessage.setProperties(programSendMessageProperties);
            programSendMessage.setName("fnCC.A_SET_SETAREAS");
            ProgramSendMessageResponse _programSendMessage__return = port.programSendMessage(programSendMessage);

            logger.debug("The gateway returned a message of type : {}", _programSendMessage__return.getName());

            for (ProgramProperty property : _programSendMessage__return.getProperties().getProgramProperty()) {
                logger.debug("\t Property {} : {} : {}",
                        new Object[] { property.getId(), property.getIndex(), property.getValue() });
            }

            if (_programSendMessage__return.getName().equals("return.error")) {
                handleError(
                        (String) _programSendMessage__return.getProperties().getProgramProperty().get(0).getValue());
            } else {
                return true;
            }
        }

        return false;

    }

    private boolean skipAlarms(int sessionID) {

        if (isWebServicesSetUp()) {

            ISyncReply port = syncReply.getBasicHttpBindingISyncReply();

            logger.debug("The gateway will arm the areas for session : {}", sessionID);
            ProgramSendMessage programSendMessage = new ProgramSendMessage();
            ArrayOfProgramProperty programSendMessageProperties = new ArrayOfProgramProperty();
            List<ProgramProperty> programSendMessagePropertiesList = new ArrayList<ProgramProperty>();

            ProgramProperty ProgramProperty1 = new ProgramProperty();
            ProgramProperty1.setId("sessionID");
            ProgramProperty1.setIndex(0);
            java.lang.Object ProgramProperty1Value = sessionID;
            ProgramProperty1.setValue(ProgramProperty1Value);
            programSendMessagePropertiesList.add(ProgramProperty1);

            programSendMessageProperties.getProgramProperty().addAll(programSendMessagePropertiesList);
            programSendMessage.setProperties(programSendMessageProperties);
            programSendMessage.setName("fnCC.A_UNSET_SKIP");
            ProgramSendMessageResponse _programSendMessage__return = port.programSendMessage(programSendMessage);

            logger.debug("The gateway returned a message of type : {}", _programSendMessage__return.getName());

            for (ProgramProperty property : _programSendMessage__return.getProperties().getProgramProperty()) {
                logger.debug("\t Property {} : {} : {}",
                        new Object[] { property.getId(), property.getIndex(), property.getValue() });
            }

            if (_programSendMessage__return.getName().equals("return.error")) {
                handleError(
                        (String) _programSendMessage__return.getProperties().getProgramProperty().get(0).getValue());
            } else {
                return true;
            }
        }
        return false;
    }

    private boolean skipFaults(int sessionID) {

        if (isWebServicesSetUp()) {

            ISyncReply port = syncReply.getBasicHttpBindingISyncReply();

            logger.debug("The gateway will arm the areas for session : {}", sessionID);
            ProgramSendMessage programSendMessage = new ProgramSendMessage();
            ArrayOfProgramProperty programSendMessageProperties = new ArrayOfProgramProperty();
            List<ProgramProperty> programSendMessagePropertiesList = new ArrayList<ProgramProperty>();

            ProgramProperty ProgramProperty1 = new ProgramProperty();
            ProgramProperty1.setId("sessionID");
            ProgramProperty1.setIndex(0);
            java.lang.Object ProgramProperty1Value = sessionID;
            ProgramProperty1.setValue(ProgramProperty1Value);
            programSendMessagePropertiesList.add(ProgramProperty1);

            programSendMessageProperties.getProgramProperty().addAll(programSendMessagePropertiesList);
            programSendMessage.setProperties(programSendMessageProperties);
            programSendMessage.setName("fnCC.A_UNSET_SKIP");
            ProgramSendMessageResponse _programSendMessage__return = port.programSendMessage(programSendMessage);

            logger.debug("The gateway returned a message of type : {}", _programSendMessage__return.getName());

            for (ProgramProperty property : _programSendMessage__return.getProperties().getProgramProperty()) {
                logger.debug("\t Property {} : {} : {}",
                        new Object[] { property.getId(), property.getIndex(), property.getValue() });
            }

            if (_programSendMessage__return.getName().equals("return.error")) {
                handleError(
                        (String) _programSendMessage__return.getProperties().getProgramProperty().get(0).getValue());
            } else {
                return true;
            }
        }
        return false;
    }

    private boolean unArmAreas(int sessionID) {

        if (isWebServicesSetUp()) {

            ISyncReply port = syncReply.getBasicHttpBindingISyncReply();

            logger.debug("The gateway will unarm the areas for session : {}", sessionID);
            ProgramSendMessage programSendMessage = new ProgramSendMessage();
            ArrayOfProgramProperty programSendMessageProperties = new ArrayOfProgramProperty();
            List<ProgramProperty> programSendMessagePropertiesList = new ArrayList<ProgramProperty>();

            ProgramProperty ProgramProperty1 = new ProgramProperty();
            ProgramProperty1.setId("sessionID");
            ProgramProperty1.setIndex(0);
            java.lang.Object ProgramProperty1Value = sessionID;
            ProgramProperty1.setValue(ProgramProperty1Value);
            programSendMessagePropertiesList.add(ProgramProperty1);

            programSendMessageProperties.getProgramProperty().addAll(programSendMessagePropertiesList);
            programSendMessage.setProperties(programSendMessageProperties);
            programSendMessage.setName("fnCC.A_UNSET_UNSETAREAS");
            ProgramSendMessageResponse _programSendMessage__return = port.programSendMessage(programSendMessage);

            logger.debug("The gateway returned a message of type : {}", _programSendMessage__return.getName());

            for (ProgramProperty property : _programSendMessage__return.getProperties().getProgramProperty()) {
                logger.debug("\t Property {} : {} : {}",
                        new Object[] { property.getId(), property.getIndex(), property.getValue() });
            }

            if (_programSendMessage__return.getName().equals("return.error")) {
                handleError(
                        (String) _programSendMessage__return.getProperties().getProgramProperty().get(0).getValue());
            } else {
                return true;
            }
        }
        return false;
    }

    private ControlSessionState getControlSessionState(int sessionID) {

        if (isWebServicesSetUp()) {

            ISyncReply port = syncReply.getBasicHttpBindingISyncReply();

            logger.debug("The gateway will fetch the control session state for session : {}", sessionID);
            ProgramSendMessage programSendMessage = new ProgramSendMessage();
            ArrayOfProgramProperty programSendMessageProperties = new ArrayOfProgramProperty();
            List<ProgramProperty> programSendMessagePropertiesList = new ArrayList<ProgramProperty>();

            ProgramProperty ProgramProperty1 = new ProgramProperty();
            ProgramProperty1.setId("sessionID");
            ProgramProperty1.setIndex(0);
            java.lang.Object ProgramProperty1Value = sessionID;
            ProgramProperty1.setValue(ProgramProperty1Value);
            programSendMessagePropertiesList.add(ProgramProperty1);

            programSendMessageProperties.getProgramProperty().addAll(programSendMessagePropertiesList);
            programSendMessage.setProperties(programSendMessageProperties);
            programSendMessage.setName("statusCC.SESSION");
            ProgramSendMessageResponse _programSendMessage__return = port.programSendMessage(programSendMessage);

            logger.debug("The gateway returned a message of type : {}", _programSendMessage__return.getName());

            for (ProgramProperty property : _programSendMessage__return.getProperties().getProgramProperty()) {
                logger.debug("\t Property {} : {} : {}",
                        new Object[] { property.getId(), property.getIndex(), property.getValue() });
            }

            if (_programSendMessage__return.getName().equals("return.error")) {
                handleError(
                        (String) _programSendMessage__return.getProperties().getProgramProperty().get(0).getValue());
            } else {
                for (ProgramProperty property : _programSendMessage__return.getProperties().getProgramProperty()) {
                    if (property.getId().equals("stateID")) {
                        return ControlSessionState.forValue((int) (long) property.getValue());
                    }
                }
            }
        }

        return ControlSessionState.CSMS_UNKNOWN;

    }

    private boolean finishControlSession(int sessionID) {

        if (isWebServicesSetUp()) {

            ISyncReply port = syncReply.getBasicHttpBindingISyncReply();

            logger.debug("The gateway will finish the control session for session : {}", sessionID);
            ProgramSendMessage programSendMessage = new ProgramSendMessage();
            ArrayOfProgramProperty programSendMessageProperties = new ArrayOfProgramProperty();
            List<ProgramProperty> programSendMessagePropertiesList = new ArrayList<ProgramProperty>();

            ProgramProperty ProgramProperty1 = new ProgramProperty();
            ProgramProperty1.setId("sessionID");
            ProgramProperty1.setIndex(0);
            java.lang.Object ProgramProperty1Value = sessionID;
            ProgramProperty1.setValue(ProgramProperty1Value);
            programSendMessagePropertiesList.add(ProgramProperty1);

            programSendMessageProperties.getProgramProperty().addAll(programSendMessagePropertiesList);
            programSendMessage.setProperties(programSendMessageProperties);
            programSendMessage.setName("destroyCC.SESSION");
            ProgramSendMessageResponse _programSendMessage__return = port.programSendMessage(programSendMessage);

            logger.debug("The gateway returned a message of type : {}", _programSendMessage__return.getName());

            for (ProgramProperty property : _programSendMessage__return.getProperties().getProgramProperty()) {
                logger.debug("\t Property {} : {} : {}",
                        new Object[] { property.getId(), property.getIndex(), property.getValue() });
            }

            if (_programSendMessage__return.getName().equals("return.error")) {
                handleError(
                        (String) _programSendMessage__return.getProperties().getProgramProperty().get(0).getValue());
            } else {
                return true;
            }
        }
        return false;
    }

    private int initiateSetControlSession(int area) {

        if (isWebServicesSetUp()) {

            ISyncReply port = syncReply.getBasicHttpBindingISyncReply();

            logger.debug("The gateway will start the set control session for area: {}", area);
            ProgramSendMessage programSendMessage = new ProgramSendMessage();
            ArrayOfProgramProperty programSendMessageProperties = new ArrayOfProgramProperty();
            List<ProgramProperty> programSendMessagePropertiesList = new ArrayList<ProgramProperty>();

            for (int i = 1; i < MAX_NUMBER_AREAS + 1; i++) {
                programSendMessagePropertiesList.add(createBooleanProperty("area." + i, i == area));
            }

            programSendMessageProperties.getProgramProperty().addAll(programSendMessagePropertiesList);
            programSendMessage.setProperties(programSendMessageProperties);
            programSendMessage.setName("createCC.A_SET");
            ProgramSendMessageResponse _programSendMessage__return = port.programSendMessage(programSendMessage);

            logger.debug("The gateway returned a message of type : {}", _programSendMessage__return.getName());

            for (ProgramProperty property : _programSendMessage__return.getProperties().getProgramProperty()) {
                logger.debug("\t Property {} : {} : {}",
                        new Object[] { property.getId(), property.getIndex(), property.getValue() });
            }

            if (_programSendMessage__return.getName().equals("return.error")) {
                handleError(
                        (String) _programSendMessage__return.getProperties().getProgramProperty().get(0).getValue());
            } else {
                for (ProgramProperty property : _programSendMessage__return.getProperties().getProgramProperty()) {
                    if (property.getId().equals("result")) {
                        return (int) (long) property.getValue();
                    }
                }
            }
        }

        return 0;

    }

    private int initiateUnsetControlSession(int area) {

        if (isWebServicesSetUp()) {

            ISyncReply port = syncReply.getBasicHttpBindingISyncReply();

            logger.debug("The gateway will start the unset control session for area: {}", area);
            ProgramSendMessage programSendMessage = new ProgramSendMessage();
            ArrayOfProgramProperty programSendMessageProperties = new ArrayOfProgramProperty();
            List<ProgramProperty> programSendMessagePropertiesList = new ArrayList<ProgramProperty>();

            for (int i = 1; i < MAX_NUMBER_AREAS + 1; i++) {
                programSendMessagePropertiesList.add(createBooleanProperty("area." + i, i == area));
            }

            programSendMessageProperties.getProgramProperty().addAll(programSendMessagePropertiesList);
            programSendMessage.setProperties(programSendMessageProperties);
            programSendMessage.setName("createCC.A_UNSET");
            ProgramSendMessageResponse _programSendMessage__return = port.programSendMessage(programSendMessage);

            logger.debug("The gateway returned a message of type : {}", _programSendMessage__return.getName());

            for (ProgramProperty property : _programSendMessage__return.getProperties().getProgramProperty()) {
                logger.debug("\t Property {} : {} : {}",
                        new Object[] { property.getId(), property.getIndex(), property.getValue() });
            }

            if (_programSendMessage__return.getName().equals("return.error")) {
                handleError(
                        (String) _programSendMessage__return.getProperties().getProgramProperty().get(0).getValue());
            } else {
                for (ProgramProperty property : _programSendMessage__return.getProperties().getProgramProperty()) {
                    if (property.getId().equals("result")) {
                        return (int) (long) property.getValue();
                    }
                }
            }
        }

        return 0;

    }

    @WebService(name = "EventLogger", targetNamespace = "ATSAdvanced.DTO", portName = "EventLoggerPort")
    @SOAPBinding(style = Style.DOCUMENT, use = Use.LITERAL, parameterStyle = ParameterStyle.WRAPPED)
    public class EventLogger {

        @WebMethod
        @WebResult(targetNamespace = "ATSAdvanced.DTO", name = "reponse")
        public ProgramSendMessage logMessage(
                @WebParam(targetNamespace = "ATSAdvanced.DTO", name = "message", mode = Mode.IN) ProgramSendMessage logEntry) {

            ArrayOfProgramProperty logProperties = logEntry.getProperties();

            switch (logEntry.getName()) {
                case "msg.error": {
                    handleError((String) logEntry.getProperties().getProgramProperty().get(0).getValue());
                    break;
                }
                case "msgCOS.ALL": {
                    for (ProgramProperty aProperty : logProperties.getProgramProperty()) {
                        switch (aProperty.getId()) {
                            case "APPOBJ_ZN": {
                                if ((boolean) aProperty.getValue()) {
                                    updateChangedZonesStatus();
                                }
                                break;
                            }
                            case "APPOBJ_AREA": {
                                if ((boolean) aProperty.getValue()) {
                                    updateChangedAreasStatus();
                                }
                                break;
                            }
                            default:
                                break;
                        }
                        ;
                    }
                    break;
                }
                case "msg.MONITOR": {
                    XMLGregorianCalendar timeStamp = null;
                    int uniqueID = 0;
                    int eventID = 0;
                    int eventSource = 0;
                    int sourceID = 0;
                    int area = 0;
                    String eventText = null;

                    for (ProgramProperty aProperty : logProperties.getProgramProperty()) {
                        // logger.debug("\t property {} : {}",aProperty.getId(),aProperty.getValue());
                        if (aProperty.getId().equals("timeStamp")) {
                            timeStamp = (XMLGregorianCalendar) aProperty.getValue();
                        }
                        if (aProperty.getId().equals("unique_id")) {
                            uniqueID = (int) (long) aProperty.getValue();
                        }
                        if (aProperty.getId().equals("event_ID")) {
                            eventID = (int) (long) aProperty.getValue();
                        }
                        if (aProperty.getId().equals("event_source")) {
                            eventSource = (int) (long) aProperty.getValue();
                        }
                        if (aProperty.getId().equals("source_ID")) {
                            sourceID = (int) (long) aProperty.getValue();
                        }
                        if (aProperty.getId().equals("Area")) {
                            if (aProperty.getValue() != null) {
                                area = (int) (long) aProperty.getValue();
                            }
                        }
                        if (aProperty.getId().equals("event_text")) {
                            eventText = (String) aProperty.getValue();
                        }
                    }

                    String result = timeStamp.toString() + ": " + "id " + uniqueID + " :" + " event type " + eventID
                            + " :" + " source " + eventSource + " :" + " sourceID " + sourceID + ": " + " area" + area
                            + " :" + " detail :" + eventText;

                    updateState(new ChannelUID(getThing().getUID(), ATSadvancedBindingConstants.MONITOR),
                            (result != null) ? new StringType(result) : UnDefType.UNDEF);
                }
                    break;
            }
            ;

            ProgramSendMessage ack = new ProgramSendMessage();
            ack.setName("return.bool");
            ArrayOfProgramProperty properties = new ArrayOfProgramProperty();

            ProgramProperty Value1 = new ProgramProperty();
            Value1.setId("result");
            Value1.setIndex(0);
            Value1.setValue(1);

            properties.getProgramProperty().add(Value1);

            ack.setProperties(properties);

            // logger.debug("Acknowledging the event to the panel: {}",logEntry.getName());
            return ack;
        }
    }

    private boolean handlingError = false;

    private boolean handleError(String error) {
        // handle errors returned from the panel
        try {
            if (!handlingError) {
                logger.debug("Handling a panel or gateway error: {}", error);
                if (StringUtils.contains(error, "Connection refused")
                        || StringUtils.contains(error, "The socket has been shut down")
                        || StringUtils.contains(error, "Connection reset by peer")
                        || StringUtils.contains(error, "Object reference not set to an instance of an object")) {
                    handlingError = true;
                    logout();
                    // tearDown();
                    onConnectionLost();
                    handlingError = false;
                }
                lastError = error;
                return true;
            }
        } catch (Exception e) {
            logger.error("An exception ocurred while handling an error at the panel or gateway: '{}'", e.getMessage());
        }
        return false;
    }

    class GatewayLogHangler extends LogOutputStream {

        Logger log;
        int level;

        public GatewayLogHangler(Logger log, int level) {
            super(level);
            this.log = log;
        }

        @Override
        protected void processLine(String line, int level) {
            switch (level) {
                case 0: {
                    logger.debug("Gateway [DEBUG]: '{}'", line);
                    break;
                }
                case 1: {
                    logger.error("Gateway [ERROR]: '{}'", line);
                    break;
                }
            }
        }
    }

    public class LoggingShutdownHookProcessDestroyer extends ShutdownHookProcessDestroyer {

        @Override
        public void run() {
            logger.info("Shutdown Hook: JVM is about to exit! Going to kill ATS gateway processes...");
            super.run();
        }
    }

    public class GatewayExecuteResultHandler extends DefaultExecuteResultHandler {

        @Override
        public void onProcessComplete(int exitValue) {
            super.onProcessComplete(exitValue);
            logger.info("The gateway process just exited, with value '{}'", exitValue);
            gatewayProcessStarted = false;
            onConnectionLost();
        }

        @Override
        public void onProcessFailed(ExecuteException e) {
            super.onProcessFailed(e);
            if (!watchDog.killedProcess()) {
                logger.error("The gateway process just failed unexpectedly with value '{}'", e.getExitValue());
                logger.error("The gateway process just failed unexpectedly with message '{}'", e.getMessage());
            }
            gatewayProcessStarted = false;
            onConnectionLost();
        }
    }
}
