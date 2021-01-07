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
import kotlinx.coroutines.withContext
import strixpyrr.eagleeye.data.models.Dataset
import strixpyrr.eagleeye.data.statusVerbose
import strixpyrr.eagleeye.data.warnVerbose
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
	override suspend fun store(data: Dataset, path: Path): Boolean
	{
		val file = path.tryResolve() ?: return false
		
		return withContext(Dispatchers.IO)
		{
			try
			{
				file.toFile()
					.outputStream()
					.use(data::encode)
				
				statusVerbose()
				{
					"Data was saved to ${file.toAbsolutePath()}."
				}
				
				true
			}
			catch (e: IOException)
			{
				warnVerbose(e)
				{
					"Writing data to ${file.toAbsolutePath()} failed:"
				}
				
				false
			}
		}
	}
	
	override suspend fun extract(source: Path): Dataset?
	{
		if (source.exists())
		{
			val file = source.tryResolve() ?: return null
			
			return withContext(Dispatchers.IO)
			{
				try
				{
					file.toFile()
						.inputStream()
						.use(Dataset.ADAPTER::decode)
				}
				catch (e: IOException)
				{
					null
				}
			}
		}
		else return null
	}
	
	private fun Path.tryResolve(): Path?
	{
		if (!exists())
		{
			if (extension.isEmpty()) createDirectory()
			else createFile()
		}
		
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
		
		if (extension == "dat" || extension == "bin" || extension.isEmpty())
			return isWritable
		
		return false
	}
	
	private val Path.isWritable get() =
		!exists() || isWritable()
}