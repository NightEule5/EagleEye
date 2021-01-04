// Copyright 2020 Strixpyrr
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package strixpyrr.eagleeye.data.models

import strixpyrr.abstrakt.collections.first
import strixpyrr.abstrakt.collections.last
import java.time.Instant

val MarketInstant           .isValid get() = time != null
val MarketInstantWithOutlook.isValid get() = time != null

@OptIn(ExperimentalUnsignedTypes::class)
val MarketFlow.isValid: Boolean get()
{
	//if (assetIndex == 0) return false
	
	val start = start ?: return false
	
	val end = end ?: return false
	
	val interval = interval ?: return false
	
	if (!interval.isValid) return false
	
	points.run()
	{
		if (isNotEmpty())
		{
			val firstTime: Instant
			
			first.run()
			{
				val time = time ?: return false
				
				if (time !in start..end) return false
				
				firstTime = time
			}
			
			if (size > 1)
				last.run()
				{
					val time = time ?: return false
					
					if (time !in start..end) return false
					
					if (time <= firstTime) return false
				}
		}
	}
	
	return true
}

val Interval.isValid: Boolean get() = length > 0