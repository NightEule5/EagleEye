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
package strixpyrr.eagleeye.data.ingest.csv

import kotlinx.coroutines.flow.flow

internal inline fun <T> ColumnSkippingRowReader.read(
	block: ColumnSkippingRowReader.SkippingRowScope.() -> T
) = scope.block()

internal class ColumnSkippingRowReader(
	fieldReader: FieldReader,
	@JvmField internal val skippedColumns: MutableSet<Int>
) : RowReader(fieldReader)
{
	override val scope get() = super.scope as SkippingRowScope
	
	// Because the current column must be kept track of, don't create a new scope
	// if you've already read fields from from the reader. Doing so will probably
	// cause unexpected behavior.
	override fun createScope() = SkippingRowScope()
	
	inner class SkippingRowScope : RowScope()
	{
		private var cur = -1
		
		override fun read(): String?
		{
			if (!hasMore)
				if (nextRow)
				{
					nextRow = false
					cur = -1
				}
				else return null
			
			var i = cur
			
			do
				if (++i !in skippedColumns)
				{
					cur = i
					
					return fieldReader.read()
				}
				else fieldReader.skip()
			while (hasMore)
			
			return null
		}
		
		override fun skip(): Boolean
		{
			if (!hasMore)
				if (nextRow)
				{
					nextRow = false
					cur = -1
				}
				else return false
			
			var i = cur
			
			// Skip all excluded columns, then skip one after.
			do
			{
				fieldReader.skip()
				
				if (++i !in skippedColumns)
				{
					cur = i
					
					return true
				}
			}
			while (hasMore)
			
			return false
		}
		
		// Reads without skipping, while still keeping track of the column.
		fun readNormally(): String?
		{
			val value = super.read()
			
			// Increment the column if we haven't reached to end of the current
			// row, but reset it if we have.
			if (value != null)
				cur++
			else cur = -1
			
			return value
		}
		
		fun readToEndNormally() =
			flow { while (true) emit(readNormally() ?: break) }
		
		fun readToEndIntoListNormally() =
			if (columnCount == -1)
			{
				val list = mutableListOf<String>()
				
				readToEndNormally(list)
				
				columnCount = list.size
				
				list
			}
			else ArrayList<String>(columnCount).apply(::readToEndNormally)
		
		fun readToEndNormally(list: MutableList<in String>)
		{
			while (true) list += readNormally() ?: break
		}
		
		fun readToEndIntoSequenceNormally() =
			sequence { while (true) yield(readNormally() ?: break) }
		
		fun skipNormally() =
			if (super.skip())
			{
				cur++
				
				true
			}
			else
			{
				cur = -1
				
				false
			}
		
		fun skipToEndNormally()
		{
			// lol again
			while (skipNormally()) Unit
		}
	}
	
	fun readNormally()                             = scope.readToEndNormally()
	fun readToListNormally()                       = scope.readToEndIntoListNormally()
	fun readNormally(list: MutableList<in String>) = scope.readToEndNormally(list)
	fun readToSequenceNormally()                   = scope.readToEndIntoSequenceNormally()
	
	fun constrain(inclusionRegex: Regex)
	{
		readHeaderInternal()
		
		val skip = HashSet<Int>(header.size, 1.0f)
		
		// Merge the existing skipped columns by checking if they're still excluded.
		skippedColumns.filterTo(skip)
		{
			it < header.size && !(header[it] matches inclusionRegex)
		}
		
		skippedColumns.clear()
		
		header.forEachIndexed()
		{ i, v ->
			if (!(v.matches(inclusionRegex)))
			{
				skip += i
				header.removeAt(i)
			}
		}
		
		skippedColumns += skip
	}
	
	/*
	override fun read() = flow { readSkipping(consume = { emit(it) }) }
	
	override fun read(list: MutableList<in String>) =
		readSkipping(consume = { list += it })
	
	override fun readToSequence() =
		sequence { readSkipping(consume = { yield(it) }) }
	
	fun readNormally()                             = super.read()
	fun readNormally(list: MutableList<in String>) = super.read(list)
	fun readNormallyToSequence()                   = super.readToSequence()
	
	private inline fun readSkipping(consume: (String) -> Unit)
	{
		var i = -1
		
		while (!fieldReader.isEor)
			if (++i !in skippedColumns)
				consume(fieldReader.read())
			else fieldReader.skip()
	}
	*/
}