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

import com.github.doyaaaaaken.kotlincsv.dsl.context.CsvReaderContext
import uy.klutter.core.common.with
import java.io.InputStream
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.extension
import kotlin.io.path.inputStream

open class CsvIngestAggregator(
	@JvmField protected val settings: CsvSettings = CsvSettings.ccdSettings
) : IIngestAggregator<Instant, Ohlcv>
{
	override fun isSupported(format: String) =
		format == "csv" || format == "tsv"
	
	protected fun createContext(isTabSeparated: Boolean = false) =
		CsvReaderContext() with
		{
			if (isTabSeparated)
				delimiter = '\t'
		}
	
	@OptIn(ExperimentalPathApi::class)
	override suspend fun ingest(path: Path, map: MutableMap<Instant, in Ohlcv>) =
		ingest(path.inputStream(), map, isTabSeparated = path.extension == "tsv")
	
	override suspend fun ingest(stream: InputStream, map: MutableMap<Instant, in Ohlcv>) =
		ingest(stream, map, isTabSeparated = false)
	
	protected suspend fun ingest(
		stream: InputStream,
		map: MutableMap<Instant, in Ohlcv>,
		isTabSeparated: Boolean
	) = createContext(isTabSeparated)
			.ingestCsv(stream, map, settings)
}