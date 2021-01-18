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
package strixpyrr.eagleeye.data.internal

// "Set" is used in the mathematical sense here.
internal inline class DiscontinuousSet<T : Comparable<T>>(
	private val ranges: Array<ClosedRange<T>>
) : Set<ClosedRange<T>>
{
	constructor(ranges: Collection<ClosedRange<T>>) : this(ranges.toTypedArray())
	
	override val size get() = ranges.size
	
	operator fun contains(element: T) = ranges.any { element in it }
	
	override fun contains(element: ClosedRange<T>) =
		ranges.any { element.start in it && element.endInclusive in it }
	
	override fun containsAll(elements: Collection<ClosedRange<T>>) =
		elements.all { it in ranges }
	
	override fun isEmpty() = ranges.isEmpty()
	
	override fun iterator() = ranges.iterator()
}