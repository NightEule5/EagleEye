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

interface IModuleCommand<C : IModuleCommand.IValueContainer>
{
	fun populate(parser: ArgParser): C
	
	suspend fun run(values: C)
	
	val subcommand: Subcommand
	
	interface IValueContainer
	{
		// Todo: Potentially do some delegate removal, so we aren't retaining refs
		//  to the options.
		fun clear() { }
	}
}