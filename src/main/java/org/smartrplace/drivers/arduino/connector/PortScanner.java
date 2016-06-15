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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartrplace.drivers.arduino.connector.pattern.ArduinoPattern;

import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;

public class PortScanner {

	private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
	private volatile ScheduledFuture<?> future = null;
	// synchronized by this
	private final Set<ArduinoPattern> patterns = new HashSet<>();	
	// synchronized on the map itself
	private final Map<String,SerialListener> serialListeners = new HashMap<String, SerialListener>();
	private final static Logger logger = LoggerFactory.getLogger(ArduinoConnector.class);
	private final Scanner scanner = new Scanner();
	private final ArduinoConnector app;
	// Map<Arduino Id, ComPort>
	private final Map<Integer,String> idPortMap = new HashMap<>();
	
	public PortScanner(ArduinoConnector app) {
		this.app = app;
	}

	
	private class Scanner implements Runnable {

		@Override
		public void run() {
			logger.info("Arduino port scanner trying to determine com ports");
			final Map<Integer, ArduinoPattern> ids = new HashMap<>();
			synchronized (PortScanner.this) {
				Iterator<ArduinoPattern> it = patterns.iterator();
				while (it.hasNext()) {
					ArduinoPattern pt = it.next();
					if (!pt.arduinoId.isActive()) 
						it.remove();
					ids.put(pt.arduinoId.getValue(), pt);
				}
				for (Map.Entry<String, String> entry : getSerialPorts().entrySet()) {
					String id = entry.getValue();
					try {
						int idInt = Integer.parseInt(id);
						for (Map.Entry<Integer,ArduinoPattern> pendingEntry: ids.entrySet()) {
							if (pendingEntry.getKey() == idInt) {
								ArduinoPattern pattern = pendingEntry.getValue();
								activatePattern(pattern, entry.getKey());
								patterns.remove(pattern);
								idPortMap.put(idInt, entry.getKey());
								break;
							}
						}
						
					} catch (Exception e) {
						// FIXME
						e.printStackTrace();
						continue;
					}
					
				}
				if (patterns.isEmpty() && future != null) {
					future.cancel(false);
					future = null;
				}
				for (ArduinoPattern pt: patterns) {
					int id = pt.arduinoId.getValue();
					logger.info("No com port found belonging to Arduino " + id);
				}
			}
		}
	}
	
	private void activatePattern(ArduinoPattern pattern,String comPort) {
		pattern.comPort.setValue(comPort);
		pattern.comPort.activate(false);
		app.patternAvailable(pattern);
	}
	
	/** 
	 * Port scanner will try to determine the port belonging to the arduino id
	 * @param pattern
	 */
	synchronized void addPattern(ArduinoPattern pattern) {
		int id = pattern.arduinoId.getValue();
		String comPort = idPortMap.get(id);
		if (comPort != null) {
			activatePattern(pattern, comPort);
			return;
		}
		
		if (!patterns.add(pattern)) return;
		
		if (future == null) {
			// check once per minute whether new Arduino is available, as long as there are pending 
			// arduino ids
			future = exec.scheduleAtFixedRate(scanner, 50, 60000, TimeUnit.MILLISECONDS);
		}
	}
	
	synchronized void removePattern(ArduinoPattern pattern) {
		if (!patterns.remove(pattern)) return;
		if (patterns.isEmpty() && future != null) {
			future.cancel(false);
			future = null;
		}
	}
	
