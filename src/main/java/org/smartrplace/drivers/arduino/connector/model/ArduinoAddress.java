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
package org.smartrplace.drivers.arduino.connector.model;

import org.ogema.core.model.ModelModifiers.NonPersistent;
import org.ogema.core.model.simple.IntegerResource;
import org.ogema.core.model.simple.StringResource;
import org.ogema.model.communication.DeviceAddress;

public interface ArduinoAddress extends DeviceAddress {

	/**
	 * This is a custom id for the Arduino, meant to be more stable than the 
	 * serial port.
	 *  
	 * @return
	 */
	IntegerResource arduinoId();
	
	/**
	 * The serial port on which the Arduino is connected. Note that this is not stable;
	 * If the Arduino is disconnected and reconnected, it may be associated a different port.
	 * 
	 * Non-persistent: the port may change upon a restart of the system
	 * 
	 * @return
	 */
	@NonPersistent
	StringResource comPort();
	
	/**
	 * This is a freely chosen number, to distinguish different data points from the same Arduino.
	 * @return
	 */
	IntegerResource serialId();
	
}
