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
import kotlinx.cli.default
import strixpyrr.abstrakt.annotations.InlineOnly
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path

@OptIn(ExperimentalPathApi::class)
suspend fun main(parameters: Array<String>)
{
	val parser = ArgParser("View", prefixStyle = GNU)
	
	val datasetPath by parser.datasetPathOption
	
	parser.parse(parameters)
	
	view(Path(datasetPath))
}

@InlineOnly
inline fun ArgParser.getViewDatasetPathOption() = datasetPathOption

@PublishedApi
internal val ArgParser.datasetPathOption get() =
	option(
		ArgType.String,
		fullName = "dataset-path",
		shortName = "d"
	).default("")