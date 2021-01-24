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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import okio.BufferedSource
import strixpyrr.eagleeye.data.ingest.IIngestStreamMetadata.IHistorical
import strixpyrr.eagleeye.data.ingest.TabularStreamMetadata.Historical
import strixpyrr.eagleeye.data.ingest.csv.ColumnSkippingRowReader
import strixpyrr.eagleeye.data.ingest.csv.FieldReader
import strixpyrr.eagleeye.data.ingest.csv.openCsv
import strixpyrr.eagleeye.data.internal.EnumSet
import java.nio.file.Path
import java.time.LocalDateTime
import kotlin.internal.InlineOnly
import kotlin.text.RegexOption.IGNORE_CASE

internal open class TabularIngestAggregator : IIngestAggregator
{
	protected fun BufferedSource.csv(
		metadata: TabularStreamMetadata,
		keepOpen: Boolean
	) = openCsv(metadata.delimiter, keepOpen)
	
	protected fun Path.csv(
		metadata: TabularStreamMetadata
	) = openCsv(metadata.delimiter)
	
	override suspend fun ingestHistorical(
		metadata: IHistorical,
		source: BufferedSource,
		keepOpen: Boolean,
		symbolFilter: (String) -> Boolean,
		entryLimit: Int,
		timeRange: ClosedRange<LocalDateTime>
	) = if (metadata is Historical)
			ingestHistorical(
				metadata,
				source,
				keepOpen,
				symbolFilter,
				entryLimit,
				timeRange
			)
		else throw UnsupportedOperationException()
	
	protected open suspend fun ingestHistorical(
		metadata: Historical,
		source: BufferedSource,
		keepOpen: Boolean,
		symbolFilter: (String) -> Boolean,
		limit: Int,
		timeRange: ClosedRange<LocalDateTime>
	) = ingestHistorical(source, keepOpen, { TODO() }, "", timeRange)
			.take(count = limit)
	
	companion object
	{
		@Suppress("BlockingMethodInNonBlockingContext")
		protected suspend fun ingestHistorical(
			source: BufferedSource,
			keepOpen: Boolean,
			isSymbolIncluded: (String) -> Boolean,
			quoteAsset: String, // Temporary
			timeRange: ClosedRange<LocalDateTime>
		): Flow<TimestampedOhlcvWithSymbol>
		{
			val peek = source.peek()
			
			// Infer the source. CryptoDataDownload puts their Url at the top, so
			// that can be used to identify them.
			val isCdd =
				peek.readUtf8LineStrict()
					.contains(
						"https://www.CryptoDataDownload.com"
					)
			
			return if (isCdd)
				ingestCddHistorical(
					source,
					keepOpen,
					isSymbolIncluded,
					quoteAsset,
					TimestampFormat.UnixEpoch,
					timeRange
				)
			else ingestCryptoTickHistorical(
					source,
					keepOpen,
					isSymbolIncluded,
					TimestampFormat.Iso8601,
					timeRange
				)
		}
		
		protected suspend fun ingestCddHistorical(
			source: BufferedSource,
			keepOpen: Boolean,
			isSymbolIncluded: (String) -> Boolean,
			baseAsset: String, // Temporary
			parseTime: TimestampFormat,
			timeRange: ClosedRange<LocalDateTime>
		): Flow<TimestampedOhlcvWithSymbol>
		{
			val csv = ColumnSkippingRowReader(
				FieldReader(
					source, delimiter = ',', keepOpen, autoClose = true
				),
				skippedColumns = mutableSetOf()
			)
			
			csv.scope.run()
			{
				// Skip the source Url.
				skipToEndNormally()
				
				// Select the Unix Timestamp, Symbol, price, and Volume columns.
				
				// Todo: Would adding the asset to the Regex like this mess with
				//  pattern compilation?
				// https://regex101.com/r/QMKzU1/1
				val columnRegex =
					Regex(
						"(unix( timestamp)?)|symbol|open|high|low|close|(volume( $baseAsset)?)",
						options = EnumSet(IGNORE_CASE)
					)
				
				csv.constrain(columnRegex)
				
				return flow()
				{
					while (hasMoreRows)
					{
						startNextRow()
						
						val time = parseTime(readStrict())
						
						// If time is within the constrained range, continue with
						// the rest of the row.
						if (time !in timeRange) continue
						
						val symbol = readStrict()
						
						// If the symbol is included, continue.
						if (isSymbolIncluded(symbol)) continue
						
						// Read the rest of the row.
						emit(
							TimestampedOhlcvWithSymbol(
								time,
								symbol,
								open   = readStrict().toDouble(),
								high   = readStrict().toDouble(),
								low    = readStrict().toDouble(),
								close  = readStrict().toDouble(),
								volume = readStrict().toDouble()
							)
						)
					}
				}
			}
		}
		
		protected suspend fun ingestCryptoTickHistorical(
			source: BufferedSource,
			keepOpen: Boolean,
			isSymbolIncluded: (String) -> Boolean,
			parseTime: TimestampFormat,
			timeRange: ClosedRange<LocalDateTime>
		): Flow<TimestampedOhlcvWithSymbol>
		{
			// This ingest implementation is much easier, because CryptoTick has a
			// consistent structure (being a service that you actually pay for). We
			// can therefore hardcode the columns.
			
			val csv = ColumnSkippingRowReader(
				FieldReader(
					source, delimiter = ';', keepOpen, autoClose = true
				),
				skippedColumns =
					mutableSetOf(
						2, 3, 4, // Unneeded time columns
						9        // Trade count column
					)
			)
			
			csv.scope()
			{
				return flow()
				{
					while (hasMoreRows)
					{
						startNextRow()
						
						val symbol = readStrict()
						
						// If the symbol is included, continue.
						if (isSymbolIncluded(symbol)) continue
						
						val time = parseTime(readStrict())
						
						// If time is within the constrained range, continue with
						// the rest of the row.
						if (time !in timeRange) continue
						
						// Read the rest of the row.
						emit(
							TimestampedOhlcvWithSymbol(
								time,
								symbol,
								open   = readStrict().toDouble(),
								high   = readStrict().toDouble(),
								low    = readStrict().toDouble(),
								close  = readStrict().toDouble(),
								volume = readStrict().toDouble()
							)
						)
					}
				}
			}
		}
	}
}

@InlineOnly
private inline operator fun <T> List<T>.component6() = get(5)
@InlineOnly
private inline operator fun <T> List<T>.component7() = get(6)