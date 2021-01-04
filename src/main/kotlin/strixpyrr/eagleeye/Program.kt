// Copyright 2020 Strixpyrr
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
package strixpyrr.eagleeye

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgParser.OptionPrefixStyle.GNU
import kotlinx.cli.Subcommand
import strixpyrr.eagleeye.data.DataAggregation
import strixpyrr.eagleeye.data.aggregateData
import strixpyrr.eagleeye.data.getApiKeyOption
import strixpyrr.eagleeye.data.getSourceOption
import kotlin.system.exitProcess

fun main(parameters: Array<String>)
{
	try
	{
		val parser = ArgParser("EagleEye", prefixStyle = GNU)
		
		parser.subcommands(DataAggregationSubcommand)
		
		parser.parse(parameters)
	}
	catch (e: Exception)
	{
		// Log the exception.
		
		exitProcess(-1)
	}
}

private object DataAggregationSubcommand :
	Subcommand(
		name = "AggregateData",
		actionDescription = "Downloads data from a remote source and aggregates it into a dataset."
	)
{
	private val source = DataAggregation.getSourceOption(parser = this)
	private val apiKey = DataAggregation.getApiKeyOption(parser = this)
	
	override fun execute() =
		aggregateData(DataAggregation(source.value, apiKey.value))
}