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

import com.github.doyaaaaaken.kotlincsv.client.CsvReader
import com.github.doyaaaaaken.kotlincsv.dsl.context.CsvReaderContext
import strixpyrr.eagleeye.data.sources.ingest.StreamMetadataPresets.AutoDetectDelimiter
import strixpyrr.eagleeye.data.sources.ingest.csv.CsvParser
import uy.klutter.core.common.with
import java.io.Reader
import java.time.LocalDateTime

open class TabularIngestAggregator : IngestAggregator()
{
	override suspend fun ingest(
		metadata: IIngestStreamMetadata,
		reader: Reader,
		map: MutableMap<LocalDateTime, in Ohlcv>,
		keepOpen: Boolean
	) = if (metadata is ITabularStreamMetadata)
			ingest(metadata, reader, map, keepOpen)
		else throw UnsupportedOperationException()
	
	protected open suspend fun ingest(
		metadata: ITabularStreamMetadata,
		reader: Reader,
		map: MutableMap<LocalDateTime, in Ohlcv>,
		keepOpen: Boolean
	)
	{
		val (columns, parseTime, delimiter, escape) = metadata
		
		val parser =
			CsvParser(
				reader,
				if (delimiter == AutoDetectDelimiter)
				{
					val d = CsvParser.tryDelimiterDetection(reader, escapeChar = escape)
					
					if (d == -1)
						throw Exception(
							"The delimiter character could not be auto-detected."
						)
					else d.toChar()
				}
				else delimiter,
				escape,
				keepOpen,
				autoClose = true
			)
		
		CsvReader(
			CsvReaderContext() with
			{
				this.delimiter =
					if (delimiter == AutoDetectDelimiter)
					{
						val d = CsvParser.tryDelimiterDetection(reader, escapeChar = escape)
						
						if (d == -1)
							throw Exception(
								"The delimiter character could not be auto-detected."
							)
						else d.toChar()
					}
					else delimiter
				
				 quoteChar = escape
				escapeChar = escape
			}
		).openAsync()
		
		// Get the header indices.
		
		val (symbolName,
			   timeName,
			   openName,
			   highName,
			    lowName,
			  closeName,
			 volumeName) = columns
		
		
	}
}