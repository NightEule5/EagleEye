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

interface IDataSource
{
	// Todo: Add some way of conveying how many requests are left, and any errors
	//  that come up.
	
	suspend fun getSymbol(apiKey: String, exchange: String, base: String, quote: String): ISymbolResult
	
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
}