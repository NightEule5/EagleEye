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
package strixpyrr.eagleeye.data.storage

import okio.ByteString
import strixpyrr.abstrakt.collections.last
import strixpyrr.eagleeye.data.models.*
import strixpyrr.eagleeye.data.models.SymbolType.Spot
import strixpyrr.eagleeye.data.storage.models.StoredData
import uy.klutter.core.common.initializedWith
import uy.klutter.core.common.withNotNull
import java.time.Instant

// This code is a mess :/
fun construct(
	existing: Dataset?,
	symbol: String,
	baseAsset: String,
	quoteAsset: String,
	exchange: String,
	interval: Interval,
	marketData: List<MarketInstant>,
	startTime: Instant?,
	endTime: Instant?
): Dataset
{
	fun <K> MutableMap<K, Int>.ordered(): MutableMap<K, Int>
	{
		var last = -1
		
		// Check if the Map is already ordered.
		if (all { (_, v) -> (v > last).also { last = v } }) return this
		
		// Todo: Find a more efficient Map ordering system. From what I understand,
		//  this creates a List of Pairs, an Array, sorts the Array, then turns it
		//  back to a list.
		return LinkedHashMap<K, Int>(size) initializedWith
		{
			putAll(toList().sortedBy { (_, v) -> v })
		}
	}
	
	// Todo: Check if the term indexes are unique.
	
	// Construct the index.
	
	val currentIndex = existing withNotNull { index }
	
	// That's a lotta' null checks o_o
	val terms = currentIndex?.terms?.toMutableMap()?.ordered() ?: mutableMapOf()
	val symbols = currentIndex?.symbols?.toMutableMap() ?: mutableMapOf()
	
	val indexes = terms.values
	
	fun next() = indexes.last + 1
	
	val   symbolIndex = terms.getOrPut(symbol,     defaultValue = ::next)
	val     baseIndex = terms.getOrPut( baseAsset, defaultValue = ::next)
	val    quoteIndex = terms.getOrPut(quoteAsset, defaultValue = ::next)
	val exchangeIndex = terms.getOrPut(exchange,   defaultValue = ::next)
	
	val metadata = symbols[symbolIndex]
	
	if (metadata == null)
		symbols[symbolIndex] =
			UnifiedIndex.SymbolMetadata(quoteIndex, baseIndex, exchangeIndex)
	else if (metadata.type != Spot)
		throw UnsupportedOperationException(
			"The symbol type ${metadata.type} is not supported yet. Only $Spot " +
			"is supported currently."
		)
	else if (
		metadata.heldAsset != quoteIndex ||
		metadata.tradedAsset != baseIndex ||
		metadata.exchange != exchangeIndex
	)
	{
		// Overwrite an inconsistent metadata entry.
		symbols[symbolIndex] =
			metadata.copy(
				quoteIndex,
				baseIndex,
				exchangeIndex,
				type = Spot
			)
	}
	
	val index =
		UnifiedIndex(
			terms,
			symbols,
			unknownFields = currentIndex?.unknownFields ?: ByteString.EMPTY
		)
	
	// Construct the flow data.
	
	val symbolGroups = existing?.symbols?.toSortedMap() ?: mutableMapOf()
	
	val currentSymbol = symbolGroups[symbolIndex]
	val intervals = currentSymbol?.data_?.toMutableList() ?: mutableListOf()
	
	val currentFlow = intervals.find { it.interval == interval }
	
	if (currentFlow == null)
		// Todo: Verify that StartTime and EndTime encompass the MarketData.
		
		intervals +=
			MarketFlow(
				start = startTime,
				end = endTime,
				interval = interval,
				points = marketData
			)
	else
	{
		val currentStart = currentFlow.start
		val currentEnd = currentFlow.end
		
		// Todo: Merge the current data with the specified data. Right now we're
		//  blindly assuming the specified data come from the current data and was
		//  modified.
		
		if (currentStart != null && startTime != null)
			require(currentStart >= startTime)
			{
				"The StartTime given must widen or maintain the already existing" +
				" time range: the current start time ($currentStart) was less " +
				"than the specified StartTime ($startTime)."
			}
		
		if (currentEnd != null && endTime != null)
			require(currentEnd <= endTime)
			{
				"The EndTime given must widen or maintain the already existing " +
				"time range: the current end time ($currentEnd) was greater than" +
				" the specified EndTime ($endTime)."
			}
		
		intervals -= currentFlow
		intervals +=
			currentFlow.copy(
				start = startTime ?: currentStart,
				end = endTime ?: currentEnd,
				interval = interval
			)
	}
	
	symbolGroups[symbolIndex] =
		SymbolIntervalGroup(
			intervals,
			unknownFields = currentSymbol?.unknownFields ?: ByteString.EMPTY
		)
	
	return Dataset(
		index,
		symbolGroups,
		unknownFields = existing?.unknownFields ?: ByteString.EMPTY
	)
}

