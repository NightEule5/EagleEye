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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import strixpyrr.eagleeye.data.storage.TransparentStorageFormat
import strixpyrr.eagleeye.data.storage.manipulation.modifiedBy
import java.nio.file.Path

internal suspend fun prune(input: Path, output: Path, inclusion: DatasetFilter, exclusion: DatasetFilter)
{
	if (!TransparentStorageFormat.canStoreTo(output))
		throw PrunerException(
			"The path specified is not a valid path to store data in: $output " +
			"was rejected by TransparentStorageFormat."
		)
	
	coroutineScope()
	{
		// Extract and decode the data from the specified path.
		val storedDataset = withContext(Dispatchers.IO)
		{
			TransparentStorageFormat.extract(input)
		}
		
		val dataset = storedDataset modifiedBy
		{
			forEachSymbol()
			{ symbols ->
				val symbol = symbol
				
				if (symbol !in inclusion || symbol in exclusion)
					symbols.remove()
				else
					forEachInterval()
					{ intervals ->
						val interval = interval
						
						if (interval !in inclusion || interval in exclusion)
							intervals.remove()
						else
						{
							val times = points.keys.iterator()
							
							for (time in times)
								if (time !in inclusion || time in exclusion)
									times.remove()
						}
					}
			}
		}
		
		if (!TransparentStorageFormat.store(dataset, output))
			throw PrunerException("Storage of the new dataset failed.")
	}
}

class PrunerException : RuntimeException
{
	constructor(                                 ) : super(              )
	constructor(message: String                  ) : super(message       )
	constructor(message: String, cause: Throwable) : super(message, cause)
	constructor(                 cause: Throwable) : super(         cause)
}