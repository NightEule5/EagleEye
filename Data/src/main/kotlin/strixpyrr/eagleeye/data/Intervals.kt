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

import strixpyrr.eagleeye.data.models.Interval
import strixpyrr.eagleeye.data.models.Interval.Denomination.*

fun parseInterval(value: String): Interval
{
	var digitCount = 0
	
	for (c in value) if (c.isDigit()) digitCount++ else break
	
	check(digitCount > 0)
	
	val denomination =
		value.substring(digitCount)
			 .parseDenomination()
	
	val length = value.substring(0 until digitCount).toInt() // [0,n)
	
	return Interval(denomination, length)
}

private fun String.parseDenomination(): Interval.Denomination
{
	val length = length
	
	fun requireExactLengthOrPluralSuffix(n: Int, correct: String)
	{
		require(length == n || this[length - 1].equals('s', ignoreCase = true))
		{
			"The specified interval denomination $this is invalid. Did you mean" +
			" \"$correct\"?"
		}
	}
	
	return when (length)
	{
		3    -> when
		{
			equals("SEC", ignoreCase = true) -> Seconds
			equals("MIN", ignoreCase = true) -> Minutes
			equals("HRS", ignoreCase = true) -> Hours
			equals("DAY", ignoreCase = true) -> Days
			equals("MTH", ignoreCase = true) -> Months
			equals("YRS", ignoreCase = true) -> Years
			else                             ->
				throw IllegalArgumentException(
					"The specified three character interval denomination $this is unknown."
				)
		}
		1    -> when (this[0])
		{
			// This syntax is neat.
			's', 'S' -> Seconds
			'm'      -> Minutes
			'h', 'H' -> Hours
			'd', 'D' -> Days
			'M'      -> Months
			'y', 'Y' -> Years
			else     ->
				throw IllegalArgumentException(
					"The specified single character interval denomination $this is unknown."
				)
		}
		else -> when
		{
			startsWith(prefix = "Second", ignoreCase = true) ->
			{
				requireExactLengthOrPluralSuffix(n = 6, correct = "Seconds")
				
				Seconds
			}
			startsWith(prefix = "Minute", ignoreCase = true) ->
			{
				requireExactLengthOrPluralSuffix(n = 6, correct = "Minutes")
				
				Minutes
			}
			startsWith(prefix = "Hour", ignoreCase = true) ->
			{
				requireExactLengthOrPluralSuffix(n = 4, correct = "Hours")
				
				Hours
			}
			// This case won't actually happen unless the word is plural.
			startsWith(prefix = "Day", ignoreCase = true) ->
			{
				requireExactLengthOrPluralSuffix(n = 3, correct = "Days")
				
				Days
			}
			startsWith(prefix = "Month", ignoreCase = true) ->
			{
				requireExactLengthOrPluralSuffix(n = 5, correct = "Months")
				
				Months
			}
			startsWith(prefix = "Year", ignoreCase = true) ->
			{
				requireExactLengthOrPluralSuffix(n = 4, correct = "Years")
				
				Years
			}
			else ->
				throw IllegalArgumentException(
					"The specified interval denomination $this is unknown."
				)
		}
	}
}