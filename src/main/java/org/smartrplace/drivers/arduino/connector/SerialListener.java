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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ogema.core.channelmanager.measurements.SampledValue;
import org.ogema.core.model.ValueResource;
import org.ogema.core.model.schedule.Schedule;
import org.ogema.core.model.simple.SingleValueResource;
import org.ogema.tools.resource.util.ValueResourceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartrplace.drivers.arduino.connector.pattern.ArduinoPattern;

import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;

public class SerialListener implements SerialPortEventListener {

	private final String portString;
	private final Logger logger = LoggerFactory.getLogger(ArduinoConnector.class);
	private final Map<Integer,ArduinoPattern> patterns = new HashMap<Integer, ArduinoPattern>();
	private final Map<Integer,ValueResource> resources = new HashMap<Integer, ValueResource>();
	private volatile String arduinoId = null; // reported by Arduino upon request
	// TODO handle overflow on Arduino side (~50d)
	private volatile long[] synchronization = new long[0]; // 1st entry: Arduino time; 2nd entry: OGEMA time 
	
	public SerialListener(String portString) {
		logger.info("New Serial listener for port " + portString);
		this.portString = portString;
		initialize();
	}
	
	
	SerialPort serialPort;
    /** The port we're normally going to use. */
//	private static final String PORT_NAMES[] = { 
//			"/dev/tty.usbserial-A9007UX1", // Mac OS X
//	                    "/dev/ttyACM0", // Raspberry Pi
//			"/dev/ttyUSB0", // Linux
//			"COM3", // Windows
//	};

	/**
	 * A BufferedReader which will be fed by a InputStreamReader converting the
	 * bytes into characters making the displayed results codepage independent
	 */
	private BufferedReader input;
	/** The output stream to the port */
//	private OutputStream output;

	/**
	 * Note that the id may be null simply because no id request has been sent yet 
	 * @return
	 */
	public String getArduinoId() {
		return arduinoId;
	}
	

	/**
	 * Handle an event on the serial port. Read the data and print it.
	 */
	@Override
	public synchronized void serialEvent(SerialPortEvent oEvent) {
		if (oEvent.getEventType() == SerialPortEvent.DATA_AVAILABLE) {
			try {
				String inputLine = input.readLine();
				logger.trace("Serial input available from port " + portString + ": " + inputLine);
				String[] components = inputLine.split("\\s", 2);
				if (components.length < 2) {
					logger.warn("Serial data received cannot be assigned to an OGEMA address: " + inputLine);
					return;
				}
				if (components[0].startsWith("ogm::")) {
					String address = components[0];
					int add = Integer.parseInt(address.substring(5));
					ValueResource res = resources.get(add);
					if (res == null) {
						logger.warn("Serial address " + add + " on port " + portString + " not registered");
						return;
					}
					if (res instanceof SingleValueResource)
						try {
							ValueResourceUtils.setValue((SingleValueResource) res, components[1]);
						} catch (NumberFormatException ee) {
							logger.warn("Could not set value of resource " + res + "; wrong input type: " + components[1]);
						}
					else if (res instanceof Schedule) {
						if (synchronization.length != 2) { // need to synchronize times before we can write schedule!
							SerialWriter.write(serialPort, Constants.SEND_TIME_SYNCHRO_REQUEST);
						}
						List<SampledValue> sv = ValueUtil.getSampledValues(components[1], synchronization);
						((Schedule) res).addValues(sv);
					}
					else
						logger.warn("Received input data for resource " + res + ", which is not a SingleValueResource... cannot handle this. Data: " + components[1]);
				} 	
				else if (components[0].equals("id")) {
					this.arduinoId = components[1];
					logger.info("New Arduino id received: " + arduinoId);
				}
				else if (components[0].equals("time")) {
					long ardTime = Long.parseLong(components[1]);
					synchronization = new long[]{ardTime, System.currentTimeMillis()};
				}
			} catch (Exception e) {
				logger.warn("Serial data could not be read: ", e); // happens quite often
			}
		}
		// Ignore all the other eventTypes, but you should consider the other
		// ones.
	}

	public void initialize() {
		// the next line is for Raspberry Pi and
		// gets us into the while loop and was suggested here was suggested
		// http://www.raspberrypi.org/phpBB3/viewtopic.php?f=81&t=32186
//		System.setProperty("gnu.io.rxtx.SerialPorts", "/dev/ttyACM0");
//
	
		try {
			// open serial port, and use class name for the appName.
			serialPort = SerialUtils.getPort(portString);

			// open the streams
			input = new BufferedReader(new InputStreamReader(serialPort.getInputStream()));
//			output = serialPort.getOutputStream();

			// add event listeners
			serialPort.addEventListener(this);
			serialPort.notifyOnDataAvailable(true);
		} catch (Exception e) {
			throw new RuntimeException("Serial port listener could not be initialized. ",e);
		}
	}
	
	/**
	 * This should be called when you stop using the port.
	 * This will prevent port locking on platforms like Linux.
	 */
	public synchronized void close() {
		if (serialPort != null) {
			serialPort.removeEventListener();
			try {
				serialPort.close();
			} catch (Exception e) {}
			SerialUtils.removePort(portString); // if a write still uses this port, it will be reopened
		}
		if (input != null) {
			try {
				input.close();
			} catch (Exception e) {}
		}
	}
	
	synchronized void addResource(ArduinoPattern pattern) {
		int address = pattern.serialId.getValue();
		patterns.put(address, pattern);
		resources.put(address,(ValueResource) pattern.model.getParent());
	}
	
	
	synchronized void removeResource(ArduinoPattern pattern) {
		int address = pattern.serialId.getValue();
		patterns.remove(address);
		resources.remove(address);
	}
	
	synchronized int getNrResources() {
		return patterns.size();
	}

}
