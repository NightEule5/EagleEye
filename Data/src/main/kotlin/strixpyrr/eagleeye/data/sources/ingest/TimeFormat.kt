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
package strixpyrr.eagleeye.data.sources.ingest

import strixpyrr.abstrakt.annotations.InlineOnly
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Suppress("HasPlatformType")
sealed class TimeFormat
{
	@InlineOnly
	inline operator fun invoke(value: String) = parse(value)
	
	abstract fun parse(value: String): LocalDateTime
	
	object UnixEpochMilli : TimeFormat()
	{
		override fun parse(value: String): LocalDateTime
		{
			val instant = Instant.ofEpochMilli(value.toLong())
			
			return LocalDateTime.ofInstant(instant, ZoneOffset.UTC)
		}
	}
	
	object Iso8601LocalDateTime : TimeFormat()
	{
		override fun parse(value: String) =
			LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
	}
}