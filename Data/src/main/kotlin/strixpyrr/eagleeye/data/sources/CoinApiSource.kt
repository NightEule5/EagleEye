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
package strixpyrr.eagleeye.data.sources

import strixpyrr.eagleeye.data.models.Interval
import strixpyrr.eagleeye.data.sources.apis.CoinApiClient
import strixpyrr.eagleeye.data.sources.apis.CoinApiClient.Status.*
import strixpyrr.eagleeye.data.warnVerbose
import java.time.Instant

open class CoinApiSource(protected val client: CoinApiClient = CoinApiClient()) : DataSource()
{
	override val limitRequestFactor get() = 100
	
	override fun toSourceNotation(interval: Interval) = interval.coinApiNotation
	
	override fun toPossibleSymbol(base: String, quote: String, exchange: String) =
		(exchange + "_SPOT_" + base + "_" + quote).toUpperCase()
	
	override suspend fun getSymbol(apiKey: String, exchange: String, base: String, quote: String) =
		getSymbol(apiKey, toPossibleSymbol(base, quote, exchange))
	
	override suspend fun getSymbol(apiKey: String, possibleSymbol: String): IDataSource.ISymbolResult
	{
		val symbols = client.getSymbols(apiKey, symbolIdFilter = arrayOf(possibleSymbol))
		
		return if (symbols.status.isSuccess)
		{
			val value = symbols.value!!
			
			if (value.size > 1)
				warnVerbose()
				{
					val results = value.joinToString { it.symbolId }
					
					"Multiple symbol results were received: $results. " +
						if (value.any { it.symbolId == possibleSymbol })
							"The data with an exact symbol Id match ($possibleSymbol)" +
								" will be used."
						else
							"The first one will be used, which may not be the desired" +
								" behavior."
				}
			
			// Todo: I don't see how the first filter would every fail, since the
			//  symbol to look for was provided in the request.
			val symbol =
				value.firstOrNull { it.symbolId == possibleSymbol } ?:
				value.firstOrNull() ?:
				return SymbolResult.createError("No symbol results were received.")
			
			SymbolResult.createSuccess(
				symbol.symbolId,
				symbol.assetIdBase,
				symbol.assetIdQuote,
				symbol.exchangeId
			)
		}
		else SymbolResult.createError(symbols.error)
	}
	
	override suspend fun getHistoricalData(apiKey: String, symbol: String, interval: String, timeStart: Instant, timeEnd: Instant?, limit: Int?): IDataSource.IHistoricalDataResult
	{
		val data = client.getHistoricalData(apiKey, symbol, interval, timeStart, timeEnd, limit = limit)
		
		return if (data.status.isSuccess)
		{
			val value =
				data.value!!
					.map()
					{
						HistoricalDataResult.Point(
							it.timePeriodStart,
							it.priceOpen,
							it.priceHigh,
							it.priceLow,
							it.priceClose,
							it.volumeTraded,
							it.tradesCount
						)
					}
			
			HistoricalDataResult.createSuccess(value)
		}
		else HistoricalDataResult.createError(data.error)
	}
	
	protected val CoinApiClient.Result<*>.error get() =
		"The server returned an error (${status.code}): $errorMessage."
	
	protected val CoinApiClient.Status.error get() =
		when (this)
		{
			BadRequest      -> "The request was invalid (HTTP status code ${BadRequest.code})."
			Unauthorized    -> "The API key was incorrect (HTTP status code ${Unauthorized.code})."
			Forbidden       -> "The API key doesn't have permission for the requested resource (HTTP status code ${Forbidden.code})."
			TooManyRequests -> "The rate limit was exceeded (HTTP status code ${TooManyRequests.code})."
			NoData          -> "No data was available (HTTP status code ${NoData.code})."
			Success         -> error("A successful status has no error message.")
		}
	
	companion object
	{
		val Interval.coinApiNotation get() =
			length.toString() +
				when (denomination)
				{
					Interval.Denomination.Seconds -> "SEC"
					Interval.Denomination.Minutes -> "MIN"
					Interval.Denomination.Hours   -> "HRS"
					Interval.Denomination.Days    -> "DAY"
					Interval.Denomination.Months  -> "MTH"
					Interval.Denomination.Years   -> "YRS"
				}
	}
}