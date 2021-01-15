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

/**
 * A [Subcommand] implementation that, upon execution, resumes a coroutine.
 */
// This was just an experiment. It's certainly faster for whatever reason, but it
// was quite finicky. See ArgParser.kt for the rest of the implementation; you'll
// see what I mean.
/*abstract class ContinuingSubcommand<C : Any>(
	name: String, desc: String,
) : Subcommand(name, desc)
{
	lateinit var continuation: Continuation<C>
	
	protected abstract val container: C
	
	override fun execute() = continuation.resume(container)
}*/