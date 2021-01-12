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
import java.time.Instant

interface IDataSource
{
	val limitRequestFactor: Int
	
	// Todo: Add some way of conveying how many requests are left, and any errors
	//  that come up.
	
	fun toSourceNotation(interval: Interval): String
	
	fun toPossibleSymbol(base: String, quote: String, exchange: String): String
	
	suspend fun getSymbol(
		apiKey: String,
		possibleSymbol: String
	): ISymbolResult
	
	suspend fun getSymbol(
		apiKey: String,
		exchange: String,
		base: String,
		quote: String
	): ISymbolResult
	
	suspend fun getHistoricalData(
		apiKey: String,
		symbol: String,
		interval: String,
		timeStart: Instant,
		timeEnd: Instant? = null,
		limit: Int? = null
	): IHistoricalDataResult
	
	interface IResult
	{
		val errorMessage: String?
		val wasSuccessful: Boolean
	}
	
	interface ISymbolResult : IResult
	{
		val symbol: String
		val actualBaseAsset: String
		val actualQuoteAsset: String
		val actualExchange: String
		
		operator fun component1() = symbol
		operator fun component2() = actualBaseAsset
		operator fun component3() = actualQuoteAsset
		operator fun component4() = actualExchange
	}
	
	interface IHistoricalDataResult : IResult
	{
		val points: Collection<IPoint>
		
		interface IPoint
		{
			// The start time of this point.
			val time: Instant
			val open: Double
			val high: Double
			val low: Double
			val close: Double
			val volume: Double
			val trades: Int
			
			operator fun component1() = time
			operator fun component2() = open
			operator fun component3() = high
			operator fun component4() = low
			operator fun component5() = close
			operator fun component6() = volume
			operator fun component7() = trades
		}
	}
}