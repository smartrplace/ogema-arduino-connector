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

import java.util.ArrayList;
import java.util.List;

import org.ogema.core.channelmanager.measurements.FloatValue;
import org.ogema.core.channelmanager.measurements.Quality;
import org.ogema.core.channelmanager.measurements.SampledValue;

public class ValueUtil {
	
	static SampledValue getSampledValue(String string, long[] synchronisation) {
		if (!string.startsWith("t:")) 
			throw new IllegalArgumentException("String format not valid... must start with \"t: \", followed by the time in Unix ms");
		String[] pair = string.split(";_v:");
		if (pair.length != 2) 
			throw new IllegalArgumentException("Unexpected string " + string);
		String t = pair[0];
		long ts  = Long.parseLong(t.substring(2)) + synchronisation[1] - synchronisation[0];
		String v = pair[1];
		float value = Float.parseFloat(v);
		return new SampledValue(new FloatValue(value), ts, Quality.GOOD);
	}

	// assume string of the form "t:timestamp;_v:value t:timestamp2;_v:value2 t:...,
	// where timestamp is a long value and value a float
	static List<SampledValue> getSampledValues(String string, long[] synchronisation) {
		if (synchronisation.length != 2) 
			throw new IllegalArgumentException("Expected two synchro values, got " + synchronisation.length);
		List<SampledValue> values = new ArrayList<>();
		String[] components = string.split(" ");
		for (String cmp : components) {
			values.add(getSampledValue(cmp,synchronisation));
		}
		return values;
	}
	
	
}
