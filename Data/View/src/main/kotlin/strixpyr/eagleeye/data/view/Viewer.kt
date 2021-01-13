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

import com.github.ajalt.mordant.terminal.TextColors.green
import strixpyrr.eagleeye.common.ConsoleContentScope
import strixpyrr.eagleeye.common.console
import strixpyrr.eagleeye.data.models.Dataset
import strixpyrr.eagleeye.data.models.Interval
import strixpyrr.eagleeye.data.models.MarketInstant
import strixpyrr.eagleeye.data.storage.TransparentStorageFormat
import uy.klutter.core.common.withNotNull
import java.nio.file.Path
import java.util.TreeMap

suspend fun view(datasetPath: Path, humanReadable: Boolean)
{
	val dataset = TransparentStorageFormat.extract(datasetPath.toAbsolutePath())
	
	console(style = green)
	{
		if (dataset == null)
		{
			printLine("No data found.")
			return
		}
		
		if (humanReadable)
			displayAll(dataset)
		else
			printLine(dataset.toString())
	}
}

private fun ConsoleContentScope.displayAll(dataset: Dataset, enableIndex: Boolean = true, dataPointLimit: Int = 21)
{
	require(dataPointLimit > 0) { "The data point limit must be more than 0, but it was $dataPointLimit." }
	
	if (enableIndex)
	{
		"Index:"()
		
		dataset.index withNotNull
		{
			endLine()
			
			indented()
			{
				printLine("Terms:")
				
				val symbols = symbols
				val exchanges = HashSet<Int>()
				val assets = symbols.values.flatMapTo(HashSet())
				{
					exchanges += it.exchange
					
					listOf(it.heldAsset, it.tradedAsset)
				}
				
				val map = terms.entries.associateTo(TreeMap<Int, Pair<String, TermType>>())
				{ (k, v) ->
					v to
						(k to
							when (v)
							{
								in symbols   -> TermType.Symbol
								in exchanges -> TermType.Exchange
								in assets    -> TermType.Asset
								else         -> TermType.Unknown
							}
						)
				}
				
				val alignedLength = map.values.maxOf { (id, _) -> id.length }
				
				indented()
				{
					for ((index, term) in map)
					{
						val (id, type) = term
						
						val padding = ' '.repeat(alignedLength - id.length)
						
						printLine("[$index] -> $id$padding ($type)")
					}
				}
				
				printEmptyLine()
				printLine("Symbol Metadata:")
				
				indented()
				{
					for ((index, meta) in symbols)
					{
						val quote    = meta.heldAsset
						val base     = meta.tradedAsset
						val exchange = meta.exchange
						val type     = meta.type
						
						var   symbolName = ""
						var    quoteName = ""
						var     baseName = ""
						var exchangeName = ""
						
						for ((name, i) in terms)
							when (i)
							{
								index    -> symbolName = name
								quote    -> quoteName = name
								base     -> baseName = name
								exchange -> exchangeName = name
							}
						
						// Todo: Add alignment whitespace.
						
						printLine("$symbolName ->")
						
						indented()
						{
							printLine("Quote   : [$quote] ($quoteName)"      )
							printLine("Base    : [$base] ($baseName)"        )
							printLine("Exchange: [$exchange] ($exchangeName)")
							printLine("Type    : $type"                      )
						}
					}
				}
			}
		} ?: finishLine(" No data.")
		
		printEmptyLine()
	}
	
	printLine("Symbol data:")
	
	indented()
	{
		val limit = dataPointLimit - 1
		
		for ((i, sym) in dataset.symbols)
		{
			printLine("[$i] ->")
			
			indented()
			{
				for (flow in sym.data_)
				{
					val interval = flow.interval.format()
					
					printLine("$interval:")
					
					indented()
					{
						val start = flow.start
						val end   = flow.end
						
						printLine(
							"Allowed Time Range: " +
								if (start == null)
									if (end == null)
										"None."
									else "(,$end]"
								else "[$start,)"
						)
						
						printEmptyLine()
						
						fun MarketInstant.print()
						{
							
							val time = time?.toString() ?: "<Undefined>"
							
							printLine(
								"[$time] Open: $openPrice | High: $highPrice | Low: $lowPrice | Close: $closePrice | Volume: $volume"
							)
						}
						
						var truncated = false
						val points = flow.points.run()
						{
							if (size > dataPointLimit)
							{
								truncated = true
								subList(0, dataPointLimit - 1) // [0,l)
							}
							else this
						}
						
						points.forEach(MarketInstant::print)
						
						if (truncated)
						{
							indented { printLine("...") }
							flow.points.last().print()
						}
					}
				}
			}
		}
	}
}

private fun Interval?.format() =
	if (this == null)
		"<Undefined>"
	else length.toString() + when (denomination)
	{
		Interval.Denomination.Seconds -> " Second"
		Interval.Denomination.Minutes -> " Minute"
		Interval.Denomination.Hours   -> " Hour"
		Interval.Denomination.Days    -> " Day"
		Interval.Denomination.Months  -> " Month"
		Interval.Denomination.Years   -> " Year"
	}

private enum class TermType { Unknown, Symbol, Asset, Exchange }

private fun Char.repeat(n: Int) =
	String(CharArray(n) { this })