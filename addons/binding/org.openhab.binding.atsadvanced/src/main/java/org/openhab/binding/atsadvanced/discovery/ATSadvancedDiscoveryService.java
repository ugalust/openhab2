/**
 * Copyright (c) 2014 openHAB UG (haftungsbeschraenkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.atsadvanced.discovery;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.openhab.binding.atsadvanced.ATSadvancedBindingConstants;
import org.openhab.binding.atsadvanced.handler.AreaHandler;
import org.openhab.binding.atsadvanced.handler.PanelHandler;
import org.openhab.binding.atsadvanced.handler.ZoneHandler;
import org.openhab.binding.atsadvanced.webservices.client.ProgramSendMessageResponse;
import org.openhab.binding.atsadvanced.webservices.datacontract.ProgramProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

/**
 * The {@link ATSadvancedDiscoveryService} is responsible for discovering a Area and Zone Things
 * of defined on the ATS Advanced Panel
 * 
 * @author Karel Goderis - Initial contribution
 */
public class ATSadvancedDiscoveryService extends AbstractDiscoveryService {

	private Logger logger = LoggerFactory.getLogger(ATSadvancedDiscoveryService.class);

	public final static Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = ImmutableSet.of(ATSadvancedBindingConstants.THING_TYPE_AREA, ATSadvancedBindingConstants.THING_TYPE_ZONE);
	private static int DISCOVERY_THREAD_INTERVAL = 60;


	private PanelHandler panel;	
	private ScheduledFuture<?> discoveryJob;


	public ATSadvancedDiscoveryService(PanelHandler panel) {
		super(SUPPORTED_THING_TYPES_UIDS, 10);
		this.panel = panel;
	}

	public Set<ThingTypeUID> getSupportedThingTypes() {
		return SUPPORTED_THING_TYPES_UIDS;
	}

	private void  discoverAreasAndZones() {

		try {
			if(panel.isGatewayConfigured() && panel.isConnected() && panel.isLoggedIn() && panel.isWebServicesSetUp()) {

				int current = 1;
				logger.debug("The gateway will fetch a list of zone names");

				while(current <= ATSadvancedBindingConstants.MAX_NUMBER_ZONES) {
					ProgramSendMessageResponse result = panel.getZoneNamesChunk(current);
					// result should contain number of zones + 1 field for "index"
					if(result!=null) {
						current = current + result.getProperties().getProgramProperty().size() -1 ;

						int resultIndex = 0;

						// do something with the result
						for(ProgramProperty property : result.getProperties().getProgramProperty()) {
							if(property.getId().equals("index")) {
								resultIndex = Integer.parseInt(Long.toString((long) property.getValue()));
							}
						}

						for(ProgramProperty property : result.getProperties().getProgramProperty()) {
							if(property.getId().equals("name")) {

								if(!( ((String) property.getValue()).equals("") )) {

									ThingUID uid = new ThingUID(ATSadvancedBindingConstants.THING_TYPE_ZONE, "Zone" + resultIndex);
									if(uid!=null) {
										Map<String, Object> properties = new HashMap<>(1);
										properties.put(ZoneHandler.NUMBER ,String.valueOf(resultIndex));
										properties.put(ZoneHandler.NAME,(String) property.getValue());
										DiscoveryResult discoveryResult = DiscoveryResultBuilder.create(uid)
												.withProperties(properties)
												.withBridge(panel.getThing().getUID())
												.withLabel("ATS Advanced Panel Zone " + resultIndex)
												.build();
										thingDiscovered(discoveryResult);
									}							
								}

								resultIndex++;
							}
						}
					}
				}

				current = 1;
				logger.debug("The gateway will fetch a list of area names");

				while(current <= ATSadvancedBindingConstants.MAX_NUMBER_AREAS) {
					ProgramSendMessageResponse result = panel.getAreaNamesChunk(current);
					// result should contain number of zones + 1 field for "index"
					if(result!=null) {
						current = current + result.getProperties().getProgramProperty().size() -1 ;

						int resultIndex = 0;

						// do something with the result
						for(ProgramProperty property : result.getProperties().getProgramProperty()) {
							if(property.getId().equals("index")) {
								resultIndex = Integer.parseInt(Long.toString((long) property.getValue()));
							}
						}

						for(ProgramProperty property : result.getProperties().getProgramProperty()) {
							if(property.getId().equals("name")) {

								if(!( ((String) property.getValue()).equals("") )) {

									ThingUID uid = new ThingUID(ATSadvancedBindingConstants.THING_TYPE_AREA, "Area" + resultIndex);
									if(uid!=null) {
										Map<String, Object> properties = new HashMap<>(1);
										properties.put(AreaHandler.NUMBER ,String.valueOf(resultIndex));
										properties.put(AreaHandler.NAME,(String) property.getValue());
										DiscoveryResult discoveryResult = DiscoveryResultBuilder.create(uid)
												.withProperties(properties)
												.withBridge(panel.getThing().getUID())
												.withLabel("ATS Advanced Panel Area " + resultIndex)
												.build();
										thingDiscovered(discoveryResult);
									}
								}
								resultIndex++;
							}
						}
					}
				}
			}
		}	
		catch(Exception e) {
			logger.error("An exception occurred while discovering an ATS Advanced Panel: '{}'",e.getMessage());
		}
	}

	private Runnable discoveryRunnable = new Runnable() {
		@Override
		public void run() {
			discoverAreasAndZones();
		}
	};

	@Override
	protected void startBackgroundDiscovery() {
		if (discoveryJob == null || discoveryJob.isCancelled()) {
			discoveryJob = scheduler.scheduleAtFixedRate(discoveryRunnable, 1, DISCOVERY_THREAD_INTERVAL, TimeUnit.SECONDS);
		}
	}

	@Override
	protected void startScan() {
		discoverAreasAndZones();
	}

}
