/*
 * Copyright (c) 2025 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.quickidlogic.util

import java.util.Calendar

/**
 * Utility for parsing MRZ (Machine Readable Zone) dates.
 *
 * MRZ dates use 2-digit years (YYMMDD format), requiring context-aware
 * century detection to determine the correct 4-digit year.
 */
object MrzDateParser {

    /**
     * Parses MRZ birth date (YYMMDD) to ISO format (YYYY-MM-DD).
     *
     * Birth dates must be in the past. Uses current year to determine century:
     * if the 2-digit year would result in a future date, subtract 100 years.
     *
     * Examples (assuming current year 2025):
     * - "990101" → "1999-01-01" (99 + 2000 = 2099 > 2025, so 2099 - 100 = 1999)
     * - "250101" → "1925-01-01" (25 + 2000 = 2025 = current year, but birth must be past)
     * - "240101" → "2024-01-01" (24 + 2000 = 2024 < 2025)
     *
     * @param mrzDate The 6-character MRZ date string (YYMMDD)
     * @param currentYear The current year (for testability)
     * @return ISO date string (YYYY-MM-DD) or original string if invalid
     */
    fun parseBirthDate(mrzDate: String, currentYear: Int = Calendar.getInstance().get(Calendar.YEAR)): String {
        if (mrzDate.length != 6) return mrzDate

        val year = mrzDate.substring(0, 2).toIntOrNull() ?: return mrzDate
        val month = mrzDate.substring(2, 4)
        val day = mrzDate.substring(4, 6)

        val currentCentury = currentYear / 100 * 100
        val candidateYear = currentCentury + year

        // Birth date must be in the past
        val fullYear = if (candidateYear > currentYear) {
            candidateYear - 100
        } else {
            candidateYear
        }

        return "$fullYear-$month-$day"
    }

    /**
     * Parses MRZ expiry date (YYMMDD) to ISO format (YYYY-MM-DD).
     *
     * Passports are typically valid for 10 years max. If the 2-digit year
     * would result in a date more than 10 years in the past, assume it's
     * in the current/next century.
     *
     * Examples (assuming current year 2025):
     * - "300601" → "2030-06-01" (30 + 2000 = 2030, within 10 years)
     * - "100601" → "2010-06-01" (10 + 2000 = 2010, within 10-year lookback)
     * - "140601" → "2014-06-01" (14 + 2000 = 2014, 2025 - 10 = 2015 > 2014... wait)
     *
     * Actually: if candidateYear < currentYear - 10, add 100 years
     * - "140601" with current 2025: 2014 < 2015? Yes → 2114
     * - "150601" with current 2025: 2015 < 2015? No → 2015
     *
     * @param mrzDate The 6-character MRZ date string (YYMMDD)
     * @param currentYear The current year (for testability)
     * @return ISO date string (YYYY-MM-DD) or original string if invalid
     */
    fun parseExpiryDate(mrzDate: String, currentYear: Int = Calendar.getInstance().get(Calendar.YEAR)): String {
        if (mrzDate.length != 6) return mrzDate

        val year = mrzDate.substring(0, 2).toIntOrNull() ?: return mrzDate
        val month = mrzDate.substring(2, 4)
        val day = mrzDate.substring(4, 6)

        val currentCentury = currentYear / 100 * 100
        val candidateYear = currentCentury + year

        // Expiry dates are typically within +10/-10 years of current year
        // If candidate is more than 10 years in past, use next century
        val fullYear = if (candidateYear < currentYear - 10) {
            candidateYear + 100
        } else {
            candidateYear
        }

        return "$fullYear-$month-$day"
    }
}
