// Copyright 2020-2021 Strixpyrr
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
package strixpyrr.eagleeye.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okio.ByteString
import org.intellij.lang.annotations.Language
import strixpyrr.abstrakt.collections.last
import strixpyrr.eagleeye.data.DataAggregation.OperationInfo.Mode.Append
import strixpyrr.eagleeye.data.DataAggregation.OperationInfo.Mode.Fill
import strixpyrr.eagleeye.data.models.Interval
import strixpyrr.eagleeye.data.models.SymbolPeriodData
import strixpyrr.eagleeye.data.models.SymbolType
import strixpyrr.eagleeye.data.sources.IDataSource
import strixpyrr.eagleeye.data.sources.create
import strixpyrr.eagleeye.data.storage.TransparentStorageFormat
import strixpyrr.eagleeye.data.storage.models.*
import strixpyrr.eagleeye.data.storage.parseInterval
import uy.klutter.core.common.initializedWith
import uy.klutter.core.common.whenNotNull
import uy.klutter.core.common.with
import uy.klutter.core.common.withNotNull
import java.nio.file.Path
import java.time.Instant
import kotlin.reflect.KClass
import kotlin.reflect.full.IllegalCallableAccessException
import kotlin.reflect.full.createInstance

suspend fun aggregateData(aggregation: DataAggregation)
{
	val (path, source, apiKey, operation) = aggregation
	val (interval) = operation
	
	if (!TransparentStorageFormat.canStoreTo(path))
		throw DataAggregatorException(
			"The path specified is not a valid path to store data in: $path was" +
			" rejected by TransparentStorageFormat."
		)
	
	coroutineScope()
	{
		// Start extracting and decoding the data from the specified path.
		val storedDataTask = async { TransparentStorageFormat.extract(path) }
		
		val dataSource = source.create()
		
		// Get the symbol.
		val symbolResult =
			dataSource.getSymbol(
				apiKey,
				operation.exchangeName,
				operation.baseAssetSymbol,
				operation.quoteAssetSymbol
			) ?: throw DataAggregatorException(
				"The symbol could not be determined for the specified exchange-" +
				"base-quote combination, or your API rate limit has been reached."
			)
		
		if (!symbolResult.wasSuccessful)
			throw DataAggregatorException(symbolResult.errorMessage!!)
		
		val (symbol, base, quote, exchange) = symbolResult
		
		// Wait for the extraction to finish.
		var storedData = storedDataTask.await()
		
		val index: Index
		val symbolIndex: Int
		val baseIndex: Int
		val quoteIndex: Int
		val intervalIndex: Int
		
		val periodData: Map<Int, SymbolPeriodData>
		
		storedData?.index.let()
		{ indexValue ->
			val assets: Map<String, Int>
			val symbols: Map<String, Int>
			val intervals: Map<String, Int>
			val symbolMeta: List<SymbolMetadata>
			val intervalMeta: List<IntervalMetadata>
			
			if (indexValue == null)
			{
				assets = mapOf(base to 0, quote to 1)
				symbols = mapOf(symbol to 0)
				intervals = mapOf(interval to 0)
				symbolMeta = listOf(SymbolMetadata(indexName = symbol, heldAsset = 1, tradedAsset = 0))
				intervalMeta = listOf(IntervalMetadata(indexName = interval, interval = parseInterval(interval)))
				
				symbolIndex = 0
				baseIndex = 0
				quoteIndex = 1
				intervalIndex = 0
			}
			else
			{
				val assetIndexes = indexValue.assetIndexes
				val symbolIndexes = indexValue.symbolIndexes
				val intervalIndexes = indexValue.intervalIndexes
				
				fun Collection<Int>.getNext() = maxOrNull()!! + 1
				
				if (assetIndexes.isNotEmpty())
				{
					val usedIndexes = assetIndexes.values
					
					baseIndex = assetIndexes[base] ?: usedIndexes.getNext()
					quoteIndex = assetIndexes[quote] ?: usedIndexes.getNext()
				}
				else
				{
					baseIndex = 0
					quoteIndex = 1
				}
				
				symbolIndex =
					if (symbolIndexes.isNotEmpty())
					{
						val usedIndexes = symbolIndexes.values
						
						symbolIndexes[symbol] ?: usedIndexes.getNext()
					}
					else 0
				
				intervalIndex =
					if (intervalIndexes.isNotEmpty())
					{
						val usedIndexes = intervalIndexes.values
						
						intervalIndexes[interval] ?: usedIndexes.getNext()
					}
					else 0
				
				assets =
					assetIndexes.toMutableMap() with
					{
						put(base, baseIndex)
						put(quote, quoteIndex)
					}
				symbols =
					symbolIndexes.toMutableMap() with
					{
						put(symbol, symbolIndex)
					}
				intervals =
					intervalIndexes.toMutableMap() with
					{
						put(interval, intervalIndex)
					}
				symbolMeta =
					indexValue.symbols.toMutableList() with
					{
						if (none { it.indexName == symbol })
							this += SymbolMetadata(
								indexName = symbol,
								heldAsset = quoteIndex,
								tradedAsset = baseIndex
							)
					}
				intervalMeta =
				
				
				assets = assetIndexes + mapOf(base to baseIndex, quote to quoteIndex)
				symbols = symbolIndexes + (symbol to symbolIndex)
				intervals = intervalIndexes + (interval to intervalIndex)
				symbolMeta = indexValue.symbols.run { if (none { it.indexName == symbol }) this + SymbolMetadata(indexName = symbol, heldAsset = quoteIndex, tradedAsset = baseIndex) else this }
				intervalMeta = indexValue.intervals.run { if (none { it.indexName == interval }) this + IntervalMetadata(indexName = interval, interval = parseInterval(interval)) else this }
			}
			
			index = Index(
				assets,
				symbols,
				intervals,
				symbolMeta,
				intervalMeta,
				indexValue?.unknownFields ?: ByteString.EMPTY
			)
		}
		
		val data = storedData?.periodData ?: SymbolPeriodData() // Todo:
		
		// Todo: Do some downloading works.
		
		TransparentStorageFormat.store(
			StoredData(index, periodData),
			path
		)
	}
}

