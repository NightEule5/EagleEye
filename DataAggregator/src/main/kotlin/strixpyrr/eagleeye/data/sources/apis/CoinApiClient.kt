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
package strixpyrr.eagleeye.data.sources.apis

import io.coinapi.rest.REST_methods

/**
 * A *very* partial reimplementation of the CoinApi.io official Java API which,
 * with all due respect, is poorly designed. In particular, this implementation
 * exposes the concept of rate requests.
 *
 * Any API calls needed that aren't covered here can be handled via [REST_methods].
 * This isn't ideal, since
 */
open class CoinApiClient(apiKey: String) : REST_methods(apiKey)
{
	var remainingRequests = -1; protected set
	
	
}