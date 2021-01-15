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

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import strixpyrr.eagleeye.common.IAction
import strixpyrr.eagleeye.common.IActionParameterContainer
import strixpyrr.eagleeye.common.IModule
import strixpyrr.eagleeye.data.internal.roundedTo
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
	const val Name          = "Data"
	const val AggregateName = "Aggregate"
	
	const val Desc          =
		"Downloads, stores, and manipulates EagleEye datasets."
	const val AggregateDesc =
		"Downloads data from a remote source and aggregates it into a dataset."
	
	override val name        get() = Name
	override val description get() = Desc
	
	override fun populate(
		parser: ArgParser
	) = parser.subcommands(
			Aggregate().subcommand
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