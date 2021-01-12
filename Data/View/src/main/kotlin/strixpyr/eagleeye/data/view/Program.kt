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
package strixpyr.eagleeye.data.view

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgParser.OptionPrefixStyle.GNU
import kotlinx.cli.ArgType
import kotlinx.cli.Subcommand
import kotlinx.cli.default
import kotlinx.coroutines.runBlocking
import strixpyrr.eagleeye.common.IModuleCommand
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path

suspend fun main(parameters: Array<String>)
{
	val parser = ArgParser(ViewCommand.Name, prefixStyle = GNU)
	
	val container = ViewCommand.Container(parser)
	
	parser.parse(parameters)
	
	container.run()
}

@OptIn(ExperimentalPathApi::class)
private suspend fun ViewCommand.Container.run() =
	view(
		datasetPath = Path(datasetPath),
		humanReadable = readable
	)

object ViewCommand : IModuleCommand<ViewCommand.Container>
{
	const val Name = "View"
	const val Desc = ""
	
	override fun populate(parser: ArgParser) = Container(parser)
	
	override suspend fun run(values: Container) = values.run()
	
	override val subcommand: Subcommand get() = ViewSubcommand()
	
	private class ViewSubcommand : Subcommand(Name, Desc)
	{
		private val container = Container(parser = this)
		
		// Todo: I don't like having to use RunBlocking here, but there doesn't
		//  seem to be a good way to do this.
		override fun execute() = runBlocking { container.run() }
	}
	
	class Container(parser: ArgParser) : IModuleCommand.IValueContainer
	{
		val datasetPath by parser.DatasetPathOption
		val readable    by parser.HumanReadableFlag
	}
}

private val ArgParser.DatasetPathOption get() =
	option(
		ArgType.String,
		fullName = "dataset-path",
		shortName = "d"
	).default("")

private val ArgParser.HumanReadableFlag get() =
	option(
		ArgType.Boolean,
		fullName = "human-readable",
		shortName = "H"
	).default(false)

