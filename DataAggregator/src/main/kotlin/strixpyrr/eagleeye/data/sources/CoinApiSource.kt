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
package strixpyrr.eagleeye.data.sources

import strixpyrr.eagleeye.data.sources.apis.CoinApiClient
import strixpyrr.eagleeye.data.storage.MarketInstant
import strixpyrr.eagleeye.data.storage.MutableDataset

// Todo: Write a function for filling missing times in a dataset in a specified
//  time span, stopping when the request limit is reached. Also allow the user to
//  set a maximum number of requests, in which case the minimum of both the API rate
//  request and user-set request will be taken.

open class CoinApiSource // : IDataSource<CoinApiSource.Session, MarketInstant>
{
	override fun startSession(apiKey: String) = Session(apiKey)
	
	class Session(
		key: String,
		override val dataset: MutableDataset<MarketInstant> = MutableDataset()
	) : ISession<MarketInstant>
	{
		// Todo: The official API sucks ASS, reimplement it.
		private val client = CoinApiClient(key)
		
		private var requestsRemaining = -1
		
		override fun close() = client.close()
	}
	
}