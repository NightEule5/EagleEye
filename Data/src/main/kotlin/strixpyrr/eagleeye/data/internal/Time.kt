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
package strixpyrr.eagleeye.data.internal

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.TemporalUnit

internal fun Instant.toDateTime() = LocalDateTime.ofInstant(this, ZoneOffset.UTC)

internal fun LocalDateTime.toInstant() = toInstant(ZoneOffset.UTC)

internal fun Instant.add(count: Long, unit: TemporalUnit): Instant
{
	return toDateTime()
		.plus(count, unit)
		.toInstant()
}

internal class TimeRange(
	override val start: LocalDateTime,
	override val endInclusive: LocalDateTime
) : ClosedRange<LocalDateTime>
{
	
	fun toDuration(incrementCount: Long, unit: TemporalUnit): Duration =
		Duration.between(start, endInclusive.plus(incrementCount, unit))
	
	fun toOpen(incrementCount: Long, unit: TemporalUnit) =
		TimeRange(start.plus(incrementCount, unit), endInclusive.minus(incrementCount, unit))
	
	fun between(incrementCount: Long, unit: TemporalUnit): Iterator<LocalDateTime> =
		IncrementingIterator(incrementCount, unit)
	
	private inner class IncrementingIterator(
		private val incrementCount: Long,
		private val unit: TemporalUnit
	) : AbstractIterator<LocalDateTime>()
	{
		private var last = start
		
		override fun computeNext()
		{
			last = last.plus(incrementCount, unit)
			
			if (last >= endInclusive)
				done()
			else setNext(last)
		}
	}
}