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
package strixpyrr.eagleeye.common.cli

import kotlinx.cli.ArgType
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path

object PathArgument : ArgType<Path>(hasParameter = true)
{
	override val description get() = "{ Path }"
	
	@OptIn(ExperimentalPathApi::class)
	override fun convert(value: kotlin.String, name: kotlin.String) = Path(value)
}