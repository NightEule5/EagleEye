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

import strixpyrr.eagleeye.data.sources.ingest.ITabularStreamMetadata.IColumnNames
import strixpyrr.eagleeye.data.sources.ingest.StreamMetadataPresets.AutoDetectDelimiter
import strixpyrr.eagleeye.data.sources.ingest.TabularStreamMetadata.ColumnNames

// Contains metadata classes that provide information to facilitate ingesting data
// such as column information, formats, etc.

interface IIngestStreamMetadata

interface ITabularStreamMetadata : IIngestStreamMetadata
{
	interface IColumnNames
	{
		val     symbol: String
		val       time: String
		val  openPrice: String
		val  highPrice: String
		val   lowPrice: String
		val closePrice: String
		val     volume: String
		
		operator fun component1() =     symbol
		operator fun component2() =       time
		operator fun component3() =  openPrice
		operator fun component4() =  highPrice
		operator fun component5() =   lowPrice
		operator fun component6() = closePrice
		operator fun component7() =     volume
	}
	
	val columnNames: IColumnNames
	val timeFormat : TimeFormat
	val delimiter  : Char
	val escape     : Char
	
	operator fun component1() = columnNames
	operator fun component2() = timeFormat
	operator fun component3() = delimiter
	operator fun component4() = escape
}

data class TabularStreamMetadata(
	override val columnNames: ColumnNames,
	override val timeFormat : TimeFormat,
	override val delimiter  : Char = AutoDetectDelimiter,
	override val escape     : Char = '"'
) : ITabularStreamMetadata
{
	data class ColumnNames(
		override val symbol    : String,
		override val time      : String,
		override val  openPrice: String,
		override val  highPrice: String,
		override val   lowPrice: String,
		override val closePrice: String,
		override val volume    : String
	) : IColumnNames
}

object StreamMetadataPresets
{
	const val AutoDetectDelimiter = Char.MIN_VALUE
	const val DisabledEscape      = Char.MIN_VALUE
	
	// Todo: CryptoDataDownload has some inconsistencies that make it very difficult
	//  to write out static rules for it. The Unix timestamp is in milliseconds in
	//  some datasets, seconds in others; the columns vary from title case to lower
	//  case, sometimes even within the same dataset; the volume column names often
	//  have the symbol in them, but not always; and the symbol format isn't constant.
	//  There's also a source URL at the top. Suffice it to say I don't want to deal
	//  with this mess right now.
	/*
	val CryptoDataDownload get() =
		TabularStreamMetadata(
			ColumnNames(
				symbol = "Symbol",
				time = "Unix Timestamp",
				openPrice = "Open",
				highPrice = "High",
				lowPrice = "Low",
				closePrice = "Close",
				volume = "Volume",
			),
			timeFormat = TimeFormat.UnixEpochMilli,
			delimiter = ',',
			escape = DisabledEscape
		)
	 */
	
	val CryptoTick get() =
		TabularStreamMetadata(
			ColumnNames(
				symbol = "symbol_id",
				time = "time_period_start",
				openPrice = "px_open",
				highPrice = "px_high",
				lowPrice = "px_low",
				closePrice = "px_close",
				volume = "sx_sum"
			),
			timeFormat = TimeFormat.Iso8601LocalDateTime,
			delimiter = ';',
			escape = DisabledEscape
		)
}
