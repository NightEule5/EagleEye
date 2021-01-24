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
package strixpyrr.eagleeye.data.ingest

import strixpyrr.eagleeye.data.ingest.TabularStreamMetadata.Historical.ColumnSpec
import strixpyrr.eagleeye.data.internal.EnumSet
import kotlin.text.RegexOption.IGNORE_CASE

object StreamMetadataPresets
{
	object CryptoDataDownload
	{
		// The volume column in particular is really inconsistent. Usually there's
		// a volume given for each asset in the pair. However, these volume values
		// are ordered inconsistently. The volume Regex will likely need to be set
		// manually with the asset symbol replacing the `\\w+`.
		// In one dataset, the volume was just named "Volume", given in the quote
		// currency, which would've so much more convenient. But their data HAD to
		// to labeled inconsistently. :/
		val Historical get() =
			TabularStreamMetadata.Historical(
				ColumnSpec(
					symbol = "symbol",
					time = "unix( timestamp)?",
					openPrice = "open",
					highPrice = "high",
					lowPrice = "low",
					closePrice = "close",
					volume = "volume( \\w+)?",
					isRegex = true,
					regexOptions = EnumSet(IGNORE_CASE)
				),
				delimiter = ',',
				timeFormat = TimestampFormat.UnixEpoch,
				skippedRows = 1
			)
	}
	
	object CryptoTick
	{
		val Historical get() =
			TabularStreamMetadata.Historical(
				ColumnSpec(
					symbol = "symbol_id",
					time = "time_period_start",
					openPrice = "px_open",
					highPrice = "px_high",
					lowPrice = "px_low",
					closePrice = "px_close",
					volume = "sx_sum",
					isRegex = false
				),
				delimiter = ';',
				timeFormat = TimestampFormat.Iso8601
			)
	}
}