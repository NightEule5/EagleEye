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

abstract class DataSource : IDataSource
{
	protected abstract class Result(
		override val errorMessage: String?
	) : IDataSource.IResult
	{
		override val wasSuccessful get() = errorMessage == null
	}
	
	protected class SymbolResult(
		errorMessage: String?,
		symbol: String?,
		actualBaseAsset: String?,
		actualQuoteAsset: String?,
		actualExchange: String?
	) : Result(errorMessage), IDataSource.ISymbolResult
	{
		private val _symbol = symbol
		override val symbol get() = _symbol ?: throw FailedResultAssessException()
		
		private val _actualBaseAsset = actualBaseAsset
		override val actualBaseAsset get() = _actualBaseAsset ?: throw FailedResultAssessException()
		
		private val _actualQuoteAsset = actualQuoteAsset
		override val actualQuoteAsset get() = _actualQuoteAsset ?: throw FailedResultAssessException()
		
		private val _actualExchange = actualExchange
		override val actualExchange get() = _actualExchange ?: throw FailedResultAssessException()
		
		companion object
		{
			@JvmStatic
			fun createError(message: String) =
				SymbolResult(
					errorMessage = message,
					symbol = null,
					actualBaseAsset = null,
					actualQuoteAsset = null,
					actualExchange = null
				)
			
			@JvmStatic
			fun createSuccess(
				symbol: String,
				actualBaseAsset: String,
				actualQuoteAsset: String,
				actualExchange: String
			) = SymbolResult(
					errorMessage = null,
					symbol,
					actualBaseAsset,
					actualQuoteAsset,
					actualExchange
				)
		}
	}
}

class FailedResultAssessException : Exception(
	"Properties of failed results have no value."
)