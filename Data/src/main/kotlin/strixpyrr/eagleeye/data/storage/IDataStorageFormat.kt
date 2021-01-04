// Copyright 2020-2021 Strixpyrr
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

import strixpyrr.eagleeye.data.storage.models.StoredData
import java.nio.file.Path

interface IDataStorageFormat
{
	fun canStoreTo(path: Path): Boolean
	
	/**
	 * @param path The path to write the data to. If this is a directory, either a
	 *  file with the same name will be placed there or a directory structure will
	 *  be created. If this is a file, either a raw binary file or an archive will
	 *  be created.
	 */
	suspend fun store(data: StoredData, path: Path): Boolean
	
	suspend fun extract(source: Path): StoredData?
}