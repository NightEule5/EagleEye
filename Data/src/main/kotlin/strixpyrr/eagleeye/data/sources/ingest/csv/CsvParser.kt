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
package strixpyrr.eagleeye.data.sources.ingest.csv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import strixpyrr.eagleeye.data.internal.EnumSet
import strixpyrr.eagleeye.data.internal.has
import strixpyrr.eagleeye.data.internal.set
import strixpyrr.eagleeye.data.internal.unset
import java.io.Closeable
import java.io.Reader
import java.util.EnumSet

// A pared-down, asynchronous CSV parser partially adapted from kotlin-csv, which
// can be found here:
//      https://github.com/doyaaaaaken/kotlin-csv

@Suppress("BlockingMethodInNonBlockingContext") // KTIJ-838
internal class CsvParser(
	private val reader: Reader,
	delimiter: Char = ',',
	escape: Char = '"',
	private val keepOpen: Boolean = false,
	private val autoClose: Boolean = true
) : Closeable
{
	init
	{
		if (!reader.markSupported())
			throw UnsupportedOperationException()
	}
	
	val endOfStream get() = fieldParser.isEos
	
	private val fieldParser = FieldParser(reader, delimiter, escape)
	
	// Header operations
	
	suspend fun readHeader() = readRow().toList()
	
	// Row operations
	
	suspend fun readRow() = flow { readRow() }
	
	private suspend fun FlowCollector<String>.readRow()
	{
		fieldParser.activate()
		{
			while (!isEor && !isEos)
			{
				reader.parseInternal()
				
				emit(value)
			}
		}
	}
	
	// Field operations
	
	suspend fun readField(): String
	{
		fieldParser.parse()
		
		if (fieldParser.isEos && autoClose) close()
		
		return fieldParser.value
	}
	
	private class FieldParser(
		private val reader: Reader,
		private val delimiter: Char,
		private val escape: Char
	)
	{
		@JvmField val scratch = StringBuilder()
		
		val value get() = scratch.run { toString().also { clear() } }
		
		// States
		
		val canUse get() =  !state.has(State.Active)
		
		val isEos: Boolean
			get()
			{
				if (state has State.StreamTerminated)
					return true
				
				if (reader.peek() < 0)
				{
					state set State.StreamTerminated
					
					return true
				}
				
				return false
			}
		
		val isEor get() = state has State.RowTerminated
		
		@JvmField val state = EnumSet<State>()
		
		inline infix fun activate(action: FieldParser.() -> Unit)
		{
			markActivity()
			
			action()
			
			unmarkActivity()
		}
		
		fun markActivity()
		{
			if (!canUse) throw ConcurrentReadException("Already in use")
			
			if (State.StreamTerminated in state)
				error("The stream has been exhausted.")
			
			state set State.Active
		}
		
		fun unmarkActivity() = state unset State.Active
		
		suspend fun parse(): EnumSet<State>
		{
			markActivity()
			
			reader.parseInternal()
			
			unmarkActivity()
			
			return state
		}
		
		suspend fun Reader.parseInternal()
		{
			withContext(Dispatchers.IO)
			{
				var started = true
				
				var c: Int
				
				// Why does this box primitives? :(
				while (run { c = read(); c > -1 })
				{
					// End of stream
					if (c == -1)
					{
						check(started)
						{
							"The stream was already exhausted, which should've " +
							"been caught before this function was invoked."
						}
						
						if (state has State.Escaped)
							throw CsvSyntaxException(
								"The last field in the stream was left unescaped."
							)
						
						state set State.   RowTerminated
						state set State.StreamTerminated
						
						break
					}
					
					when (val char = c.toChar())
					{
						delimiter ->
							if (state has State.Escaped)
								scratch.append(char)
							else
							{
								state unset State.WasEscaped
								
								break
							}
						'\n'      ->
						{
							state unset State.WasEscaped
							
							if (state has State.Escaped)
								throw CsvSyntaxException(
									"An escaped field was left unescaped at the" +
									" end of the current row."
								)
							
							state set State.RowTerminated
							
							break
						}
						'\r' ->
						{
							state unset State.WasEscaped
							
							if (state has State.Escaped)
								throw CsvSyntaxException(
									"An escaped field was left unescaped at the" +
									" end of the current row."
								)
							
							mark(1)
							
							val n = read()
							
							if (n == -1)
								state set State.StreamTerminated
							
							if (n.toChar() != '\n') reset()
							
							state set State.RowTerminated
							
							break
						}
						escape -> when
						{
							state has State.Escaped    ->
							{
								state unset State.Escaped
								state set State.WasEscaped
							}
							state has State.WasEscaped ->
							{
								state unset State.WasEscaped
								
								// Todo: Possibly the wrong way to treat this, as
								//  escaping could also apply to the escape char
								//  itself.
								throw CsvSyntaxException("Fields cannot be escaped twice.")
							}
							else                       ->
							{
								if (!started)
									state set State.Escaped
								else
									throw CsvSyntaxException(
										"Fields cannot be escaped after they start."
									)
							}
						}
						else ->
						{
							if (state has State.WasEscaped)
							{
								state unset State.WasEscaped
								
								throw CsvSyntaxException(
									"A delimiter or end of the row is expected " +
									"after a closing escape."
								)
							}
							
							scratch.append(char)
						}
					}
					
					if (started) started = false
				}
			}
		}
		
		enum class State
		{
			/**
			 * A field is currently being parsed so the parser can't be used until
			 * it becomes inactive.
			 */
			Active,
			
			/**
			 * An escape character occurred at the start of the field. The parser
			 * will ignore delimiters until the field is unescaped, at which point
			 * a delimiter must follow.
			 */
			Escaped,
			
			WasEscaped,
			
			RowTerminated,
			StreamTerminated
		}
	}
	
	// Close pattern
	
	private var closed = false
	
	override fun close()
	{
		if (!closed)
		{
			if (!keepOpen) reader.close()
			
			closed = true
		}
	}
	
	companion object
	{
		/**
		 * Attempts to detect which known delimiter character is being used in the
		 * source [reader], returning `-1` if the [peek] limit is reached, the end
		 * of the stream is reached, or the end of a line is encountered. The Peek
		 * parameter can be tweaked depending on how large the fields are.
		 *
		 * This function obeys delimiter escaping via the provided [escapeChar].
		 */
		suspend fun tryDelimiterDetection(reader: Reader, peek: Int = 32, escapeChar: Char = '"'): Int
		{
			require(peek > 0) { "The peek value must be more than zero." }
			
			require(reader.markSupported()) { "The Reader must support marking." }
			
			return withContext(Dispatchers.IO)
			{
				try // ... finally
				{
					reader.mark(peek)
					
					var escaped = false
					
					for (i in 0 until peek)
					{
						val c = reader.read()
						
						if (c < 0) break
						
						when (val ch = c.toChar())
						{
							',', ';', '\t' ->
							{
								if (!escaped)
									return@withContext ch.toInt()
							}
							escapeChar -> escaped = !escaped
							'\r', '\n' -> break
						}
					}
					
					return@withContext -1
				}
				finally
				{
					reader.reset()
				}
			}
		}
	}
}

