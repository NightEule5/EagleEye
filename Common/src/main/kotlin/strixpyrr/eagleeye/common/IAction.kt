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
package strixpyrr.eagleeye.common

import kotlinx.cli.ArgParser
import kotlinx.cli.Subcommand
import strixpyrr.eagleeye.common.cli.ActionSubcommand

interface IAction<C : IActionParameterContainer> : ICommandInfoProvider
{
	fun createParameterContainer(parser: ArgParser): C
	
	suspend operator fun invoke(container: C)
	
	val subcommand: Subcommand get() =
		ActionSubcommand(parent = this, name, description)
}