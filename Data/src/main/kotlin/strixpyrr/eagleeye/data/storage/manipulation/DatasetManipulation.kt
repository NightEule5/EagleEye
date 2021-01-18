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
package strixpyrr.eagleeye.data.storage.manipulation

import com.squareup.wire.Message
import okio.ByteString
import strixpyrr.abstrakt.annotations.InlineOnly
import strixpyrr.abstrakt.collections.first
import strixpyrr.abstrakt.collections.last
import strixpyrr.eagleeye.data.internal.toDateTime
import strixpyrr.eagleeye.data.internal.toInstant
import strixpyrr.eagleeye.data.models.*
import strixpyrr.eagleeye.data.models.UnifiedIndex.SymbolMetadata
import strixpyrr.eagleeye.data.sources.CoinApiSource.Companion.coinApiNotation
import strixpyrr.eagleeye.data.warnVerbose
import uy.klutter.core.common.initializedWith
import uy.klutter.core.common.withNotNull
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.TreeMap
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

// This is messier than the stuff it was meant to replace, but at least using scopes
// and closures might make what's going on easier to understand.

@InlineOnly
@OptIn(ExperimentalContracts::class)
internal inline infix fun Dataset?.modifiedBy(modify: DatasetScope.() -> Unit): Dataset
{
	contract {
		callsInPlace(modify, kind = InvocationKind.EXACTLY_ONCE)
	}
	
	return (DatasetScope(existing = this) initializedWith modify).toDataset()
}

// Creation of this class may or may not be optimized away by Hotspot (via Scalar
// Replacement?), since there is no escape of this value. Yes, I worry needlessly
// over these things. Don't @ me.
internal class DatasetScope(private val existing: Dataset?)
{
	// Index
	
	private val currentIndex = existing?.index
	
	private val termMap = currentIndex?.terms?.sortToMutableMap() ?: mutableMapOf()
	
	private val symbolMetadataMap = currentIndex?.symbols?.toMutableMap() ?: mutableMapOf()
	
	private val usedIndexes = termMap.values
	
	private val nextIndex get() = if (usedIndexes.isEmpty()) 0 else usedIndexes.last + 1
	
	// Data
	
	private val symbols = existing?.symbols?.toMutableMap() ?: mutableMapOf()
	
	fun hasSymbol(symbol: String) = symbol in termMap
	fun hasQuote(quote: String) = quote in termMap
	fun hasBase(base: String) = base in termMap
	
	@OptIn(ExperimentalContracts::class)
	inline fun onSymbol(
		symbol: String,
		base: String,
		quote: String,
		exchange: String,
		modify: SymbolScope.() -> Unit
	)
	{
		contract {
			callsInPlace(modify, InvocationKind.EXACTLY_ONCE)
		}
		
		val symbolIndex = populateSymbolMetadata(symbol, base, quote, exchange)
		
		val intervalGroup =
			(SymbolScope(symbol, getIntervalGroup(symbolIndex)) initializedWith modify).toIntervalGroup()
		
		setIntervalGroup(symbolIndex, intervalGroup)
	}
	
	@PublishedApi
	internal fun populateSymbolMetadata(
		symbol: String,
		base: String,
		quote: String,
		exchange: String
	): Int
	{
		val   symbolIndex = termMap.getOrPut(symbol,   defaultValue = ::nextIndex)
		val     baseIndex = termMap.getOrPut(base,     defaultValue = ::nextIndex)
		val    quoteIndex = termMap.getOrPut(quote,    defaultValue = ::nextIndex)
		val exchangeIndex = termMap.getOrPut(exchange, defaultValue = ::nextIndex)
		
		val metadata = symbolMetadataMap[symbolIndex]
		
		if (metadata == null)
		{
			symbolMetadataMap[symbolIndex] =
				SymbolMetadata(quoteIndex, baseIndex, exchangeIndex)
		}
		else if (
			metadata.heldAsset != quoteIndex ||
			metadata.tradedAsset != baseIndex ||
			metadata.exchange != exchangeIndex ||
			metadata.type != SymbolType.Spot
		)
		{
			warnVerbose()
			{
				"The current symbol $symbol already has existing metadata which" +
				" is inconsistent with it. This metadata will be overwritten to" +
				" correct this discrepancy."
			}
			
			symbolMetadataMap[symbolIndex] =
				metadata.copy(quoteIndex, baseIndex, exchangeIndex, SymbolType.Spot)
		}
		
		return symbolIndex
	}
	
	@PublishedApi
	internal fun getIntervalGroup(symbolIndex: Int) = symbols[symbolIndex]
	