internal fun Reader.peek(): Int
{
	mark(1)
	
	val c = read()
	
	reset()
	
	return c
}

internal      class CsvSyntaxException(message: String) : CsvParseException(message)
internal class ConcurrentReadException(message: String) : CsvParseException(message)
internal open class  CsvParseException(message: String) :         Exception(message)

/*
@Suppress("BlockingMethodInNonBlockingContext") // KTIJ-838
internal class CsvParserOld(
	@JvmField internal val reader: Reader,
	private var delimiter: Char = ',',
	private val keepOpen: Boolean = false,
	private val autoClose: Boolean = true
) : Closeable
{
	init
	{
		if (!reader.markSupported())
			throw UnsupportedOperationException()
	}
	
	var currentColumn: Int = 0
		internal set
	
	private val scratch = StringBuilder()
	private var isActive = false
	
	private var delimiterDetected = delimiter != AutoDetectDelimiter
	
	val isAtEnd: Boolean get() = reader.peek() > 0
	
	
	
	suspend fun readNextRow(): Flow<String>
	{
		handleDelimiterDetection()
		
		return flow()
		{
			do readNextFieldInternal()
			while (currentColumn > 0)
		}
	}
	
	// Field-wise operations
	
	suspend fun readNextField(): String
	{
		handleDelimiterDetection()
		
		return readNextFieldInternal()
	}
	
	private suspend fun readNextFieldInternal(): String
	{
		scratch.readTo(delimiter).handleAutoClose()
		
		val v = scratch.toString()
		
		scratch.clear()
		
		return v
	}
	
	private suspend fun StringBuilder.readTo(stopChar: Char): State
	{
		ensureInactivity()
		ensureOpen()
		
		isActive = true
		
		val state = withContext(Dispatchers.IO)
		{
			var c: Int
			
			while (true)
			{
				c = reader.read()
				
				if (c == -1)
				{
					if (isEmpty())
						throw IOException("The stream was already exhausted.")
					return@withContext State.StreamExhausted
				}
				
				when (val char = c.toChar())
				{
					stopChar -> break
					'\n' ->
					{
						currentColumn = 0
						return@withContext State.LineTerminated
					}
					'\r' ->
					{
						reader.mark(1)
						
						val n = reader.read()
						
						currentColumn = 0
						
						when
						{
							n == -1            ->
								return@withContext State.StreamExhausted
							n.toChar() == '\n' ->
								return@withContext State.LineTerminated
							else               -> reader.reset()
						}
					}
					else -> append(char)
				}
			}
			
			currentColumn++
			State.InRow
		}
		
		isActive = false
		
		return state
	}
	
	suspend fun skipFields(n: Int)
	{
		handleDelimiterDetection()
		
		for (i in 0 until n)
		{
			val state = skipTo(delimiter)
			
			state.handleAutoClose()
			
			if (state == State.StreamExhausted)
				break
		}
	}
	
	suspend fun skipField()
	{
		handleDelimiterDetection()
		
		skipTo(delimiter).handleAutoClose()
	}
	
	private suspend fun skipTo(stopChar: Char): State
	{
		ensureInactivity()
		ensureOpen()
		
		isActive = true
		
		val state = withContext(Dispatchers.IO)
		{
			var started = false
			
			var c: Int
			
			while (true)
			{
				c = reader.read()
				
				if (c == -1)
				{
					if (!started)
						throw IOException("The stream was already exhausted.")
					return@withContext State.StreamExhausted
				}
				else if (!started) started = true
				
				when (c.toChar())
				{
					stopChar -> break
					'\n' ->
					{
						currentColumn = 0
						return@withContext State.LineTerminated
					}
					'\r' ->
					{
						reader.mark(1)
						
						val n = reader.read()
						
						currentColumn = 0
						
						when
						{
							n == -1            ->
								return@withContext State.StreamExhausted
							n.toChar() == '\n' ->
								return@withContext State.LineTerminated
							else               -> reader.reset()
						}
					}
				}
			}
			
			currentColumn++
			State.InRow
		}
		
		isActive = false
		
		return state
	}
	
	private suspend fun handleDelimiterDetection()
	{
		if (!delimiterDetected) delimiter = detectDelimiter()
	}
	
	private suspend fun detectDelimiter(limit: Int = 32): Char
	{
		check(limit > 0)
		
		ensureInactivity()
		ensureOpen()
		
		isActive = true
		
		val delimiter =
			withContext(Dispatchers.IO)
			{
				reader.mark(limit)
				
				var delimiter = Char.MIN_VALUE
				
				var escape = false
				
				for (i in 0 until limit)
				{
					val c = reader.read()
					
					if (c > -1)
					{
						when (c.toChar())
						{
							',', ';', '\t' ->
							{
								if (!escape)
								{
									delimiter = c.toChar()
									break
								}
							}
							'"'            -> escape = !escape
							'\n', '\r'     ->
								throw Exception(
									"The delimiter could not be auto-detected: " +
									"the line terminated before any known delim" +
									"iter was encountered."
								)
						}
					}
					else
						throw Exception(
							"The delimiter could not be auto-detected: the end-" +
							"of-stream was reached before a delimiter was found."
						)
				}
				
				reader.reset()
				
				if (delimiter == Char.MIN_VALUE)
					throw Exception(
						"The delimiter could not be auto-detected: the set read" +
						"limit of $limit was exhausted before a known delimiter" +
						" was encountered."
					)
				else delimiter
			}
		
		isActive = false
		
		delimiterDetected = true
		
		return delimiter
	}
	
	private fun ensureInactivity()
	{
		if (isActive)
			throw ConcurrentReadException("This parser is already being read from.")
	}
	
	private fun ensureOpen()
	{
		if (isClosed)
			throw Exception("This parser is closed.")
	}
	
	private fun State.handleAutoClose()
	{
		if (this == State.StreamExhausted && autoClose) close()
	}
	
	private enum class State { InRow, LineTerminated, StreamExhausted }
	
	companion object
	{
		const val AutoDetectDelimiter = Char.MIN_VALUE
	}
	
	// Close pattern
	
	private var isClosed = false
	
	override fun close()
	{
		if (!isClosed)
		{
			if (!keepOpen) reader.close()
			
			isClosed = true
		}
	}
}
*/