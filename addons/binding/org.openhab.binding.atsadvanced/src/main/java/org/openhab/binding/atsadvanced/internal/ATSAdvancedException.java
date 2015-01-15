/**
 * Copyright (c) 2010-2014, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.atsadvanced.internal;

/**
 * Exception used during stick initialization
 *
 * @author Karel Goderis
 * @since 1.1.0
 */
public class ATSAdvancedException extends Exception {

	private static final long serialVersionUID = 2095258016390913221L;

	public ATSAdvancedException(String msg) {
		super(msg);
	}

	public ATSAdvancedException(Throwable cause) {
		super(cause);
	}

	public ATSAdvancedException(String msg, Throwable cause) {
		super(msg, cause);
	}
	
}
