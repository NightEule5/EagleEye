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

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import org.intellij.lang.annotations.Language
import strixpyrr.abstrakt.collections.first
import strixpyrr.abstrakt.collections.last
import strixpyrr.eagleeye.data.DataAggregation.OperationInfo.Mode
import strixpyrr.eagleeye.data.DataAggregation.OperationInfo.Mode.Append
import strixpyrr.eagleeye.data.DataAggregation.OperationInfo.Mode.Fill
import strixpyrr.eagleeye.data.internal.toDateTime
import strixpyrr.eagleeye.data.internal.toInstant
import strixpyrr.eagleeye.data.models.Interval
import strixpyrr.eagleeye.data.models.MarketInstant
import strixpyrr.eagleeye.data.sources.IDataSource
import strixpyrr.eagleeye.data.sources.create
import strixpyrr.eagleeye.data.storage.TransparentStorageFormat
import strixpyrr.eagleeye.data.storage.manipulation.DatasetScope
import strixpyrr.eagleeye.data.storage.manipulation.modifiedBy
import strixpyrr.eagleeye.data.storage.manipulation.temporalUnit
import uy.klutter.core.common.whenNotNull
import uy.klutter.core.common.withNotNull
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.temporal.TemporalUnit
import java.util.TreeMap
import kotlin.reflect.KClass
import kotlin.reflect.full.IllegalCallableAccessException
import kotlin.reflect.full.createInstance

suspend fun aggregateData(aggregation: DataAggregation)
{
	val (path, source, apiKey, operation) = aggregation
	val (interval, limit, mode, start, end) = operation
	
	if (!TransparentStorageFormat.canStoreTo(path))
		throw DataAggregatorException(
			"The path specified is not a valid path to store data in: $path was" +
			" rejected by TransparentStorageFormat."
		)
	
	coroutineScope()
	{
		if (!TransparentStorageFormat.canStoreTo(path))
			throw DataAggregatorException(
				"The path provided is not a valid location to store the resulting dataset."
			)
		
		// Start extracting and decoding the data from the specified path.
		val datasetTask = async(Dispatchers.IO) { TransparentStorageFormat.extract(path) }
		
		val dataSource = source.create()
		
		// Get the symbol.
		val symbolResult =
			dataSource.getSymbol(
				apiKey,
				operation.exchangeName,
				operation.baseAssetSymbol,
				operation.quoteAssetSymbol
			)
		
		if (!symbolResult.wasSuccessful)
			throw DataAggregatorException(symbolResult.errorMessage!!)
		
		val (symbol, base, quote, exchange) = symbolResult
		
		// Wait for the extraction to finish.
		val storedDataset = datasetTask.await()
		
		val dataset = storedDataset modifiedBy
		{
			onSymbol(symbol, base, quote, exchange)
			{
				val intervalValue = parseInterval(interval)
				
				onInterval(intervalValue)
				{
					val from = start ?: LocalDateTime.MIN
					val to   = end   ?: LocalDateTime.MAX
					
					start withNotNull { startTime = toInstant() }
					end   withNotNull {   endTime = toInstant() }
					
					download(symbol, mode, apiKey, dataSource, from, to, limit, intervalValue)
				}
			}
		}
		
		if (!TransparentStorageFormat.store(dataset, path))
			throw DataAggregatorException("Storage of the new dataset failed.")
	}
}

private suspend fun DatasetScope.IntervalScope.download(
	symbol: String,
	mode: Mode,
	apiKey: String,
	source: IDataSource,
	from: LocalDateTime,
	to: LocalDateTime,
	limit: Int,
	interval: Interval
)
{
	val factor = source.limitRequestFactor
	
	val nearestLimit = (limit / factor + if (limit % factor == 0) 0 else 1) * factor
	
	val timeIncrement = interval.length.toLong()
	val timeUnit = interval.denomination.temporalUnit
	
	require(to >= from)
	{
		"The specified start time comes after the end time."
	}
	
	val downloadTask =
		when (mode)
		{
			Append -> downloadAppending(symbol, apiKey, source, from, to, nearestLimit, interval)
			Fill   -> downloadFilling(symbol, apiKey, source, from, to, nearestLimit, interval, timeIncrement, timeUnit)
		}
	
	val map = TreeMap<LocalDateTime, MarketInstant>()
	
	downloadTask.collect()
	{
		map.putAll(it)
		
		statusVerbose()
		{
			val count = it.size
			val keys = it.keys
			val start = keys.first
			val end = keys.last
			
			"$count data points were downloaded successfully between $start and" +
			" $end."
		}
	}
	
	points.putAll(map)
}

private suspend fun DatasetScope.IntervalScope.downloadFilling(
	symbol: String,
	apiKey: String,
	source: IDataSource,
	from: LocalDateTime,
	to: LocalDateTime,
	limit: Int,
	interval: Interval,
	timeIncrement: Long,
	timeUnit: TemporalUnit
) = flow<Map<LocalDateTime, MarketInstant>>()
	{
		coroutineScope()
		{
			val intervalString = source.toSourceNotation(interval)
			var current: Deferred<Int>
			var missing = getNextMissingRange(from, to)
			
			var currentLimit = limit
			
			while (missing != null)
			{
				val start = missing.start
				val end = missing.endInclusive
				
				current = async()
				{
					val result =
						source.getHistoricalData(
							apiKey,
							symbol,
							intervalString,
							start.toInstant(),
							end.toInstant(),
							currentLimit
						)
					
					if (result.wasSuccessful)
					{
						val points =
							result
								.points
								.associate()
								{ (time, open, high, low, close, volume) ->
									time.toDateTime() to
										MarketInstant(
											time,
											open,
											high,
											low,
											close,
											volume
										)
								}
						
						emit(points)
						
						points.size
					}
					else throw DataAggregatorException(result.errorMessage!!)
				}
				
				missing = getNextMissingRange(from, to)
				
				currentLimit = current.await()
				
				if (currentLimit <= 0) return@coroutineScope
			}
			
			// Finish using the limit by appending for the rest of it.
			downloadAppending(
				symbol,
				apiKey,
				source,
				points
					.lastKey()
					.plus(
						timeIncrement,
						timeUnit
					),
				to,
				currentLimit,
				interval,
			).collect(this@flow::emit)
		}
	}

private suspend fun DatasetScope.IntervalScope.downloadAppending(
	symbol: String,
	apiKey: String,
	source: IDataSource,
	from: LocalDateTime,
	to: LocalDateTime,
	limit: Int,
	interval: Interval
) = flow<Map<LocalDateTime, MarketInstant>>()
	{
		coroutineScope()
		{
			val intervalString = source.toSourceNotation(interval)
			
			val start =
				if (points.isNotEmpty())
				{
					val lastTime = points.lastKey()
					
					if (lastTime > from)
						lastTime
					else from
				}
				else from
			val end = if (to == LocalDateTime.MAX) null else to
			
			val result =
				source.getHistoricalData(
					apiKey,
					symbol,
					intervalString,
					start.toInstant(),
					end?.toInstant(),
					limit
				)
			
			if (result.wasSuccessful)
			{
				val points =
					result
						.points
						.associate()
						{ (time, open, high, low, close, volume) ->
							time.toDateTime() to
								MarketInstant(
									time,
									open,
									high,
									low,
									close,
									volume
								)
						}
				
				emit(points)
				
				points.size
			}
			else throw DataAggregatorException(result.errorMessage!!)
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
		val start: LocalDateTime?,
		val end: LocalDateTime?,
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