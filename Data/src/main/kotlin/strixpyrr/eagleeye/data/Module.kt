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
package strixpyrr.eagleeye.data

import kotlinx.cli.*
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import strixpyrr.abstrakt.collections.first
import strixpyrr.eagleeye.common.IAction
import strixpyrr.eagleeye.common.IActionParameterContainer
import strixpyrr.eagleeye.common.IModule
import strixpyrr.eagleeye.common.cli.PathArgument
import strixpyrr.eagleeye.data.internal.DiscontinuousSet
import strixpyrr.eagleeye.data.internal.roundedTo
import strixpyrr.eagleeye.data.models.Interval
import strixpyrr.eagleeye.data.models.Interval.Denomination
import uy.klutter.core.common.withNotNull
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.system.exitProcess
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

object DataModule : IModule
{
	const val          Name = "Data"
	const val AggregateName = "Aggregate"
	const val     PruneName = "Prune"
	
	const val          Desc = "Downloads, stores, and manipulates EagleEye data" +
							  "sets."
	const val AggregateDesc = "Downloads data from a remote source and aggregat" +
							  "es it into a dataset."
	const val     PruneDesc = "Removes unneeded data from a dataset."
	
	override val name        get() = Name
	override val description get() = Desc
	
	override fun populate(
		parser: ArgParser
	) = parser.subcommands(
			Aggregate().subcommand,
			Prune    ().subcommand
		)
	
