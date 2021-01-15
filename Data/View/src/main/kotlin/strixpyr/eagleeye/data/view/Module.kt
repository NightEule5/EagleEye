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
import kotlinx.cli.ArgType
import kotlinx.cli.default
import strixpyrr.eagleeye.common.IAction
import strixpyrr.eagleeye.common.IActionParameterContainer
import strixpyrr.eagleeye.common.IModule
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path

object ViewModule : IModule, IAction<ViewModule.Container>
{
	const val Name = "View"
	const val Desc = ""
	
	override val name        get() = Name
	override val description get() = Desc
	
	// IModule
	
	override fun populate(parser: ArgParser) { }
	
	// IAction
	
	override val subcommand
		get() = super<IAction>.subcommand
	
	@[OptIn(ExperimentalPathApi::class) JvmStatic]
	internal suspend fun Container.run() =
		view(
			datasetPath = Path(datasetPath),
			humanReadable = readable
		)
	
	override fun createParameterContainer(parser: ArgParser) = Container(parser)
	
	override suspend fun invoke(container: Container) = container.run()
	
	class Container(parser: ArgParser) : IActionParameterContainer
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