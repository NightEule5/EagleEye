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

import uy.klutter.core.common.with
import java.io.InputStream
import java.io.Reader
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.temporal.Temporal
import java.util.TreeMap
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.bufferedReader

@OptIn(ExperimentalPathApi::class)
interface IIngestAggregator<T : Temporal, D : IIngestAggregator.IOhlcv>
{
	fun isSupported(format: String) = true
	
	private companion object
	{
		private const val bs = 2 * 1024
	}
	
	suspend fun ingest(
		metadata: IIngestStreamMetadata,
		path: Path
	) = ingest(metadata, path.bufferedReader(bufferSize = bs), keepOpen = false)
	
	suspend fun ingest(
		metadata: IIngestStreamMetadata,
		stream: InputStream,
		keepOpen: Boolean = false
	) = ingest(metadata, stream.reader().buffered(bufferSize = bs), keepOpen)
	
	suspend fun ingest(
		metadata: IIngestStreamMetadata,
		reader: Reader,
		keepOpen: Boolean = false
	) = TreeMap<T, D>() with
		{
			ingest(metadata, reader, map = this, keepOpen)
		}
	
	suspend fun ingest(
		metadata: IIngestStreamMetadata,
		path: Path,
		map: MutableMap<T, in D>
	) = ingest(metadata, path.bufferedReader(bufferSize = bs), map, keepOpen = false)
	
	suspend fun ingest(
		metadata: IIngestStreamMetadata,
		stream: InputStream,
		map: MutableMap<T, in D>,
		keepOpen: Boolean = false
	) = ingest(metadata, stream.reader().buffered(bufferSize = bs), keepOpen)
	
	suspend fun ingest(
		metadata: IIngestStreamMetadata,
		reader: Reader,
		map: MutableMap<T, in D>,
		keepOpen: Boolean = false
	)
	
	interface IOhlcv
	{
		val open: Double
		val high: Double
		val low: Double
		val close: Double
		val volume: Double
		
		operator fun component1() = open
		operator fun component2() = high
		operator fun component3() = low
		operator fun component4() = close
		operator fun component5() = volume
	}
}

data class Ohlcv(
	override val open: Double,
	override val high: Double,
	override val low: Double,
	override val close: Double,
	override val volume: Double
): IIngestAggregator.IOhlcv

abstract class IngestAggregator : IIngestAggregator<LocalDateTime, Ohlcv>
{

}