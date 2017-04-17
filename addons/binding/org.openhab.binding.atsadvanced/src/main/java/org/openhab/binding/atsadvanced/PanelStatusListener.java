/**
 * Copyright (c) 2014 openHAB UG (haftungsbeschraenkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.atsadvanced;

import org.openhab.binding.atsadvanced.handler.PanelHandler;

/**
 * The {@link PanelStatusListener} is interface that is to be implemented
 * by all classes that wish to be informed of events happening to an ATS Advanced Panel
 * 
 * @author Karel Goderis - Initial contribution
 * @since 2.0.0
 *
 */
public interface PanelStatusListener {
	
	public void onChangedStatus();
	
	/**
	 * 
	 * Called when the connection with the remote panel is lost
	 * 
	 * @param bridge
	 */
	public void onBridgeDisconnected(PanelHandler bridge);
	
	/**
	 * Called when the connection with the remote panel is established
	 * 
	 * @param bridge
	 */
	public void onBridgeConnected(PanelHandler bridge);

}