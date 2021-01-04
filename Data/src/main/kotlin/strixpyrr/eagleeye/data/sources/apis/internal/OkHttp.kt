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
package strixpyrr.eagleeye.data.sources.apis.internal

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import strixpyrr.abstrakt.annotations.InlineOnly

@InlineOnly
inline fun createOkHttpClient(
	defaultHeader: Pair<String, String>
): OkHttpClient.Builder =
	OkHttpClient.Builder()
		.addInterceptor(
			SingleHeaderInterceptor(defaultHeader)
		)

// Interceptors

@InlineOnly
inline fun SingleHeaderInterceptor(defaultHeader: Pair<String, String>) =
	SingleHeaderInterceptor(defaultHeader.first, defaultHeader.second)

class SingleHeaderInterceptor(private val defaultHeaderKey: String, private val defaultHeaderValue: String) : Interceptor
{
	override fun intercept(chain: Interceptor.Chain): Response =
		chain.proceed(
			chain.request()
				.newBuilder()
				.header(defaultHeaderKey, defaultHeaderValue)
				.build()
		)
}

class HeaderInterceptor(private val defaultHeaders: Map<String, String>) : Interceptor
{
	override fun intercept(chain: Interceptor.Chain): Response =
		chain.proceed(
			chain.request()
				.newBuilder()
				.apply()
				{
					for ((key, value) in defaultHeaders)
						header(key, value)
				}
				.build()
	)
}