	@PublishedApi
	internal fun setIntervalGroup(
		symbolIndex: Int,
		group: SymbolIntervalGroup
	) = symbols.put(symbolIndex, group)
	
	class SymbolScope(
		@JvmField internal val symbol: String, // For error messages.
		private val intervalGroup: SymbolIntervalGroup?
	)
	{
		private val data = intervalGroup?.data_?.toMutableList() ?: mutableListOf()
		
		@OptIn(ExperimentalContracts::class)
		inline fun onInterval(interval: Interval, modify: IntervalScope.() -> Unit)
		{
			contract {
				callsInPlace(modify, InvocationKind.EXACTLY_ONCE)
			}
			
			val flow = getFlow(interval)
			
			val scope =
				IntervalScope(
					symbol,
					interval,
					flow
				).apply(modify)
			
			setFlow(flow, scope.toFlow())
		}
		
		inline fun forEachInterval(action: IntervalScope.(MutableIterator<IntervalScope>) -> Unit) =
			intervalIterator().run { forEach { it.action(this) } }
		
		fun intervalIterator(): MutableIterator<IntervalScope> = IntervalIterator(data)
		
		@PublishedApi
		internal fun getFlow(interval: Interval) = data.find { it.interval == interval }
		
		@PublishedApi
		internal fun setFlow(old: MarketFlow?, new: MarketFlow)
		{
			if (old != null) data -= old
			
			data += new
		}
		
		@PublishedApi
		internal fun toIntervalGroup() =
			SymbolIntervalGroup(
				data,
				unknownFields = intervalGroup.carryUnknown()
			)
		
		private inner class IntervalIterator(
			data: MutableList<MarketFlow>
		) : AbstractIterator<IntervalScope>(), MutableIterator<IntervalScope>
		{
			private val data = data.iterator()
			
			override fun computeNext()
			{
				if (!data.hasNext()) { done(); return }
				
				val flow = data.next()
				setNext(IntervalScope(symbol, flow.interval!!, flow))
			}
			
			override fun remove() = data.remove()
		}
	}
	
	class IntervalScope(
		symbol: String,
		@JvmField internal val interval: Interval,
		private val flow: MarketFlow?
	)
	{
		// Note that the Instant-LocalDateTime conversion must be done using the
		// UTC zone offset, since that's what CoinApi uses.
		// We're using LocalDateTime as a key here to support adding Months and
		// Years.
		val points =
			TreeMap<LocalDateTime, MarketInstant>() initializedWith
			{
				flow?.points?.forEach()
				{
					val time = it.time
					
					if (time == null)
						warnVerbose()
						{
							val interval = interval.coinApiNotation
							
							"A data point for the current symbol ($symbol) under" +
							" the interval $interval has no time value, so it" +
							"cannot be included in the output data."
						}
					else put(time.toDateTime(), it)
				}
			}
		
		private val existingStartTime: Instant?
		private val existingEndTime:   Instant?
		
		init
		{
			val flow = flow
			
			if (flow != null)
			{
				val start = flow.start
				val end   = flow.end
				
				if (start != null && end != null && start > end)
				{
					warnVerbose()
					{
						"The existing start and end times ($start and $end) are" +
						" invalid: the start time comes after the end time. To " +
						"correct this, these will be swapped."
					}
					
					existingStartTime = end
					existingEndTime = start
				}
				else
				{
					existingStartTime = start
					existingEndTime   = end
				}
			}
			else
			{
				existingStartTime = null
				existingEndTime   = null
			}
		}
		
		var startTime: Instant? = null
		var endTime: Instant? = null
		
		fun getNextMissingRange() = points.getNextMissingRange()
		
		fun getNextMissingRange(from: LocalDateTime = LocalDateTime.MIN, to: LocalDateTime = LocalDateTime.MAX) =
			points.subMap(from, true, to, true)!!.getNextMissingRange()
		
		private fun Map<LocalDateTime, MarketInstant>.getNextMissingRange(): ClosedRange<LocalDateTime>?
		{
			val values = keys.iterator()
			
			if (!values.hasNext()) return null
			
			val interval = interval
			val count: Long = interval.length.toLong()
			val unit = interval.denomination.temporalUnit
			
			var last = values.next()
			
			for (value in values)
			{
				val nextIncrement = last.plus(count, unit)
				
				if (value > nextIncrement)
				{
					// We've found a discontinuity in the timestamps.
					return nextIncrement..value.minus(count, unit)
				}
				
				last = value
			}
			
			// We've hit the end of the data with no discontinuous timestamps.
			return null
		}
		
		@PublishedApi
		internal fun toFlow(): MarketFlow
		{
			val start = startTime ?: existingStartTime
			val end   =   endTime ?: existingEndTime
			
			// Check that the start and end aren't swapped.
			if (start != null && end != null)
			{
				require(start <= end)
				{
					"The specified start and end times ($start and $end) are in" +
					"valid: the start time comes after the end time."
				}
			}
			
			val times = points.keys
			
			// Check that the entries are within the start-end range.
			if (times.isNotEmpty())
			{
				val first = times.first.toInstant()
				val last  = times.last.toInstant()
				
				if (start != null)
				{
					require(start <= first)
					{
						"The start time restricts the point set: the timestamp " +
						"of the first point ($first) represents a time before "  +
						"the start time ($start)."
					}
				}
				
				if (end != null)
				{
					require(end >= last)
					{
						"The end time restricts the point set: the timestamp of" +
						" the last point ($last) represents a time after the end" +
						" time ($end)."
					}
				}
			}
			
			return MarketFlow(
				start, end, interval, points.values.toList(), unknownFields = flow.carryUnknown()
			)
		}
	}
	
