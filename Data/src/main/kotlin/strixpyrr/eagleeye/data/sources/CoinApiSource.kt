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

import com.github.ajalt.mordant.terminal.TextColors.yellow
import strixpyrr.eagleeye.data.sources.apis.CoinApiClient
import strixpyrr.eagleeye.data.sources.apis.CoinApiClient.Status.*

open class CoinApiSource(protected val client: CoinApiClient = CoinApiClient()) : DataSource()
{
	override suspend fun getSymbol(apiKey: String, exchange: String, base: String, quote: String): IDataSource.ISymbolResult
	{
		val preparedExchange = exchange.toUpperCase()
		val preparedBase = base.toUpperCase()
		val preparedQuote = quote.toUpperCase()
		val possibleSymbol = "${preparedExchange}_SPOT_${preparedBase}_$preparedQuote"
		
		val symbols = client.getSymbols(apiKey, symbolIdFilter = arrayOf(possibleSymbol))
		
		return if (symbols.status.isSuccess)
		{
			val value = symbols.value!!
			
			if (value.size > 1)
				println(
					yellow(
						"Multiple symbol results were received. The first one " +
						"will be used, which may not be the desired behavior."
					)
				)
			
			val symbol = value.firstOrNull() ?:
				return SymbolResult.createError("No symbol results were received.")
			
			SymbolResult.createSuccess(
				symbol.symbolId,
				symbol.assetIdBase,
				symbol.assetIdQuote,
				symbol.exchangeId
			)
		}
		else SymbolResult.createError(symbols.status.error)
	}
	
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
}