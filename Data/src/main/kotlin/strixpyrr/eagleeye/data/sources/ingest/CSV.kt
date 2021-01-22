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

import com.github.doyaaaaaken.kotlincsv.client.CsvFileReader
import com.github.doyaaaaaken.kotlincsv.client.CsvReader
import com.github.doyaaaaaken.kotlincsv.dsl.context.CsvReaderContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import strixpyrr.abstrakt.annotations.InlineOnly
import uy.klutter.core.common.with
import java.io.InputStream
import java.time.Instant

suspend fun ingestCsv(
	stream: InputStream,
	map: MutableMap<Instant, in Ohlcv>,
	parameters: CsvParameters
) = withContext(Dispatchers.IO)
	{
		CsvReader(ctx = parameters.context).openAsync(stream)
		{
		
		}
	}

data class CsvParameters(
	val source: Source = Source.Unknown,
	val delimiterType: DelimiterType = DelimiterType.Auto
)
{
	internal val context get() = CsvReaderContext() with
	{
		when (delimiterType)
		{
			DelimiterType.Auto      -> when (source)
			{
				Source.Unknown            -> { }
				Source.CryptoTick         -> delimiter = ';'
				Source.CryptoDataDownload -> delimiter = ','
			}
			DelimiterType.Comma     -> delimiter = ','
			DelimiterType.Tab       -> delimiter = '\t'
			DelimiterType.Semicolon -> delimiter = ';'
		}
	}
	
	enum class Source { Unknown, CryptoTick, CryptoDataDownload }
	
	enum class DelimiterType { Auto, Comma, Tab, Semicolon }
}

suspend fun CsvReaderContext.ingestCsv(
	stream: InputStream,
	map: MutableMap<Instant, in Ohlcv>,
	settings: CsvSettings = CsvSettings.ccdSettings
)
{
	CsvReader(ctx = this).openAsync(stream)
	{
		val (skippedLines,
			timeColumn,
			openColumn,
			highColumn,
			lowColumn,
			closeColumn,
			volumeColumn,
			parseTime) = settings
		
		val flow = readAllAsFlow(skip = skippedLines)
		
		flow.buffer(capacity = 256)
			.collect()
			{ value ->
				val time   = parseTime(value[  timeColumn])
				val open   =           value[  openColumn]
				val high   =           value[  highColumn]
				val low    =           value[   lowColumn]
				val close  =           value[ closeColumn]
				val volume =           value[volumeColumn]
				
				if (time !in map)
					map[time] = Ohlcv(
						open  .toDouble(),
						high  .toDouble(),
						low   .toDouble(),
						close .toDouble(),
						volume.toDouble()
					)
			}
	}
}

data class CsvSettings(
	val skippedLines: Int,
	val timeColumn: Int,
	val openColumn: Int,
	val highColumn: Int,
	val lowColumn: Int,
	val closeColumn: Int,
	val volumeColumn: Int,
	val timeParser: CsvTimeParser
)
{
	companion object
	{
		val ccdSettings
			get() =
				CsvSettings(
					skippedLines = 2,
					timeColumn   = 0,
					openColumn   = 3,
					highColumn   = 4,
					lowColumn    = 5,
					closeColumn  = 6,
					volumeColumn = 7,
					timeParser   = CsvTimeParser.UnixEpoch
				)
	}
}

sealed class CsvTimeParser
{
	@InlineOnly
	inline operator fun invoke(value: String) = parse(value)
	
	abstract fun parse(value: String): Instant
	
	object UnixEpoch : CsvTimeParser()
	{
		@Suppress("HasPlatformType")
		override fun parse(value: String) = Instant.ofEpochMilli(value.toLong())
	}
}

// KotlinCSV Extensions

private fun CsvFileReader.readAllAsFlow(skip: Int = 0) =
	flow()
	{
		for (i in 0 until skip) readNext()
		
		var cur: List<String>?
		
		// Have I expressed how much I fucking hate this syntax limitation? I hate
		// having to use silly workarounds like this or the godawful Also function
		// assignment. Just make assignments expressions!
		//      while ((cur = readNext()) != null) emit(cur)
		// ...Is much easier to understand than this mess.
		// Seems I'm still having C# withdrawals...
		while (true)
		{
			cur = readNext()
			
			emit(cur ?: break)
		}
	}