private fun Source.createSource(customSourceType: KClass<out IDataSource>?) =
	if (this == Source.Custom)
		try
		{
			checkNotNull(customSourceType)
			{
				"A source type should be provided for the Custom source."
			}
			
			customSourceType.createInstance()
		}
		catch (e: IllegalArgumentException)
		{
			throw DataAggregatorException(
				"The specified source type has no parameterless constructor, so" +
				" no instance could be created.", e
			)
		}
		catch (e: IllegalCallableAccessException)
		{
			// Todo: Will this happen? The constructor list only returns public
			//  constructors from what I understand, so I don't think this would
			//  ever be thrown.
			
			throw DataAggregatorException(
				"The specified source type's parameterless constructor is inacc" +
				"essable, so no instance could be created.", e
			)
		}
	else create()

data class DataAggregation(
	val path: Path,
	val source: Source,
	val apiKey: String,
	val operation: OperationInfo,
	val customSourceType: KClass<out IDataSource>? = null
)
{
	/**
	 * @property start The start time of the data to download, if desired. If this
	 *  is set to null, data will be downloaded with no specific start time (which
	 *  in practice means however much from the end time to reach the entry limit).
	 * @property end The end time of the data. If this is set, the download will be
	 *  stopped either when the end time is reached, there's no more data, or the
	 *  limit is reached (whichever comes first). Data may still be downloaded from
	 *  after the end time to fill the limit.
	 */
	data class OperationInfo(
		val interval: String,
		val entryLimit: Int,
		val mode: Mode,
		val start: Instant?,
		val end: Instant?,
		val exchangeName: String,
		val baseAssetSymbol: String,
		val quoteAssetSymbol: String
	)
	{
		/**
		 * @property Append Downloads data starting from the last time downloaded.
		 * @property Fill Fills in missing data before switching to [Append]. This
		 *  could waste requests, as missing data may barely take up more than one
		 *  request.
		 */
		enum class Mode { Append, Fill }
	}
	
	companion object // Used for the option extensions.
	{
		fun create(path: Path, source: Source, apiKey: String?, operation: OperationInfo) =
			DataAggregation(path, source, apiKey ?: getApiKeyFromEnvironment(source), operation)
		
		private fun getApiKeyFromEnvironment(source: Source): String
		{
			val env = Environment
			
			// A bit overkill maybe, but whatever.
			fun getAlternateForm(@Language("RegExp") pattern: String): String?
			{
				val regex = Regex(pattern, RegexOption.IGNORE_CASE)
				
				for (key in env.keys)
					if (key.matches(regex))
						return env[key]
				
				return null
			}
			
			var apiKey: String? = null
			
			env["EagleEyeDataSourceApiKey"] whenNotNull { return it }
			
			getAlternateForm(
				"eagle[_-]eye[_-]data[_-]source[_-]api[_-]key"
			) whenNotNull { return it }
			
			@Suppress("SpellCheckingInspection")
			when (source)
			{
				Source.CoinAPI -> apiKey = env["CoinApiKey"] ?: getAlternateForm("coinapi[_-]key")
				Source.Custom  -> apiKey = env["EagleEyeDataApiKey"] ?: getAlternateForm("eagleeye[_-]data[_-]api[_-]key")
			}
			
			if (apiKey == null)
				throw DataAggregatorException("No API key could be found.")
			
			return apiKey
		}
	}
}

enum class Source { CoinAPI, Custom }

private inline val Environment get() = System.getenv()

class DataAggregatorException : RuntimeException
{
	constructor(                                 ) : super(              )
	constructor(message: String                  ) : super(message       )
	constructor(message: String, cause: Throwable) : super(message, cause)
	constructor(                 cause: Throwable) : super(         cause)
}