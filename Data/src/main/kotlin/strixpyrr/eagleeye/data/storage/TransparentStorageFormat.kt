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
package strixpyrr.eagleeye.data.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import strixpyrr.eagleeye.data.storage.models.StoredData
import uy.klutter.core.common.exists
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.*

@OptIn(ExperimentalPathApi::class)
object TransparentStorageFormat : IDataStorageFormat
{
	/**
	 * @throws SecurityException Read access is denied
	 */
	override fun canStoreTo(path: Path) =
		when
		{
			!path.exists() ||
				path.isRegularFile() -> path.isValidFile
			path.isDirectory()       ->
			{
				val name = path.name
				val namesake = path / "$name.dat"
				
				namesake.isWritable
			}
			else                     -> false
		}
	
	/**
	 * @throws SecurityException Read access is denied
	 */
	override suspend fun store(data: StoredData, path: Path): Boolean
	{
		val file = path.tryResolve() ?: return false
		
		return coroutineScope()
		{
			withContext(Dispatchers.IO)
			{
				try
				{
					file.toFile()
						.outputStream()
						.use(data::encode)
					
					true
				}
				catch (e: IOException)
				{
					false
				}
			}
		}
	}
	
	override suspend fun extract(source: Path): StoredData?
	{
		if (source.exists())
			TODO("Not yet implemented")
		else return null
	}
	
	private fun Path.tryResolve(): Path?
	{
		if (isDirectory())
		{
			val namesake = this / "$name.dat"
			
			return if (namesake.isWritable) namesake else null
		}
		
		if (isRegularFile())
		{
			return if (isValidFile) this else null
		}
		
		return null
	}
	
	private val Path.isValidFile get(): Boolean
	{
		val extension = extension
		
		if (extension == "dat" || extension == "bin" || extension == "")
			return isWritable
		
		return false
	}
	
	private val Path.isWritable get() =
		!exists() || isWritable()
}