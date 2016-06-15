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

import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;
import gnu.io.SerialPort;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.slf4j.LoggerFactory;

public class SerialUtils {
	
	// TODO close ports... Important!!
	
	private static final Map<String, CommPortIdentifier> ports = new HashMap<String, CommPortIdentifier>();
	private static final Map<String, SerialPort> serialports = new HashMap<String, SerialPort>();
	/** Milliseconds to block while waiting for port open */
	static final int TIME_OUT = 2000;
	/** Default bits per second for COM port. */
	private static final int DATA_RATE = 9600;
	
	static synchronized void removePort(String portString) {
		SerialPort portId = serialports.remove(portString);
		ports.remove(portString);
		if (portId != null) 
			portId.close();
	}

	static synchronized SerialPort getPort(String portString) {
		SerialPort portId = serialports.get(portString);
		if (portId == null) {
			// FIXME
			System.out.println("  xxx creating new serial port " + portString);
			portId = getPortNew(portString);
		}
		// FIXME
		else {
			System.out.println("  xxx returning old port " + portString);
		}
		return portId;
	}
	
	private static SerialPort getPortNew(String portString) {
		CommPortIdentifier portId = null;
//		Enumeration portEnum = CommPortIdentifier.getPortIdentifiers();
		StringBuilder sb = new StringBuilder();

		try {
			portId = CommPortIdentifier.getPortIdentifier(portString); // TODO check does this work?
		} catch (NoSuchPortException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		// First, Find an instance of serial port as set in PORT_NAMES.
/*		while (portEnum.hasMoreElements()) {
			CommPortIdentifier currPortId = (CommPortIdentifier) portEnum
					.nextElement();
//			for (String portName : PORT_NAMES) {
//				if (currPortId.getName().equals(portName)) {
//					portId = currPortId;
//					break;
//				}
//			}
			if (currPortId.getName().equals(portString)) {
				portId = currPortId;
				break;
			}
			sb.append(currPortId.getName() + "; ");
		}
*/
		SerialPort serialPort = null;
		if (portId == null) 
			LoggerFactory.getLogger(ArduinoConnector.class).error("Port " + portString + " not found. Available ports: " + sb.toString());
		else {
			try {
				serialPort = (SerialPort) portId.open(ArduinoConnector.class.getName(), TIME_OUT);
				// set port parameters
				serialPort.setSerialPortParams(DATA_RATE, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
				ports.put(portString, portId);
				serialports.put(portString, serialPort);
				// FIXME 
				System.out.println("   --- --- port opened... " + serialPort.getName() + "; " + serialPort.getInputBufferSize());
			} catch (Exception e) {
				LoggerFactory.getLogger(ArduinoConnector.class).error("Could not open port " + portString,e);
				return null;
			}
		}
		return serialPort;
	}
	
	
}
