// Copyright 2021 Strixpyrr
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
package strixpyrr.eagleeye.data.ingest

import strixpyrr.abstrakt.annotations.InlineOnly
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

abstract class TimestampFormat
{
	@InlineOnly
	inline operator fun invoke(value: String) = parse(value)
	
	abstract fun parse(value: String): LocalDateTime
	
	object UnixEpoch : TimestampFormat()
	{
		override fun parse(value: String): LocalDateTime
		{
			val ticks = value.toLong()
			
			// Unfortunately, sources that use Unix timestamps are inconsistent on
			// their usage of Epoch Seconds and Epoch Milliseconds. We'll need to
			// infer which it is and offset accordingly.
			// Currently, in seconds the Unix time is 10 digits long. If the time
			// given is 13 digits long, we can therefore assume it was given in ms.
			// Of course, this will break in roughly 265 years, but who would still
			// be using this? I like to think we'll all have our minds uploaded long
			// before then.
			
			return when
			{
				ticks < 10_000_000_000L     -> getMilliseconds(ticks * 1000)
				ticks < 10_000_000_000_000L -> getMilliseconds(ticks       )
				else                        ->
					throw IllegalArgumentException("You're too far in the future! D:")
			}
		}
		
		private fun getMilliseconds(value: Long): LocalDateTime
		{
			val instant = Instant.ofEpochMilli(value)
			
			return LocalDateTime.ofInstant(instant, ZoneOffset.UTC)
		}
	}
	
	@Suppress("HasPlatformType")
	object Iso8601 : TimestampFormat()
	{
		override fun parse(value: String) =
			LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
	}
}