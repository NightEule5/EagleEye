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

import com.github.ajalt.mordant.rendering.TextStyle
import strixpyrr.abstrakt.annotations.InlineOnly
import strixpyrr.eagleeye.common.ConsoleContentScope.*
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@InlineOnly
inline fun console(
	indentationType: IndentationType = IndentationType.Tabs,
	style: TextStyle = ConsoleContentScope.DefaultStyle,
	format: ConsoleContentScope.() -> Unit
): ConsoleContentScope
{
	contract {
		callsInPlace(format, InvocationKind.EXACTLY_ONCE)
	}
	
	return console(ConsoleContentScope(indentationType, style), format)
}

@InlineOnly
inline fun console(
	currentScope: ConsoleContentScope = ConsoleContentScope(),
	format: ConsoleContentScope.() -> Unit
): ConsoleContentScope
{
	contract {
		callsInPlace(format, InvocationKind.EXACTLY_ONCE)
	}
	
	currentScope.format()
	
	return currentScope
}

class ConsoleContentScope(
	indentationType: IndentationType = IndentationType.Tabs,
	style: TextStyle = DefaultStyle
)
{
	var indentation = "\t"
		private set
	var indentLevel = 0
		private set
	
	var indentationType = indentationType
		set(value)
		{
			indentation = when(value)
			{
				IndentationType.Tabs       -> "\t"
				IndentationType.TwoSpaces  -> "  "
				IndentationType.FourSpaces -> "    "
			}
			
			field = value
		}
	
	private val baseStyle = style
	var currentStyle      = style
	
	fun indent() = ++indentLevel
	fun dedent() = --indentLevel
	
	fun indent(levelIncrease: Int): Int
	{
		check(levelIncrease > 0)
		
		indentLevel += levelIncrease
		
		return indentLevel
	}
	
	fun dedent(levelDecrease: Int): Int
	{
		check(levelDecrease > 0)
		check(levelDecrease <= indentLevel)
		
		indentLevel -= levelDecrease
		
		return indentLevel
	}
	
	fun clearIndent() { indentLevel = 0 }
	
	inline fun indented(format: () -> Unit)
	{
		contract {
			callsInPlace(format, InvocationKind.EXACTLY_ONCE)
		}
		
		indent()
		format()
		dedent()
	}
	
	enum class IndentationType { Tabs, TwoSpaces, FourSpaces }
	
	inline fun styledWith(style: TextStyle, format: () -> Unit)
	{
		contract {
			callsInPlace(format, InvocationKind.EXACTLY_ONCE)
		}
		
		val lastStyle = currentStyle
		
		currentStyle += style
		
		format()
		
		currentStyle = lastStyle
	}
	
	inline fun TextStyle.invoke(format: () -> Unit)
	{
		contract {
			callsInPlace(format, InvocationKind.EXACTLY_ONCE)
		}
		
		styledWith(this@invoke, format)
	}
	
	fun resetStyle() { currentStyle = baseStyle }
	
	private var lineStarted = false
	
	fun startLine(): Boolean
	{
		if (!lineStarted)
		{
			for (i in 0 until indentLevel)
				print(indentation)
			
			lineStarted = true
			
			return false
		}
		
		return true
	}
	
	fun endLine() = println().also { lineStarted = false }
	
	fun printFragment(style: TextStyle, value: String)
	{
		startLine()
		
		print((currentStyle + style)(value))
	}
	
	fun printFragment(value: String)
	{
		startLine()
		
		print(currentStyle(value))
	}
	
	operator fun String.invoke() = printFragment(this)
	
	fun printLine(style: TextStyle, value: String)
	{
		startLine()
		printFragment(style, value)
		  endLine()
	}
	
	fun printLine(value: String)
	{
		startLine()
		printFragment(value)
		  endLine()
	}
	
	fun finishLine(value: String)
	{
		printFragment(value)
		endLine()
	}
	
	companion object
	{
		@[JvmField PublishedApi]
		internal val DefaultStyle = TextStyle()
	}
}