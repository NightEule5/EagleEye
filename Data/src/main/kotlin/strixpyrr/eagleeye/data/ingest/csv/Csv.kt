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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okio.BufferedSource
import strixpyrr.abstrakt.annotations.InlineOnly
import uy.klutter.core.collections.toImmutable
import java.io.Closeable

// A pared-down CSV parser built on top of Okio.

internal class FieldReader(
	private val source: BufferedSource,
	private val delimiter: Char,
	private val keepOpen: Boolean,
	private val autoClose: Boolean
) : Closeable
{
	var isEor = false
		private set
	
	suspend fun readAsync() =
		withContext(Dispatchers.IO) { read() }
	
	fun read(): String
	{
		check(source.isOpen) { "The source is closed." }
		
		val eof = source.indexOf(delimiter)
		val eor = source.indexOf('\n')
		
		val value =
			if (eof < eor)
			{
				// The next delimiter isn't after a newline, so we aren't at the
				// end of a row.
				
				isEor = false
				
				source.readUtf8(eof).also { source.skip(1) }
			}
			else
			{
				// We're at the end of a row, read as a line instead.
				
				isEor = true
				
				source.readUtf8LineStrict()
			}
		
		if (source.exhausted())
			if (autoClose) close()
		
		return value
	}
	
	suspend fun skipAsync() =
		withContext(Dispatchers.IO) { skip() }
	
	fun skip()
	{
		check(source.isOpen) { "The source is closed." }
		
		val eof = source.indexOf(delimiter)
		val eor = source.indexOf('\n')
		
			if (eof < eor)
			{
				// The next delimiter isn't after a newline, so we aren't at the
				// end of a row.
				
				isEor = false
				
				source.skip(eof + 1)
			}
			else
			{
				// We're at the end of a row.
				
				isEor = true
				
				if (eor > -1L)
					source.skip(eor + 1)
				else source.skipRest()
			}
		
		if (source.exhausted())
			if (autoClose) close()
	}
	
	// Closeable
	
	override fun close()
	{
		if (source.isOpen && !keepOpen) source.close()
	}
}

@InlineOnly
private inline fun BufferedSource.indexOf(c: Char) = indexOf(c.toByte())

@InlineOnly
private inline fun BufferedSource.skipRest() = skip(Long.MAX_VALUE)

internal class RowReader(@JvmField internal val fieldReader: FieldReader) : Closeable by fieldReader
{
	fun toTable() = Table(parent = this, header = readToList())
	
	inline fun readWithSkippedColumns(crossinline skip: (Int) -> Boolean) =
		flow()
		{
			var i = -1
			
			while (!fieldReader.isEor)
				if (!skip(++i))
					emit(fieldReader.read())
				else fieldReader.skip()
		}
	
	inline fun readToListWithSkippedColumns(capacity: Int = -1, crossinline skip: (Int) -> Boolean) =
		(if (capacity > -1)
			ArrayList<String>(capacity)
		else ArrayList()).apply { readWithSkippedColumns(this, skip) }
	
	inline fun readWithSkippedColumns(list: MutableList<in String>, crossinline skip: (Int) -> Boolean)
	{
		var i = -1
		
		while (!fieldReader.isEor)
			if (!skip(++i))
				list += fieldReader.read()
			else fieldReader.skip()
	}
	
	fun read() =
		flow()
		{
			while (!fieldReader.isEor)
				emit(fieldReader.read())
		}
	
	@Suppress("NOTHING_TO_INLINE")
	inline fun readToList(capacity: Int = -1) =
		(if (capacity > -1)
			ArrayList<String>(capacity)
		else ArrayList()).apply { read(this) }
	
	fun read(list: MutableList<in String>)
	{
		while (!fieldReader.isEor)
			list += fieldReader.read()
	}
	
	fun readToSequence(): Sequence<String> = rowIterator.sequence
	
	fun skip()
	{
		while (!fieldReader.isEor)
			fieldReader.skip()
	}
	
	internal val rowIterator get() = RowIterator(fieldReader)
	
	internal class RowSequence(
		private val iterator: Iterator<String>
	) : Sequence<String>
	{
		override fun iterator() = iterator
	}
	
	internal class RowIterator(
		private val fieldReader: FieldReader
	) : AbstractIterator<String>()
	{
		override fun computeNext()
		{
			if (fieldReader.isEor) done()
			else setNext(fieldReader.read())
		}
		
		inline val sequence get() = RowSequence(iterator = this)
	}
}

internal class Table(
	@JvmField internal val parent: RowReader,
	header: List<String>
)
{
	val header = header.toImmutable()
	
	fun indexColumns(patterns: Set<Regex>): Set<Int>
	{
		val columns =
			if (patterns is MutableSet)
				patterns
			else patterns.toMutableSet()
		val indices = HashSet<Int>(header.size, 1.0f)
		
		header.forEachIndexed()
		{ i, v ->
			// (Don't match a column more than once.)
			for (column in columns)
				if (column matches v)
				{
					indices += i
					columns -= column
				}
		}
		
		return indices
	}
	
	fun selectColumns(indices: Set<Int>): Flow<List<String>>
	{
		indices.requireWithinBounds()
		
		return flow()
		{
			val columnCount = indices.size
			
			val list =
				parent
					.readToListWithSkippedColumns(
						capacity = columnCount,
						skip = { it !in indices }
					)
			
			emit(list)
		}
	}
	
	private fun Set<Int>.requireWithinBounds()
	{
		val n = header.size
		
		// Todo: Add a "RequireAll" to Abstrakt?
		forEach()
		{ i ->
			require(i < n)
			{
				"All indices must be within the bounds of the header: $i >= $n."
			}
		}
	}
}