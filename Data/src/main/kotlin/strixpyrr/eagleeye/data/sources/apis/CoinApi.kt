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
package strixpyrr.eagleeye.data.sources.apis

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import okhttp3.OkHttpClient
import retrofit2.*
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import strixpyrr.abstrakt.annotations.InlineOnly
import strixpyrr.eagleeye.data.sources.apis.internal.createOkHttpClient
import strixpyrr.eagleeye.data.sources.apis.internal.sumByLengths
import uy.klutter.core.common.whenNotNull
import java.lang.Integer.parseInt
import java.lang.reflect.Type
import java.time.Instant
import java.util.Date
import kotlin.reflect.KClass

/**
 * An asynchronous, stateless client for the [coinapi.io](https://www.coinapi.io/)
 * REST API. Unlike the official Sdk, this client is idiomatic to users of Kotlin
 * and Java (the official client used snake_case for example), exposes more data
 * (such as the rate limit information and error, which the official client failed
 * to provide at all), uses high-level libraries such as Retrofit and Jackson instead
 * of writing everything out by hand, and is extensible.
 *
 * However, this implementation is by no means complete. It only exposes features I
 * felt would be of use for EagleEye. In the future, this may be fleshed out into
 * its own library though.
 */
@Suppress("SpellCheckingInspection") // For "coinapi.io" in the docs.
open class CoinApiClient protected constructor(retrofit: Retrofit)
{
	constructor(sandbox: Boolean = false)
		: this(createRetrofit(sandbox).build())
	
	protected val service = retrofit.create<CoinApiService>()
	
	open suspend fun getExchangeIds(apiKey: String): Result<Collection<String>> =
		getExchanges(apiKey).map { map(Exchange::exchangeId) }
	
	open suspend fun getExchanges(
		apiKey: String
	) = service.getExchanges(
			apiKey
		).awaitResponse().result
	
	open suspend fun getSymbols(
		apiKey: String,
		symbolIdFilter: Array<String>? = null,
		exchangeIdFilter: Array<String>? = null,
		assetIdFilter: Array<String>? = null
	) = service.getSymbols(
			apiKey,
			symbolIdFilter,
			exchangeIdFilter,
			assetIdFilter
		).awaitResponse().result
	
	open suspend fun getSymbols(
		apiKey: String,
		exchangeId: String
	) = service.getSymbols(
			apiKey,
			exchangeId
		).awaitResponse().result
	
	open suspend fun getHistoricalData(
		apiKey: String,
		symbolId: String,
		periodId: String,
		timeStart: Instant,
		timeEnd: Instant? = null,
		includeEmptyItems: Boolean? = null,
		limit: Int? = null
	) = service.getHistoricalData(
			apiKey,
			symbolId,
			periodId,
			timeStart,
			timeEnd,
			includeEmptyItems,
			limit
		).awaitResponse().result
	
	data class Result<V>(
		val value: V?,
		val status: Status,
		val errorMessage: String?,
		val rateLimit: Int,
		val remaining: Int,
		val requestCost: Int,
		val reset: Instant?
	)
	{
		inline fun <X> map(transform: V.() -> X) =
			Result(
				value?.run(transform),
				status,
				errorMessage,
				rateLimit,
				remaining,
				requestCost,
				reset
			)
	}
	
	enum class Status(val code: Int)
	{
		Success(200),
		BadRequest(400),
		Unauthorized(401),
		Forbidden(403),
		TooManyRequests(429),
		NoData(550);
		
		val isSuccess get() = this == Success
		
		companion object
		{
			@JvmStatic
			infix fun from(code: Int) =
				when(code)
				{
					200 -> Success
					400 -> BadRequest
					401 -> Unauthorized
					403 -> Forbidden
					429 -> TooManyRequests
					550 -> NoData
					else -> error("Unknown HTTP Status Code")
				}
		}
	}
	
