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

@Suppress("DELEGATED_MEMBER_HIDES_SUPERTYPE_OVERRIDE")
open class MutableDataset<D>(override val data: MutableList<D> = mutableListOf()) : Dataset<D>(data), IMutableDataset<D>, MutableList<D> by data
{
	// Todo: Does adding 1 make the end inclusive for subList?
	override fun subset(start: Int, end: Int) = MutableDataset(data.subList(start, end + 1))
}



// Todo: The subset should be a view of the original, like the immutable variant.
inline fun <D> MutableDataset<D>.subset(predicate: (D) -> Boolean) =
	MutableDataset(data.filterTo(ArrayList(data.size), predicate))