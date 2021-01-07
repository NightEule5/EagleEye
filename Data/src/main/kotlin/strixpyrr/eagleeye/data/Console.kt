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

import com.github.ajalt.mordant.terminal.TextColors.*

@JvmField internal var Verbose: Boolean = false

internal inline fun warnVerbose(exception: Exception, lazyText: () -> String)
{
	if (Verbose)
	{
		warn(lazyText())
		println()
		warn(exception.stackTraceToString())
	}
}

internal inline fun warnVerbose(lazyText: () -> String)
{
	if (Verbose)
		warn(lazyText())
}

internal fun warnVerbose(text: String)
{
	if (Verbose) warn(text)
}

internal inline fun statusVerbose(lazyText: () -> String)
{
	if (Verbose)
		status(lazyText())
}

internal fun statusVerbose(text: String)
{
	if (Verbose) status(text)
}

internal inline fun errorVerbose(lazyText: () -> String)
{
	if (Verbose)
		error(lazyText())
}

internal fun errorVerbose(text: String)
{
	if (Verbose) error(text)
}

internal fun warn(text: String) = println(yellow(text))

internal fun status(text: String) = println(cyan(text))

internal fun error(text: String) = println(red(text))