	companion object
	{
		@JvmStatic
		protected val <V> Response<V>.result: Result<V> get()
		{
			val value = body()
			val code = code()
			
			var errorMessage: String? = null
			
			errorBody() whenNotNull
			{ error ->
				val e = error.charStream()
				
				val message = e.readText()
				
				errorMessage = jackson.readValue<Error>(e).error
				// errorMessage = jackson.readValue<Error>(error.charStream()).error
			}
			
			val rateLimit: Int
			val remaining: Int
			val requestCost: Int
			val reset: Instant?
			
			headers().run()
			{
				rateLimit   = this["X-RateLimit-Limit"        ] whenNotNull { parseInt(it) } ?: -1
				remaining   = this["X-RateLimit-Remaining"    ] whenNotNull { parseInt(it) } ?: -1
				requestCost = this["X-RateLimit-Request-Cost" ] whenNotNull { parseInt(it) } ?: -1
				reset       = this["X-RateLimit-Reset"        ] whenNotNull { Instant.parse(it) }
			}
			
			if (code == 200 && value == null)
				throw Exception("The request was successful, but no value was returned.")
			
			return Result(value, Status from code, errorMessage, rateLimit, remaining, requestCost, reset)
		}
		
		/**
		 * Creates a suitable [Retrofit.Builder] instance.
		 */
		@JvmStatic
		protected fun createRetrofit(
			sandbox: Boolean = false
		): Retrofit.Builder =
			Retrofit.Builder()
				.baseUrl(
					if (sandbox)
						"https://rest-sandbox.coinapi.io/v1/"
					else "https://rest.coinapi.io/v1/"
				)
				.client(createClient().build())
				.addConverterFactory(
					JacksonConverterFactory
						.create(jackson)
				)
				.addConverterFactory(
					// A converter factory for converting arrays/varargs into comma
					// delimited lists, as Retrofit's "multiple query" formatting
					// isn't compatible with CoinApi (only commas and semicolons).
					object : Converter.Factory()
					{
						override fun stringConverter(
							type: Type,
							annotations: Array<Annotation>,
							retrofit: Retrofit
						): Converter<*, String>?
						{
							if (Query::class in annotations)
							{
								val erased = getRawType(type)
								
								if (erased.isArray && erased.componentType == String::class.java)
								{
									return Converter<Array<String>, String>()
									{
										buildString(it.sumByLengths() + it.size - 1)
										{
											var isFirst = false
											
											for (value in it)
											{
												if (!isFirst)
													append(',') // or ';'
												else isFirst = true
												
												append(value)
											}
										}
									}
								}
							}
							
							return null
						}
						
						@InlineOnly
						private inline operator fun Array<out Annotation>.contains(type: KClass<out Annotation>) =
							any { it.annotationClass == type }
					}
				)
		
		/**
		 * Creates an [OkHttpClient.Builder] instance for [Retrofit], with an added
		 * [okhttp3.Interceptor] that sets the `X-CoinAPI-Key` and `Accept` headers
		 * of a request.
		 */
		@JvmStatic
		protected fun createClient(): OkHttpClient.Builder =
			createOkHttpClient(defaultHeader = "Accept" to "application/json")
		
		private val jackson by lazy()
		{
			jacksonObjectMapper()
				.setPropertyNamingStrategy(
					PropertyNamingStrategy.SNAKE_CASE
				)
				.registerModule(JavaTimeModule())
		}
	}
}

// Service

interface CoinApiService
{
	// Metadata
	
	@GET("exchanges/")
	fun getExchanges(
		@Header("X-CoinAPI-Key")
		apiKey: String
	): Call<Collection<Exchange>>
	
	@GET("exchanges/{$ExchangeId}")
	fun getExchanges(
		@Header(CoinApiKey)
		apiKey: String,
		@Path(ExchangeId)
		exchangeId: String
	): Call<Collection<Exchange>>
	
	@GET("exchanges/")
	fun getExchanges(
		@Header(CoinApiKey)
		apiKey: String,
		@Query(ExchangeIdFilter)
		vararg exchangeIdFilter: String
	): Call<Collection<Exchange>>
	
	@GET("symbols/")
	fun getSymbols(
		@Header(CoinApiKey)
		apiKey: String,
		@Query(SymbolIdFilter)
		symbolIdFilter: Array<String>? = null,
		@Query(ExchangeIdFilter)
		exchangeIdFilter: Array<String>? = null,
		@Query(AssetIdFilter)
		assetIdFilter: Array<String>? = null
	): Call<Collection<Symbol>>
	