	@OptIn(
		ExperimentalPathApi::class,
		ExperimentalTime::class
	)
	private suspend fun Aggregate.Container.run()
	{
		try
		{
			Verbose = verbose
			
			if (!interval.isValidTimeInterval)
				throw DataAggregatorException("$interval is not a valid time interval.")
			
			val executionTime = measureTime()
			{
				try
				{
					withTimeout(60L * 1000L)
					{
						val outputPath = output ?: "./EagleEyeDataset.dat"
						
						aggregateData(
							DataAggregation.create(
								Path(outputPath),
								source,
								apiKey,
								DataAggregation.OperationInfo(
									interval,
									limit,
									mode,
									start withNotNull
									{
										LocalDateTime.parse(this, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
									},
									end withNotNull
									{
										LocalDateTime.parse(this, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
									},
									exchange,
									quote,
									base
								)
							)
						)
					}
				}
				catch (e: TimeoutCancellationException)
				{
					error("Data aggregation timed out.")
				}
			}
			
			status("Data aggregation finished successfully in ${executionTime.inSeconds roundedTo 2}s.")
			
			exitProcess(0)
		}
		catch (e: DataAggregatorException)
		{
			error("Data aggregation failed:\n")
			
			e.printStackTrace()
			
			exitProcess(1)
		}
	}
	
	@OptIn(ExperimentalTime::class)
	private suspend fun Prune.Container.run()
	{
		try
		{
			Verbose = verbose
			
			val executionTime = measureTime()
			{
				prune(
					input,
					outputPath,
					inclusionFilter,
					exclusionFilter
				)
			}
			
			status("Pruning finished successfully in ${executionTime.inSeconds roundedTo 2}s.")
			
			exitProcess(0)
		}
		catch (e: PrunerException)
		{
			error("Pruning failed:\n")
			
			e.printStackTrace()
			
			exitProcess(1)
		}
	}
	
	internal class Aggregate : IAction<Aggregate.Container>
	{
		class Container(parser: ArgParser) : CommonContainer(parser)
		{
			// Todo: Add a symbol option that overrides the Quote, Base, and Exchange
			//  options.
			
			val source   by parser.sourceOption
			val apiKey   by parser.apiKeyOption
			val output   by parser.outputPathOption
			val interval by parser.intervalOption
			val limit    by parser.entryLimitOption
			val mode     by parser.modeOption
			val start    by parser.startTimeOption
			val end      by parser.endTimeOption
			val exchange by parser.exchangeOption
			val quote    by parser.quoteOption
			val base     by parser.baseOption
		}
		
		override fun createParameterContainer(parser: ArgParser) = Container(parser)
		
		override suspend fun invoke(container: Container) = container.run()
		
		override val name        get() = AggregateName
		override val description get() = AggregateDesc
	}
	
	internal class Prune : IAction<Prune.Container>
	{
		class Container(parser: ArgParser) : CommonContainer(parser)
		{
			val input by parser.inputDatasetArgument
			val output by parser.outputDatasetOption
			val include by parser.includeOption
			val exclude by parser.excludeOption
			
			// Prune in place.
			val outputPath get() = output ?: input
			
			val inclusionFilter get() = createFilter(include, default = DatasetFilter.All)
			val exclusionFilter get() = createFilter(exclude, default = DatasetFilter.None)
			
			companion object
			{
				private fun createFilter(specs: List<DatasetValueSpec>, default: DatasetFilter): DatasetFilter
				{
					if (specs.isEmpty()) return default
					
					val symbols   =  mutableSetOf<String                    >()
					val intervals =  mutableSetOf<IntervalSpec              >()
					val times     = mutableListOf<ClosedRange<LocalDateTime>>()
					
					for (spec in specs)
					{
						when (spec)
						{
							is SymbolSpec    -> symbols += spec.value
							is IntervalSpec  -> intervals += spec
							is TimeRangeSpec -> times += spec.range
						}
					}
					
					return DatasetFilter.Constrained(symbols, intervals, DiscontinuousSet(times))
				}
			}
		}
		
		override fun createParameterContainer(parser: ArgParser) = Container(parser)
		
		override suspend fun invoke(container: Container) = container.run()
		
		override val name        get() = PruneName
		override val description get() = PruneDesc
	}
	
	internal abstract class CommonContainer(parser: ArgParser) : IActionParameterContainer
	{
		val verbose by parser.verboseOption
	}
}

private val ArgParser.verboseOption get() =
	option(
		ArgType.Boolean,
		fullName = "verbose",
		shortName = "v"
	).default(false)

// region Aggregate

private val ArgParser.sourceOption get() =
	option(
		ArgType.Choice<Source>(),
		fullName = "source",
		shortName = "i",
		description = "The source to download data from."
	).default(Source.CoinAPI)

private val ArgParser.outputPathOption get() =
	option(
		ArgType.String,
		fullName = "output-path",
		shortName = "o",
		description = "A path to the file or directory to write data to."
	)

private val ArgParser.apiKeyOption get() =
	option(
		ArgType.String,
		fullName = "api-key",
		shortName = "a",
		description = ApiKeyDescription
	)

private const val ApiKeyDescription =
	"The API key to use when connecting to the source. If no value is provided," +
		" it will be pulled from an environment variable."

private val ArgParser.intervalOption get() =
	option(
		ArgType.String,
		fullName = "time-interval",
		shortName = "t",
		description = "The time interval to download data from."
	).required()

/**
 * Matches any of the supported intervals, as listed in the CoinAPI documentation
 * [here](https://docs.coinapi.io/#list-all-periods).
 */
private val String.isValidTimeInterval get() =
	matches(
		@Suppress("RegExpRepeatedSpace")
		Regex(
			"""
			([1-6]|[1-3]0|15)(SEC|MIN) | # Single digit 1-6, 10, 15, 20, or 30 followed by SEC or MIN
			([1-46]|12      ) HRS      | # Single digit 1-4 or 5, or 12 followed by HRS
			([1-357]|10     ) DAY      | # Single digit 1-3, 5 or 7, or 10 followed by DAY
			 [1-46]           MTH      | # Single digit 1-4 or 6 followed by MTH
			 [1-5]            YRS        # Single digit 1-5 followed by YRS
			""",
			setOf(
				RegexOption.COMMENTS,
				RegexOption.IGNORE_CASE,
			)
		)
	)

private val ArgParser.entryLimitOption get() =
	option(
		ArgType.Int,
		fullName = "entry-limit",
		shortName = "l",
		description = "How many entries should be downloaded."
	).default(-1)

private val ArgParser.modeOption get() =
	option(
		ArgType.Choice<DataAggregation.OperationInfo.Mode>(),
		fullName = "mode",
		shortName = "m"
	).default(DataAggregation.OperationInfo.Mode.Append)

private val ArgParser.startTimeOption get() =
	option(
		ArgType.String,
		fullName = "start-time",
		shortName = "s"
	)

private val ArgParser.endTimeOption get() =
	option(
		ArgType.String,
		fullName = "end-time",
		shortName = "e"
	)

private val ArgParser.exchangeOption get() =
	option(
		ArgType.String,
		fullName = "exchange-name",
		shortName = "E"
	).required()

private val ArgParser.quoteOption get() =
	option(
		ArgType.String,
		fullName = "quote-asset",
		shortName = "Q"
	).required()

private val ArgParser.baseOption get() =
	option(
		ArgType.String,
		fullName = "base-asset",
		shortName = "B"
	).required()

// endregion

// region Prune

private val ArgParser.inputDatasetArgument get() =
	argument(
		PathArgument,
		fullName = "dataset-path",
		description = "A path to the dataset to prune."
	)

private val ArgParser.outputDatasetOption get() =
	option(
		PathArgument,
		fullName = "output-dataset-path",
		shortName = "o",
		description = "The path to write the pruned dataset to. If omitted, the" +
					  " input dataset is pruned in place."
	)

private val ArgParser.includeOption get() =
	option(
		DatasetValueSpecArgument,
		fullName = "include",
		shortName = "I",
		description = "Excludes a value from the dataset."
	).multiple()

private val ArgParser.excludeOption get() =
	option(
		DatasetValueSpecArgument,
		fullName = "exclude",
		shortName = "E",
		description = "Excludes a value from the dataset."
	).multiple()

private object DatasetValueSpecArgument : ArgType<DatasetValueSpec>(hasParameter = true)
{
	private const val ValidFormat = "[Symbol|Interval|Time]:<Value>"
	
	override val description get() = "{ A value spec of format $ValidFormat }"
	
	override fun convert(value: kotlin.String, name: kotlin.String): DatasetValueSpec
	{
		val components = value.split(':', limit = 2)
		
		require(components.size == 2)
		{
			"A value spec must have the format $ValidFormat."
		}
		
		val (type, spec) = components
		
		return when (type.length)
		{
			1    -> when (type[0])
			{
				'S', 's' -> SymbolSpec(value = spec)
				'I', 'i' -> IntervalSpec(interval = parseInterval(spec))
				'T', 't' -> TimeRangeSpec(range = parseTimeRange(spec))
				else     ->
					throw IllegalArgumentException(
						"A single character spec type must be either S, I, or T" +
						", but ${type[0]} was specified."
					)
			}
			else -> when
			{
				type.equals("symbol",   ignoreCase = true) -> SymbolSpec(value = spec)
				type.equals("interval", ignoreCase = true) -> IntervalSpec(interval = parseInterval(spec))
				type.equals("time",     ignoreCase = true) -> TimeRangeSpec(range = parseTimeRange(spec))
				else                                       ->
					throw IllegalArgumentException(
						"A spec type must be either Symbol, Interval, or Time, " +
						" but $spec was specified."
					)
			}
		}
	}
	
	private const val MalformedTime = "Malformed time spec:"
	
	private fun parseTimeRange(value: kotlin.String): ClosedRange<LocalDateTime>
	{
		require(value.isNotEmpty()) { "No time value was specified." }
		
		val ends = value.split(',')
		val endCount = ends.size
		
		require(endCount <= 2)
		{
			"$MalformedTime only one or two range components are allowed."
		}
		
		val leftTime: LocalDateTime
		val leftSymbol: Char
		val rightTime: LocalDateTime
		val rightSymbol: Char
		
		when (ends.size)
		{
			1    -> with(ends.first)
			{
				val length = length
				val lastIndex = length - 1
				
				leftSymbol = this[0]
				rightSymbol = this[lastIndex]
				
				
				val s = when (leftSymbol)
				{
					'['      -> 1
					'(', ']' ->
						throw IllegalArgumentException(
							"$MalformedTime time ranges must be inclusive."
						)
					else     -> 0
				}
				
				val e = when (rightSymbol)
				{
					']'      -> lastIndex
					')', '[' ->
						throw IllegalArgumentException(
							"$MalformedTime time ranges must be inclusive."
						)
					else     -> length
				}
				
				val time =
					LocalDateTime.parse(
						substring(startIndex = s, endIndex = e)
					)
				
				leftTime = time
				rightTime = time
			}
			2    ->
			{
				val (left, right) = ends
				
				leftSymbol = left[0]
				rightSymbol = right[right.lastIndex]
				
				leftTime =
					when (leftSymbol)
					{
						'['      -> LocalDateTime.parse(left.substring(startIndex = 1))
						'(', ']' ->
							if (left.length == 1)
								LocalDateTime.MIN
							else
								throw IllegalArgumentException(
									"$MalformedTime time ranges must be inclusive."
								)
						else     -> LocalDateTime.parse(left)
					}
				
				rightTime =
					when (rightSymbol)
					{
						']'      -> LocalDateTime.parse(right.substring(startIndex = 0, endIndex = right.lastIndex))
						')', '[' ->
							if (right.length == 1)
								LocalDateTime.MAX
							else
								throw IllegalArgumentException(
									"$MalformedTime time ranges must be inclusive."
								)
						else     -> LocalDateTime.parse(right)
					}
			}
			else ->
				throw IllegalArgumentException(
					"$MalformedTime only one or two range components are allowed."
				)
		}
		
		return leftTime.rangeTo(rightTime)
	}
}

internal sealed class DatasetValueSpec

internal data class SymbolSpec(val value: String) : DatasetValueSpec()

internal data class IntervalSpec(val length: Int, val denomination: Denomination) : DatasetValueSpec()
{
	constructor(interval: Interval) : this(interval.length, interval.denomination)
	
	fun intervalEquals(other: Interval) =
		length       == other.length       &&
		denomination == other.denomination
}

internal data class TimeRangeSpec(val range: ClosedRange<LocalDateTime>) : DatasetValueSpec()

internal sealed class DatasetFilter
{
	object All : DatasetFilter()
	{
		override fun contains(symbol   : String                    ) = true
		override fun contains(interval : Interval                  ) = true
		override fun contains(time     : LocalDateTime             ) = true
		override fun contains(timeRange: ClosedRange<LocalDateTime>) = true
	}
	
	object None : DatasetFilter()
	{
		override fun contains(symbol   : String                    ) = false
		override fun contains(interval : Interval                  ) = false
		override fun contains(time     : LocalDateTime             ) = false
		override fun contains(timeRange: ClosedRange<LocalDateTime>) = false
	}
	
	class Constrained(
		private val symbols: Collection<String>,
		private val intervals: Collection<IntervalSpec>,
		private val timeRanges: DiscontinuousSet<LocalDateTime>
	) : DatasetFilter()
	{
		override fun contains(symbol: String) = symbol in symbols
		
		override fun contains(interval: Interval) =
			intervals.any { it.intervalEquals(interval) }
		
		override fun contains(time: LocalDateTime) = time in timeRanges
		
		override fun contains(
			timeRange: ClosedRange<LocalDateTime>
		) = timeRange in timeRanges
	}
	
	abstract operator fun contains(symbol   : String                    ): Boolean
	abstract operator fun contains(interval : Interval                  ): Boolean
	abstract operator fun contains(time     : LocalDateTime             ): Boolean
	abstract operator fun contains(timeRange: ClosedRange<LocalDateTime>): Boolean
}

// endregion