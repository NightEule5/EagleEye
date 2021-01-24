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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okio.BufferedSource
import okio.buffer
import okio.source
import strixpyrr.abstrakt.annotations.InlineOnly
import uy.klutter.core.collections.asReadOnly
import java.io.Closeable
import java.nio.file.Path

// A pared-down CSV parser built on top of Okio.

internal fun Path.openCsv(delimiter: Char) =
	source().buffer().openCsv(delimiter, keepOpen = false, autoClose = true)

internal fun BufferedSource.openCsv(
	delimiter: Char,
	keepOpen: Boolean,
	autoClose: Boolean = true
) = RowReader(
		FieldReader(source = this, delimiter, keepOpen, autoClose)
	)

internal fun openStdInAsCsv(delimiter: Char) =
	RowReader(
		FieldReader(
			System.`in`.source().buffer(),
			delimiter,
			keepOpen = true,
			autoClose = false
		)
	)

internal class FieldReader(
	private val source: BufferedSource,
	private val delimiter: Char,
	private val keepOpen: Boolean,
	private val autoClose: Boolean
) : Closeable
{
	val isEos get() = source.exhausted()
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

internal inline fun <T> RowReader.read(block: RowReader.RowScope.() -> T) = RowScope().block()

// Ok so the Scope variable inheritance got a little wack, but that's fine.
// Shouldn't break™
internal open class RowReader(
	@JvmField internal val fieldReader: FieldReader
) : Closeable by fieldReader
{
	private lateinit var _scope: RowScope
	open val scope
		get() =
			if (::_scope.isInitialized)
				_scope
			else createScope()
	
	open fun read()                             = scope.readToEnd()
	open fun readToList()                       = scope.readToEndIntoList()
	open fun read(list: MutableList<in String>) = scope.readToEnd(list)
	open fun readToSequence()                   = scope.readToEndIntoSequence()
	
	open fun skip() = scope.skipToEnd()
	
	protected open fun createScope() = RowScope()
	
	open inner class RowScope
	{
		inline operator fun <T> invoke(block: RowScope.() -> T) = block()
		
		val hasMore     get() = !fieldReader.isEor
		val hasMoreRows get() = !fieldReader.isEos
		
		@JvmField protected var columnCount = -1
		
		// This is true by default, so if a scope is created when the reader is at
		// the end of a row, the next row is started. When the end of the next row
		// is reached, this will be set to false, leading to the intended behavior
		// of needing to call StartNextRow before reading again.
		// If the stream is exhausted, this property will be false, and a next row
		// will not be allowed to start.
		@JvmField protected var nextRow = hasMoreRows
		
		open fun startNextRow() =
			hasMoreRows.also { nextRow = it }
		
		open fun read() =
			if (hasMore || nextRow)
			{
				nextRow = false
				
				fieldReader.read()
			}
			else null
		
		open fun readToEnd() =
			flow { while (true) emit(read() ?: break) }
		
		open fun readToEndIntoList() =
			if (columnCount == -1)
			{
				val list = mutableListOf<String>()
				
				readToEnd(list)
				
				columnCount = list.size
				
				list
			}
			else ArrayList<String>(columnCount).apply(::readToEnd)
		
		open fun readToEnd(list: MutableList<in String>)
		{
			while (true) list += read() ?: break
		}
		
		open fun readToEndIntoSequence() =
			sequence { while (true) yield(read() ?: break) }
		
		open fun skip() =
			if (hasMore || nextRow)
			{
				nextRow = false
				
				fieldReader.skip()
				
				true
			}
			else false
		
		open fun skipToEnd()
		{
			// lol
			while (skip()) Unit
		}
	}
	
	protected val header: MutableList<String> = mutableListOf()
	
	val hasHeader get() = header.isNotEmpty()
	
	fun readHeader(): List<String>
	{
		readHeaderInternal()
		
		return header.asReadOnly()
	}
	
	protected fun readHeaderInternal()
	{
		if (!hasHeader) read(header)
	}
	
	// fun toTable() = Table(parent = this, header = readToList())
	
	/*
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
	*/
	
	//open fun read() =
	//	flow()
	//	{
	//		while (!fieldReader.isEor)
	//			emit(fieldReader.read())
	//	}
	
	/*
	@Suppress("NOTHING_TO_INLINE")
	inline fun readToList(capacity: Int = -1) =
		(if (capacity > -1)
			ArrayList<String>(capacity)
		else ArrayList()).apply { read(this) }
	 */
	
	//open fun read(list: MutableList<in String>)
	//{
	//	while (!fieldReader.isEor)
	//		list += fieldReader.read()
	//}
	
	//open fun readToSequence(): Sequence<String> = rowIterator.sequence
	
	//open fun skip()
	//{
	//	while (!fieldReader.isEor)
	//		fieldReader.skip()
	//}
	
	// internal val rowIterator get() = RowIterator(fieldReader)
	
	/*
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
	 */
}

/*
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
					
					break
				}
		}
		
		if (columns.isNotEmpty())
		{
			val unmatched = columns.joinToString { it.pattern }
			
			throw Exception(
				"Some columns weren't matched in the header: $unmatched."
			)
		}
		
		return indices
	}
	
	fun indexColumns(names: Set<String>, ignoreCase: Boolean = true): Set<Int>
	{
		val columns =
			if (names is MutableSet)
				names
			else names.toMutableSet()
		val indices = HashSet<Int>(header.size, 1.0f)
		
		header.forEachIndexed()
		{ i, v ->
			// (Don't match a column more than once.)
			for (column in columns)
				if (column.equals(v, ignoreCase))
				{
					indices += i
					columns -= column
					
					break
				}
		}
		
		if (columns.isNotEmpty())
		{
			val unmatched = columns.joinToString()
			
			throw Exception(
				"Some columns weren't matched in the header: $unmatched."
			)
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
 */

/*
internal class TableReader(
	internal val rowReader: RowReader,
	header: List<String> = emptyList()
) : Closeable
{
	internal var header = header
		private set
	
	fun readRow() = rowReader.read()
	
	fun readRowToList() = rowReader.readToList(header.size)
	
	fun readRow(list: MutableList<in String>) = rowReader.read(list)
	
	fun readRowToSequence() = rowReader.readToSequence()
	
	fun skipRow() = rowReader.skip()
	
	fun readHeader() = rowReader.readToList()
	
	inline fun transform(
		create: (FieldReader) -> RowReader,
		transformHeader: (List<String>) -> List<String> = { it }
	) = TableReader(
			create(rowReader.fieldReader),
			transformHeader(header)
		)
	
	override fun close() = rowReader.close()
}
 */