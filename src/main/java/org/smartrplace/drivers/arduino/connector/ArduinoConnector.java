/**
 *  Copyright 2016 Smartrplace UG
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.smartrplace.drivers.arduino.connector;

import java.util.HashMap;
import java.util.Map;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.ogema.core.application.Application;
import org.ogema.core.application.ApplicationManager;
import org.ogema.core.logging.OgemaLogger;
import org.ogema.core.model.ValueResource;
import org.ogema.core.resourcemanager.AccessPriority;
import org.ogema.core.resourcemanager.pattern.PatternListener;
import org.ogema.core.resourcemanager.pattern.ResourcePatternAccess;
import org.smartrplace.drivers.arduino.connector.pattern.ArduinoPattern;

/**
 * To configure a resource for sending or receiving data via a serial port (e.g. connected to
 * an Arduino), attach a resource of type {@see ArduinoCommunicationInformation} to it. See
 * pattern {@link ArduinoPattern} for the required set of subresources.
 * 
 * @author cnoelle
 */
@Component(specVersion = "1.2", immediate=true)
@Service(Application.class)
public class ArduinoConnector implements Application, PatternListener<ArduinoPattern> {

	private OgemaLogger logger;
	private ResourcePatternAccess rpa;
	// synchronized on the map itself
	// Map<port, listener>
	private final Map<String,SerialListener> serialListeners = new HashMap<String, SerialListener>();
	private final SerialWriter serialWriter = new SerialWriter();
	private final PortScanner portScanner = new PortScanner(this);

	
	@Override
	public void start(ApplicationManager am) {
		this.logger = am.getLogger();
		this.rpa = am.getResourcePatternAccess();
		rpa.addPatternDemand(ArduinoPattern.class, this, AccessPriority.PRIO_LOWEST);
	}
	
    @Override
	public void stop(AppStopReason reason) {
    	
    	rpa.removePatternDemand(ArduinoPattern.class, this);
    	synchronized (serialListeners) {
        	for (SerialListener sl : serialListeners.values()) {
        		sl.close();
        	}
        	serialListeners.clear();
		}
    	serialWriter.close();
    	portScanner.close();
	}

	@Override
	public void patternAvailable(ArduinoPattern pattern) {
		if (pattern.model.isTopLevel() || !(pattern.model.getParent() instanceof ValueResource))
			throw new RuntimeException("Found Arduino data not attached to a value resource; " + pattern.model);
		String port = pattern.comPort.getValue();
		boolean readable = pattern.readable.getValue();
		boolean writable = pattern.writable.getValue();
		if (readable && writable || !readable && !writable) {
			logger.warn("Found Arduino communication information with inconsistent readable/writable settings: " + pattern.model);
			return;
		}
		else if (!pattern.comPort.isActive() || pattern.comPort.getValue() == null || pattern.comPort.getValue().isEmpty()) {
			portScanner.addPattern(pattern);  // in this case the arduinoId is known, but not the port
		}
		else if (readable) {	
			SerialListener sl;
			synchronized (serialListeners) {
				sl = serialListeners.get(port);
				if (sl == null) { 
					sl = new SerialListener(port); // throws RuntimeException if port not found
					serialListeners.put(port, sl);
				}
			}
			sl.addResource(pattern);
			
		}
		else {
			serialWriter.addResource(pattern);
		}
	} 

	@Override
	public void patternUnavailable(ArduinoPattern pattern) {
		String port = pattern.comPort.getValue();
		synchronized (serialListeners) {
			SerialListener sl = serialListeners.get(port);
			if (sl != null) { 
				sl.removeResource(pattern);
				if (sl.getNrResources() == 0) {
					sl.close();
					serialListeners.remove(port);
				}
			}
		}
		serialWriter.removeResource(pattern);  // we cannot really know at this point whether this was a readable or writable pattern... may have changed in the meantime
		portScanner.removePattern(pattern);
	}
    
}