fun parseInterval(value: String): Interval
{
	var digitCount = 0
	
	for (c in value) if (c.isDigit()) digitCount++ else break
	
	check(digitCount > 0)
	
	val denomination =
		value.substring(digitCount)
			.run()
			{
				when
				{
					equals("SEC", ignoreCase = true) -> Interval.Denomination.Seconds
					equals("MIN", ignoreCase = true) -> Interval.Denomination.Minutes
					equals("HRS", ignoreCase = true) -> Interval.Denomination.Hours
					equals("DAY", ignoreCase = true) -> Interval.Denomination.Days
					equals("MTH", ignoreCase = true) -> Interval.Denomination.Months
					equals("YRS", ignoreCase = true) -> Interval.Denomination.Years
					else                             ->
						throw IllegalArgumentException(
							"The interval has an unknown denomination."
						)
				}
			}
	
	val length = value.substring(0 until digitCount).toInt() // [0,n)
	
	return Interval(denomination, length)
}

private fun StoredData?.constructStoredData(symbol: String, baseAsset: String, quoteAsset: String, exchange: String): StoredData
{
	fun <K> MutableMap<K, Int>.ordered(): MutableMap<K, Int>
	{
		var last = -1
		
		// Check if the Map is already ordered.
		if (all { (_, v) -> (v > last).also { last = v } }) return this
		
		// Todo: Find a more efficient Map ordering system. From what I understand,
		//  this creates a List of Pairs, an Array, sorts the Array, then turns it
		//  back to a list.
		return LinkedHashMap<K, Int>(size) initializedWith
		{
			putAll(toList().sortedBy { (_, v) -> v })
		}
	}
	
	// Todo: Check if the term indexes are unique.
	
	// Construct the index.
	
	val currentIndex = withNotNull { index }
	
	// That's a lotta' null checks o_o
	val terms   = currentIndex?.  terms?.toMutableMap()?.ordered() ?: mutableMapOf()
	val symbols = currentIndex?.symbols?.toMutableMap()            ?: mutableMapOf()
	
	val indexes = terms.values
	
	fun next() = indexes.last + 1
	
	val   symbolIndex = terms.getOrPut(symbol,     defaultValue = ::next)
	val     baseIndex = terms.getOrPut( baseAsset, defaultValue = ::next)
	val    quoteIndex = terms.getOrPut(quoteAsset, defaultValue = ::next)
	val exchangeIndex = terms.getOrPut(exchange,   defaultValue = ::next)
	
	val metadata = symbols[symbolIndex]
	
	if (metadata == null)
		symbols[symbolIndex] =
			UnifiedIndex.SymbolMetadata(quoteIndex, baseIndex, exchangeIndex)
	else if (metadata.type != Spot)
		throw UnsupportedOperationException(
			"The symbol type ${metadata.type} is not supported yet. Only $Spot " +
			"is supported currently."
		)
	else if (
		metadata.heldAsset != quoteIndex ||
		metadata.tradedAsset != baseIndex ||
		metadata.exchange != exchangeIndex
	)
	{
		// Overwrite an inconsistent metadata entry.
		symbols[symbolIndex] =
			metadata.copy(
				quoteIndex,
				baseIndex,
				exchangeIndex,
				type = Spot
			)
	}
	
	val index =
		UnifiedIndex(
			terms,
			symbols,
			unknownFields = currentIndex?.unknownFields ?: ByteString.EMPTY
		)
	
	// Todo: Construct the flow data.
}