	/**
	 * 
	 * This method is slightly cumbersome. To determine the arduino id of a given Port,
	 * we must distinguish three cases:
	 * 1) the id is known already, then we simply read it from the SErialListener
	 * 2) it is not known, but the serial listener for the port exists already. 
	 * 3) the port is completely new
	 * In the latter two cases we need to send a request to the arduino to identify itself,
	 * and wait for the response
	 * 
	 * @return
	 * 		Map<port, arduino id>
	 */
	public Map<String,String> getSerialPorts() {
			@SuppressWarnings("rawtypes")
			Enumeration portEnum;
			portEnum = CommPortIdentifier.getPortIdentifiers();
//			Map<String,Boolean> ports = new LinkedHashMap<>();
			final Map<String,String> ports = new LinkedHashMap<>();
			final List<SerialListener> pendingListeners = new ArrayList<>();
			List<String> newPorts = new ArrayList<>();
			// First, Find an instance of serial port as set in PORT_NAMES. 
			synchronized (serialListeners) { // we must not change the port listeners while checking the ports
				while (portEnum.hasMoreElements()) {
					CommPortIdentifier portID = (CommPortIdentifier) portEnum.nextElement();
					if (portID.getPortType() != CommPortIdentifier.PORT_SERIAL) continue;
//					if (idPortMap.values().contains(portID.getName())) continue; // we must not try to open thi
					String portName = portID.getName();
					logger.debug("Com port found: " + portName);
					SerialListener listener = serialListeners.get(portName);
					if (listener != null) {
						String arduinoId = listener.getArduinoId();
						if (arduinoId == null) {
							try {
								SerialPort sp = listener.serialPort;
								SerialWriter.write(sp, Constants.ID_REQUEST);
							} catch (Exception e) {
								continue;
							}
							pendingListeners.add(listener);  // we'll check later whether arduinoId has been set in the meantime
						}
						else {
							ports.put(portName, arduinoId);
						}
					}
					else {
						newPorts.add(portName);
					}
					
				}
				if (!newPorts.isEmpty()) {
					List<AuxiliaryPortListener> auxListeners = new ArrayList<>();
					final CountDownLatch latch = new CountDownLatch(newPorts.size());
					for (String pt : newPorts) {
						AuxiliaryPortListener auxListener =null;
						try {
							auxListener = new AuxiliaryPortListener(pt, latch);
							SerialWriter.write(auxListener.serialPort, Constants.ID_REQUEST);
							auxListeners.add(auxListener);
						} catch (Exception e) {
							try {
								if (auxListener != null)
									auxListener.close();
							} catch (Exception ee) {}
							logger.warn("Could not query com port for Arduino Id",e);
							latch.countDown();
							continue;
						}
					}
					// apparently, sending the request just once does not work... 
					Timer timer = null;
					try {
						timer = new Timer();
						IdRequester req = new IdRequester(auxListeners);
						timer.schedule(req, 1000,1000);
						latch.await(10000, TimeUnit.MILLISECONDS);
					} catch (InterruptedException e) {
						logger.warn("Unexpected interrupt exception ",e);
					} finally {
						if (timer != null)
							timer.cancel();
					}
					for (AuxiliaryPortListener auxListener : auxListeners) {
						
						try {
							auxListener.close();
						} catch (Exception e) {
							e.printStackTrace();
						}
						try {
							Thread.sleep(1000); // required to avoid timing issues with new port listener being creating 
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						String ardId = auxListener.arduinoId;
						if (ardId != null) {
							ports.put(auxListener.serialPort.getName(), ardId);
						}
					}
					if (!pendingListeners.isEmpty()) {
						final CountDownLatch latch2 = new CountDownLatch(pendingListeners.size());
						TimerTask task = new TimerTask() {
							
							@Override
							public void run() {
								Iterator<SerialListener> it = pendingListeners.iterator();
								while (it.hasNext()) {
									SerialListener sl = it.next();
									String id = sl.getArduinoId();
									if (id != null) {
										ports.put(sl.serialPort.getName(), id);
										latch2.countDown();
										it.remove();
									}
								}
							}
						};
						Timer timer2 = new Timer();
						timer2.schedule(task, 50, 100);
						try {
							latch2.await(2000, TimeUnit.MILLISECONDS);
						} catch (InterruptedException e) {
							logger.warn("Unexpeced interrupt exception ",e);
						} finally {
							timer2.cancel();
						}
					}
				}
			}
			logger.info("Port scanner identified the following Arduino ports: " + ports);
			return ports;	 
	}
	
	private class IdRequester extends TimerTask {
		
		private final List<AuxiliaryPortListener> auxListeners;
		
		public IdRequester(List<AuxiliaryPortListener> auxListeners) {
			this.auxListeners = auxListeners;
		}

		@Override
		public void run() {
			for (AuxiliaryPortListener al : auxListeners) {
				try {
					SerialWriter.write(al.serialPort, Constants.ID_REQUEST);
				} catch (Exception e) {
					logger.debug("Error sending repeated id request " + al.serialPort.getName());
				}
			}
		}
		
	}
	
	
	private class AuxiliaryPortListener implements SerialPortEventListener {
		
		private final SerialPort serialPort;
		private volatile String arduinoId = null;
		private final BufferedReader input;
		private final CountDownLatch latch;
		private boolean closed = false;
		
		public AuxiliaryPortListener(String portString, CountDownLatch latch) {
			this.latch = latch;
			try {
				// open serial port, and use class name for the appName.
				serialPort = (SerialPort) CommPortIdentifier.getPortIdentifier(portString).open(ArduinoConnector.class.getName(), SerialUtils.TIME_OUT);

				// open the streams
				input = new BufferedReader(new InputStreamReader(serialPort.getInputStream()));
//				output = serialPort.getOutputStream();

				// add event listeners
				serialPort.addEventListener(this);
				serialPort.notifyOnDataAvailable(true);
			} catch (Exception e) {
				try {
					close();
				} catch (Exception ee) {}
				throw new RuntimeException("Serial port listener could not be initialized. ",e);
			}
		}

		@Override
		public void serialEvent(SerialPortEvent oEvent) {
			 if (closed) return;
			logger.info("Serial Event !!!! " + oEvent.getEventType());
			if (oEvent.getEventType() == SerialPortEvent.DATA_AVAILABLE) {
				
				try {
					String inputLine = input.readLine();
					String[] components = inputLine.split("\\s", 2);
					if (components.length < 2) {
						return;
					}
					if (components[0].equals("id")) {
						this.arduinoId = components[1];
						logger.info("New Arduino id received: " + arduinoId);
						latch.countDown();
						close();
					}
					logger.trace("Serial input available from port " + serialPort.getName() + ": " + inputLine);
				} catch (Exception e) {
					logger.warn("Serial data could not be read: ",e); // happens quite often
				}
			}
		}
		
		public synchronized void close() {
			if (closed) return;
				closed = true;
			if (serialPort != null) {
				try {
					serialPort.removeEventListener();
				} catch (Exception e) {}
				try {
					serialPort.close();
				} catch (Exception e) {}
			}
			try {
				if (input != null)
					input.close();
			} catch (Exception e) {}
		}
		
	}
	
	synchronized void close() {
		if (future != null) {
			future.cancel(false);
			future = null;
		}
		patterns.clear();
	}
	
}
