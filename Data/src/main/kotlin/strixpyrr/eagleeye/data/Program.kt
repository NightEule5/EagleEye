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

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgParser.OptionPrefixStyle.GNU
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import strixpyrr.abstrakt.annotations.InlineOnly
import strixpyrr.eagleeye.data.DataAggregation.OperationInfo
import strixpyrr.eagleeye.data.DataAggregation.OperationInfo.Mode
import strixpyrr.eagleeye.data.internal.roundedTo
import uy.klutter.core.common.withNotNull
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.system.exitProcess
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

@OptIn(
	ExperimentalTime::class,
	ExperimentalPathApi::class
)
suspend fun main(parameters: Array<String>)
{
	try
	{
		val parser = ArgParser("Data", prefixStyle = GNU)
		
		val verbose = parser.verboseOption
		
		val source = parser.sourceOption
		val apiKey = parser.apiKeyOption
		val output = parser.outputPathOption
		val interval = parser.intervalOption
		val limit = parser.entryLimitOption
		val mode = parser.modeOption
		val start = parser.startTimeOption
		val end = parser.endTimeOption
		val exchange = parser.exchangeOption
		val quote = parser.quoteOption
		val base = parser.baseOption
		
		parser.parse(parameters)
		
		if (verbose.value)
			Verbose = true
		
		if (!interval.value.isValidTimeInterval)
			throw DataAggregatorException("${interval.value} is not a valid time interval.")
		
		val executionTime = measureTime()
		{
			try
			{
				withTimeout(60L * 1000L)
				{
					val outputPath = output.value ?: "./EagleEyeDataset.dat"
					
					aggregateData(
						DataAggregation.create(
							Path(outputPath),
							source.value,
							apiKey.value,
							OperationInfo(
								interval.value,
								limit.value,
								mode.value,
								start.value withNotNull
								{
									LocalDateTime.parse(this, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
								},
								end.value withNotNull
								{
									LocalDateTime.parse(this, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
								},
								exchange.value,
								quote.value,
								base.value
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

@InlineOnly
inline fun DataAggregation.Companion.getVerboseOption(parser: ArgParser) = parser.verboseOption

@PublishedApi
internal val ArgParser.verboseOption get() =
	option(
		ArgType.Boolean,
		fullName = "verbose",
		shortName = "v"
	).default(false)

// Exposes sourceOption without potentially conflicting with options with the same
// name in other modules.
@InlineOnly
inline fun DataAggregation.Companion.getSourceOption(parser: ArgParser) = parser.sourceOption

@PublishedApi
internal val ArgParser.sourceOption get() =
	option(
		ArgType.Choice<Source>(),
		fullName = "source",
		shortName = "i",
		description = "The source to download data from."
	).default(Source.CoinAPI)

@InlineOnly
inline fun DataAggregation.Companion.getOutputPathOption(parser: ArgParser) = parser.outputPathOption

@PublishedApi
internal val ArgParser.outputPathOption get() =
	option(
		ArgType.String,
		fullName = "output-path",
		shortName = "o",
		description = "A path to the file or directory to write data to."
	)

@InlineOnly
inline fun DataAggregation.Companion.getApiKeyOption(parser: ArgParser) = parser.apiKeyOption

@PublishedApi
internal val ArgParser.apiKeyOption get() =
	option(
		ArgType.String,
		fullName = "api-key",
		shortName = "a",
		description = ApiKeyDescription
	)

private const val ApiKeyDescription =
	"The API key to use when connecting to the source. If no value is provided," +
	" it will be pulled from an environment variable."

@InlineOnly
inline fun DataAggregation.Companion.getIntervalOption(parser: ArgParser) = parser.intervalOption

@PublishedApi
internal val ArgParser.intervalOption get() =
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

@InlineOnly
inline fun DataAggregation.Companion.getEntryLimitOption(parser: ArgParser) = parser.entryLimitOption

@PublishedApi
internal val ArgParser.entryLimitOption get() =
	option(
		ArgType.Int,
		fullName = "entry-limit",
		shortName = "l",
		description = "How many entries should be downloaded."
	).default(-1)

@InlineOnly
inline fun DataAggregation.Companion.getModeOption(parser: ArgParser) = parser.modeOption

@PublishedApi
internal val ArgParser.modeOption get() =
	option(
		ArgType.Choice<Mode>(),
		fullName = "mode",
		shortName = "m"
	).default(Mode.Append)

@InlineOnly
inline fun DataAggregation.Companion.getStartTimeOption(parser: ArgParser) = parser.startTimeOption

@PublishedApi
internal val ArgParser.startTimeOption get() =
	option(
		ArgType.String,
		fullName = "start-time",
		shortName = "s"
	)

@InlineOnly
inline fun DataAggregation.Companion.getEndTimeOption(parser: ArgParser) = parser.endTimeOption

@PublishedApi
internal val ArgParser.endTimeOption get() =
	option(
		ArgType.String,
		fullName = "end-time",
		shortName = "e"
	)

@InlineOnly
inline fun DataAggregation.Companion.getExchangeOption(parser: ArgParser) = parser.exchangeOption

@PublishedApi
internal val ArgParser.exchangeOption get() =
	option(
		ArgType.String,
		fullName = "exchange-name",
		shortName = "E"
	).required()

@InlineOnly
inline fun DataAggregation.Companion.getQuoteOption(parser: ArgParser) = parser.quoteOption

@PublishedApi
internal val ArgParser.quoteOption get() =
	option(
		ArgType.String,
		fullName = "quote-asset",
		shortName = "Q"
	).required()

@InlineOnly
inline fun DataAggregation.Companion.getBaseOption(parser: ArgParser) = parser.baseOption

@PublishedApi
internal val ArgParser.baseOption get() =
	option(
		ArgType.String,
		fullName = "base-asset",
		shortName = "B"
	).required()