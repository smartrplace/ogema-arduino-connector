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

import gnu.io.SerialPort;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

import org.ogema.core.model.ValueResource;
import org.ogema.core.model.simple.SingleValueResource;
import org.ogema.core.resourcemanager.ResourceValueListener;
import org.ogema.tools.resource.util.ValueResourceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartrplace.drivers.arduino.connector.pattern.ArduinoPattern;

public class SerialWriter implements ResourceValueListener<ValueResource> {

	private final static Logger logger= LoggerFactory.getLogger(ArduinoConnector.class);
	// Map<Resource location, Arduino pattern>
	private final Map<String,ArduinoPattern> patterns = new HashMap<String, ArduinoPattern>();

	void addResource(ArduinoPattern pattern) {
		String path  = pattern.model.getParent().getPath();
		ArduinoPattern oldPattern = patterns.put(path, pattern);
		if (oldPattern != null) // should not normally happen
			return;
		ValueResource vr = pattern.model.getParent();
		vr.addValueListener(this, true);
	}
	
	void removeResource(ArduinoPattern pattern) {
		ValueResource parent = pattern.model.getParent();
		ArduinoPattern oldP = patterns.remove(parent.getPath());
		if (oldP == null)
			oldP =  patterns.remove(parent.getLocation()); // FIXME does this make sense?
		if (oldP == null) 
			return;
		parent.removeValueListener(this);		
	}
	
	void close() {
		for (ArduinoPattern pt: patterns.values()) {
			pt.model.getParent().removeValueListener(this);
		}
		patterns.clear();
	}

	@Override
	public void resourceChanged(ValueResource resource) {
		ArduinoPattern pt = patterns.get(resource.getPath());
		if (pt == null) {
			logger.warn("Pattern corresponding to " + resource.getPath() + " not found. Cannot write value to serial port");
			return;
		}
		String portStr = pt.comPort.getValue();
		int serialId = pt.serialId.getValue();
		if (!(resource instanceof SingleValueResource)) {
			logger.warn("Resource " + resource + " is not a SingleVlaueResource, cannot handle this");
			return;
		}
		String value = ValueResourceUtils.getValue((SingleValueResource) resource);
		logger.trace("Now trying to write value " + value + " to serial port " + portStr);
		SerialPort port = SerialUtils.getPort(portStr);
		write(port,"ogm::" + serialId + " " + value + (value.endsWith("\n") ? "" : "\n" ));
	}
	
	static void write(SerialPort port, String content) {
		OutputStream out = null;
		try {
			if (port == null) 
				throw new RuntimeException("Port not found. Cannot write value");
			if (content == null)
				return;
			logger.debug("Sending serial data to port " + port.getName() + ": " + content);
			out = port.getOutputStream();
			final PrintStream printStream = new PrintStream(out);
			printStream.print(content);
			printStream.close();
			out.flush();
		} catch (IOException e) {
			throw new RuntimeException("Could not write data to serial port " + port.getName() ,e);
		} finally {
			try {
				if (out != null)
					out.close();
			} catch (Exception ee) {}
		}
	}
	
	
	
}
