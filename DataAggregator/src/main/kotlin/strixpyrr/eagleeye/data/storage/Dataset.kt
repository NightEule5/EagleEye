// Copyright 2020 Strixpyrr
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

import uy.klutter.core.collections.asReadOnly

open class Dataset<D>(override val data: List<D>) : IDataset<D>, List<D> by data
{
	// Todo: Does adding 1 make the end inclusive for subList?
	override fun subset(start: Int, end: Int) = Dataset(data.subList(start, end + 1))
}

inline fun <D> Dataset<D>.subset(predicate: (D) -> Boolean) =
	Dataset(data.filterTo(ArrayList(data.size), predicate).asReadOnly())