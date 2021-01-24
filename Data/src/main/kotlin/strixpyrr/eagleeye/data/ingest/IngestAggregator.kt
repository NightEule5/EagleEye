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

import kotlinx.coroutines.flow.Flow
import okio.BufferedSource
import java.time.LocalDateTime

interface IIngestAggregator
{
	suspend fun ingestHistorical(
		metadata: IIngestStreamMetadata.IHistorical,
		source: BufferedSource,
		keepOpen: Boolean = false,
		symbolFilter: (String) -> Boolean = AllSymbols,
		entryLimit: Int = NoLimit,
		timeRange: ClosedRange<LocalDateTime> = Unconstrained
	): Flow<TimestampedOhlcvWithSymbol>
	
	companion object
	{
		private val AllSymbols: (String) -> Boolean get() = { true }
		private val Unconstrained get() = LocalDateTime.MIN..LocalDateTime.MAX
		private const val NoLimit = Int.MAX_VALUE
	}
}

data class TimestampedOhlcvWithSymbol(
	val timestamp: LocalDateTime,
	val symbol: String,
	val open: Double,
	val high: Double,
	val low: Double,
	val close: Double,
	val volume: Double
) : Comparable<TimestampedOhlcvWithSymbol>
{
	// Sort by timestamp, then by symbol.
	
	override fun compareTo(other: TimestampedOhlcvWithSymbol): Int
	{
		var comparison = timestamp.compareTo(other.timestamp)
		
		if (comparison == 0)
			comparison = symbol.compareTo(other.symbol, ignoreCase = true)
		
		return comparison
	}
	
	// Values with the same timestamp and symbol are overwritten.
	
	override fun equals(other: Any?): Boolean
	{
		if (this === other) return true
		if (other !is TimestampedOhlcvWithSymbol) return false
		
		if (timestamp != other.timestamp) return false
		if (symbol != other.symbol) return false
		
		return true
	}
	
	override fun hashCode(): Int
	{
		var result = timestamp.hashCode()
		result = 31 * result + symbol.hashCode()
		return result
	}
}