	@GET("symbols/{$ExchangeId}")
	fun getSymbols(
		@Header(CoinApiKey)
		apiKey: String,
		@Path(ExchangeId)
		exchangeId: String
	): Call<Collection<Symbol>>
	
	// Data
	
	@GET("ohlcv/{$SymbolId}/history")
	fun getHistoricalData(
		@Header(CoinApiKey)
		apiKey: String,
		@Path(SymbolId)
		symbolId: String,
		@Query(PeriodId)
		periodId: String,
		@Query(TimeStart)
		timeStart: Instant,
		@Query(TimeEnd)
		timeEnd: Instant? = null,
		@Query(IncludeEmptyItems)
		includeEmptyItems: Boolean? = null,
		@Query(Limit)
		limit: Int? = null
	): Call<Collection<HistoricalOhlcvData>>
	
	private companion object
	{
		private const val CoinApiKey = "X-CoinAPI-Key"
		
		private const val ExchangeId = "exchange_id"
		private const val SymbolId = "symbol_id"
		private const val PeriodId = "period_id"
		private const val TimeStart = "time_start"
		private const val TimeEnd = "time_end"
		private const val Limit = "limit"
		private const val IncludeEmptyItems = "include_empty_items"
		
		private const val AssetIdFilter = "filter_asset_id"
		private const val ExchangeIdFilter = "filter_exchange_id"
		private const val SymbolIdFilter = "filter_symbol_id"
	}
}

// Data

data class Exchange(
	val exchangeId: String,
	val website: String,
	val name: String,
	val dataStart: Date,
	val dataEnd: Date,
	val dataQuoteStart: Instant,
	val dataQuoteEnd: Instant,
	val dataOrderbookStart: Instant,
	val dataOrderbookEnd: Instant,
	val dataTradeStart: Instant,
	val dataTradeEnd: Instant,
	val dataSymbolsCount: Int,
	@JsonAlias("volume_1hrs_usd")
	val volume1hrsUsd: Double,
	@JsonAlias("volume_1day_usd")
	val volume1dayUsd: Double,
	@JsonAlias("volume_1mth_usd")
	val volume1mthUsd: Double
)

data class Symbol(
	val symbolId: String,
	val exchangeId: String,
	val symbolType: SymbolType,
	val assetIdBase: String,
	val assetIdQuote: String,
	val dataStart: Date,
	val dataEnd: Date,
	val dataQuoteStart: Instant,
	val dataQuoteEnd: Instant,
	val dataOrderbookStart: Instant,
	val dataOrderbookEnd: Instant,
	val dataTradeStart: Instant,
	val dataTradeEnd: Instant,
	@JsonAlias("volume_1hrs")
	val volume1hrs: Double,
	@JsonAlias("volume_1hrs_usd")
	val volume1hrsUsd: Double,
	@JsonAlias("volume_1day")
	val volume1day: Double,
	@JsonAlias("volume_1day_usd")
	val volume1dayUsd: Double,
	@JsonAlias("volume_1mth")
	val volume1mth: Double,
	@JsonAlias("volume_1mth_usd")
	val volume1mthUsd: Double,
	val price: Double
)

enum class SymbolType(@JsonValue val value: String)
{
	Spot     ("SPOT"     ),
	Futures  ("FUTURES"  ),
	Option   ("OPTION"   ),
	Perpetual("PERPETUAL"),
	Index    ("INDEX"    ),
	Credit   ("CREDIT"   )
}

data class HistoricalOhlcvData(
	val timePeriodStart: Instant,
	val timePeriodEnd: Instant,
	val timeOpen: Instant,
	val timeClose: Instant,
	val priceOpen: Double,
	val priceHigh: Double,
	val priceLow: Double,
	val priceClose: Double,
	val volumeTraded: Double,
	val tradesCount: Int
)

internal data class Error(
	val error: String?,
	@[JsonAlias("faq_0") JsonIgnore]
	val faq0: String?,
	@[JsonAlias("faq_1") JsonIgnore]
	val faq1: String?,
	@[JsonAlias("faq_2") JsonIgnore]
	val faq2: String?,
	@[JsonAlias("faq_3") JsonIgnore]
	val faq3: String?
)