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

import strixpyrr.abstrakt.annotations.InlineOnly
import java.util.EnumSet
import java.util.EnumSet.noneOf

internal inline fun <reified E : Enum<E>> EnumSet(): EnumSet<E> = noneOf(E::class.java)

@InlineOnly
internal inline infix fun <E : Enum<E>> EnumSet<E>.set(value: E) { add(value) }

@InlineOnly
internal inline infix fun <E : Enum<E>> EnumSet<E>.unset(value: E) { remove(value) }

@InlineOnly
internal inline infix fun <E : Enum<E>> EnumSet<E>.isSet(value: E) = value in this

@InlineOnly
internal inline infix fun <E : Enum<E>> EnumSet<E>.isNotSet(value: E) = value !in this



@InlineOnly
internal inline infix fun <E : Enum<E>> EnumSet<E>.has(value: E) = value in this