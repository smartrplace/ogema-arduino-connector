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
package org.smartrplace.drivers.arduino.connector.pattern;

import org.ogema.core.model.Resource;
import org.ogema.core.model.simple.BooleanResource;
import org.ogema.core.model.simple.IntegerResource;
import org.ogema.core.model.simple.StringResource;
import org.ogema.core.resourcemanager.pattern.ResourcePattern;
import org.smartrplace.drivers.arduino.connector.model.ArduinoAddress;
import org.smartrplace.drivers.arduino.connector.model.ArduinoCommunicationInformation;

public class ArduinoPattern extends ResourcePattern<ArduinoCommunicationInformation> {

	public ArduinoPattern(Resource match) {
		super(match);
	}

	public final ArduinoAddress address = model.comAddress();
	
	@Existence(required=CreateMode.OPTIONAL)
	public final IntegerResource arduinoId = address.arduinoId();
	
	@Existence(required=CreateMode.OPTIONAL)
	public final StringResource comPort = address.comPort();
	
	/**
	 * If not present, all values will be associated to the resource
	 */
//	@Existence(required=CreateMode.OPTIONAL)
	public final IntegerResource serialId = address.serialId();

	public final BooleanResource readable = address.readable();
	
	public final BooleanResource writable = address.writeable();
	
	@Override
	public boolean accept() {
		return arduinoId.isActive() || comPort.isActive();
	}
	
}
