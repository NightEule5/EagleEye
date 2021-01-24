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

import org.intellij.lang.annotations.Language
import strixpyrr.eagleeye.data.ingest.IIngestStreamMetadata.IHistorical

// I like this WAY better than how it was. Clear, concise, everything partitioned
// into separate operations. This will also make it much easier to ingest other
// data in the future, like order books.

interface IIngestStreamMetadata
{
	interface IHistorical : IIngestStreamMetadata
}

abstract class TabularStreamMetadata : IIngestStreamMetadata
{
	abstract val delimiter: Char
	abstract val timeFormat: TimestampFormat
	abstract val skippedRows: Int
	
	interface IColumnSpec
	{
		val isRegex: Boolean
		val regexOptions: Set<RegexOption>
		
		val nameSet: Set<String>
		val regexSet: Set<Regex>
	}
	
	data class Historical(
		val columns: ColumnSpec,
		override val delimiter: Char,
		override val timeFormat: TimestampFormat,
		override val skippedRows: Int = 0
	) : IHistorical, TabularStreamMetadata()
	{
		data class ColumnSpec(
			@Language("regexp") val     symbol: String,
			@Language("regexp") val       time: String,
			@Language("regexp") val  openPrice: String,
			@Language("regexp") val  highPrice: String,
			@Language("regexp") val   lowPrice: String,
			@Language("regexp") val closePrice: String,
			@Language("regexp") val     volume: String,
			override val isRegex: Boolean,
			override val regexOptions: Set<RegexOption> = emptySet()
		) : IColumnSpec
		{
			override val nameSet get() =
				setOf(
					symbol,
					time,
					openPrice,
					highPrice,
					lowPrice,
					closePrice,
					volume
				)
			
			override val regexSet get() =
				setOf(
					Regex(symbol,     regexOptions),
					Regex(time,       regexOptions),
					Regex(openPrice,  regexOptions),
					Regex(highPrice,  regexOptions),
					Regex(lowPrice,   regexOptions),
					Regex(closePrice, regexOptions),
					Regex(volume,     regexOptions),
				)
		}
	}
}