	inline fun forEachSymbol(
		action: SymbolScope.(MutableIterator<SymbolScope>) -> Unit
	) = symbolIterator().run { forEach { it.action(this) } }
	
	fun symbolIterator(): MutableIterator<SymbolScope> = SymbolIterator(symbolMetadataMap, termMap, symbols)
	
	private class SymbolIterator(
		private val metadata: MutableMap<Int, SymbolMetadata>,
		private val terms: MutableMap<String, Int>,
		private val symbols: MutableMap<Int, SymbolIntervalGroup>
	) : AbstractIterator<SymbolScope>(), MutableIterator<SymbolScope>
	{
		private val indices = symbols.keys.iterator()
		private var cur = -1
		
		override fun computeNext()
		{
			if (!indices.hasNext()) { done(); return }
			
			cur = indices.next()
			setNext(
				SymbolScope(getId(cur), symbols[cur])
			)
		}
		
		private fun getId(i: Int): String
		{
			for ((key, value) in terms)
				if (value == i)
					return key
			
			throw NoSuchElementException()
		}
		
		override fun remove()
		{
			val terms = terms.values
			val symbol = metadata[cur]!!
			
			// Remove the symbol.
			indices.remove()
			metadata -= cur
			terms    -= cur
			
			// Safely remove the asset and exchange indices from metadata.
			val held   = symbol.heldAsset
			val traded = symbol.tradedAsset
			
			val exchange = symbol.exchange
			
			val symbols = metadata.values
			
			if (symbols.none { it.heldAsset == held })
				terms -= held
			
			if (symbols.none { it.tradedAsset == traded })
				terms -= traded
			
			if (symbols.none { it.exchange == exchange })
				terms -= exchange
		}
	}
	
	fun toDataset() =
		Dataset(
			index = UnifiedIndex(
				termMap,
				symbolMetadataMap,
				unknownFields = currentIndex.carryUnknown()
			),
			symbols,
			unknownFields = existing.carryUnknown()
		)
}

private val Collection<Int>.max: Int get()
{
	if (isEmpty()) return -1
	
	val values = iterator()
	
	var max = values.next()
	
	forEach { cur -> if (max < cur) max = cur }
	
	return max
}

private fun <K> Map<K, Int>.sortToMutableMap(): MutableMap<K, Int>
{
	var last = -1
	
	// Check if the Map is already ordered.
	if (
		size <= 1 ||
		all()
		{ (_, v) ->
			(v > last).also { last = v }
		}
	) return toMutableMap()
	
	// Todo: This way of sorting could be improved.
	
	return map { it.toPair() }
		.sortedBy { it.second }
		.toMap(LinkedHashMap())
}

private fun Message<*, *>?.carryUnknown() =
	withNotNull { unknownFields } ?: ByteString.EMPTY

internal val Interval.Denomination.temporalUnit get() =
	when (this)
	{
		Interval.Denomination.Seconds -> ChronoUnit.SECONDS
		Interval.Denomination.Minutes -> ChronoUnit.MINUTES
		Interval.Denomination.Hours   -> ChronoUnit.HOURS
		Interval.Denomination.Days    -> ChronoUnit.DAYS
		Interval.Denomination.Months  -> ChronoUnit.MONTHS
		Interval.Denomination.Years   -> ChronoUnit.YEARS
	}