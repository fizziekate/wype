package com.wype.security.utils

import android.content.Context
import android.telephony.TelephonyManager
import java.util.*

/**
 * Helper class for managing international country codes
 */
object CountryCodeHelper {
    
    data class CountryInfo(
        val countryName: String,
        val countryCode: String,
        val dialCode: String,
        val flag: String
    )
    
    // Most common countries first
    private val countries = listOf(
        CountryInfo("United States", "US", "+1", "🇺🇸"),
        CountryInfo("United Kingdom", "GB", "+44", "🇬🇧"),
        CountryInfo("Canada", "CA", "+1", "🇨🇦"),
        CountryInfo("Australia", "AU", "+61", "🇦🇺"),
        CountryInfo("Germany", "DE", "+49", "🇩🇪"),
        CountryInfo("France", "FR", "+33", "🇫🇷"),
        CountryInfo("Italy", "IT", "+39", "🇮🇹"),
        CountryInfo("Spain", "ES", "+34", "🇪🇸"),
        CountryInfo("Netherlands", "NL", "+31", "🇳🇱"),
        CountryInfo("Belgium", "BE", "+32", "🇧🇪"),
        CountryInfo("Switzerland", "CH", "+41", "🇨🇭"),
        CountryInfo("Austria", "AT", "+43", "🇦🇹"),
        CountryInfo("Sweden", "SE", "+46", "🇸🇪"),
        CountryInfo("Norway", "NO", "+47", "🇳🇴"),
        CountryInfo("Denmark", "DK", "+45", "🇩🇰"),
        CountryInfo("Finland", "FI", "+358", "🇫🇮"),
        CountryInfo("Poland", "PL", "+48", "🇵🇱"),
        CountryInfo("Czech Republic", "CZ", "+420", "🇨🇿"),
        CountryInfo("Hungary", "HU", "+36", "🇭🇺"),
        CountryInfo("Portugal", "PT", "+351", "🇵🇹"),
        CountryInfo("Greece", "GR", "+30", "🇬🇷"),
        CountryInfo("Ireland", "IE", "+353", "🇮🇪"),
        CountryInfo("Japan", "JP", "+81", "🇯🇵"),
        CountryInfo("South Korea", "KR", "+82", "🇰🇷"),
        CountryInfo("China", "CN", "+86", "🇨🇳"),
        CountryInfo("India", "IN", "+91", "🇮🇳"),
        CountryInfo("Singapore", "SG", "+65", "🇸🇬"),
        CountryInfo("Hong Kong", "HK", "+852", "🇭🇰"),
        CountryInfo("Taiwan", "TW", "+886", "🇹🇼"),
        CountryInfo("Thailand", "TH", "+66", "🇹🇭"),
        CountryInfo("Malaysia", "MY", "+60", "🇲🇾"),
        CountryInfo("Indonesia", "ID", "+62", "🇮🇩"),
        CountryInfo("Philippines", "PH", "+63", "🇵🇭"),
        CountryInfo("Vietnam", "VN", "+84", "🇻🇳"),
        CountryInfo("New Zealand", "NZ", "+64", "🇳🇿"),
        CountryInfo("South Africa", "ZA", "+27", "🇿🇦"),
        CountryInfo("Brazil", "BR", "+55", "🇧🇷"),
        CountryInfo("Mexico", "MX", "+52", "🇲🇽"),
        CountryInfo("Argentina", "AR", "+54", "🇦🇷"),
        CountryInfo("Chile", "CL", "+56", "🇨🇱"),
        CountryInfo("Colombia", "CO", "+57", "🇨🇴"),
        CountryInfo("Peru", "PE", "+51", "🇵🇪"),
        CountryInfo("Venezuela", "VE", "+58", "🇻🇪"),
        CountryInfo("Ecuador", "EC", "+593", "🇪🇨"),
        CountryInfo("Uruguay", "UY", "+598", "🇺🇾"),
        CountryInfo("Paraguay", "PY", "+595", "🇵🇾"),
        CountryInfo("Bolivia", "BO", "+591", "🇧🇴"),
        CountryInfo("Russia", "RU", "+7", "🇷🇺"),
        CountryInfo("Ukraine", "UA", "+380", "🇺🇦"),
        CountryInfo("Turkey", "TR", "+90", "🇹🇷"),
        CountryInfo("Israel", "IL", "+972", "🇮🇱"),
        CountryInfo("United Arab Emirates", "AE", "+971", "🇦🇪"),
        CountryInfo("Saudi Arabia", "SA", "+966", "🇸🇦"),
        CountryInfo("Egypt", "EG", "+20", "🇪🇬"),
        CountryInfo("Morocco", "MA", "+212", "🇲🇦"),
        CountryInfo("Algeria", "DZ", "+213", "🇩🇿"),
        CountryInfo("Tunisia", "TN", "+216", "🇹🇳"),
        CountryInfo("Libya", "LY", "+218", "🇱🇾"),
        CountryInfo("Nigeria", "NG", "+234", "🇳🇬"),
        CountryInfo("Kenya", "KE", "+254", "🇰🇪"),
        CountryInfo("Ghana", "GH", "+233", "🇬🇭"),
        CountryInfo("Ethiopia", "ET", "+251", "🇪🇹"),
        CountryInfo("Tanzania", "TZ", "+255", "🇹🇿"),
        CountryInfo("Uganda", "UG", "+256", "🇺🇬"),
        CountryInfo("Rwanda", "RW", "+250", "🇷🇼"),
        CountryInfo("Zimbabwe", "ZW", "+263", "🇿🇼"),
        CountryInfo("Botswana", "BW", "+267", "🇧🇼"),
        CountryInfo("Namibia", "NA", "+264", "🇳🇦"),
        CountryInfo("Zambia", "ZM", "+260", "🇿🇲")
    )
    
    /**
     * Get country info by dial code
     */
    fun getCountryByDialCode(dialCode: String): CountryInfo? {
        return countries.find { it.dialCode == dialCode }
    }
    
    /**
     * Get country info by country code
     */
    fun getCountryByCode(countryCode: String): CountryInfo? {
        return countries.find { it.countryCode.equals(countryCode, ignoreCase = true) }
    }
    
    /**
     * Get all countries
     */
    fun getAllCountries(): List<CountryInfo> {
        return countries
    }
    
    /**
     * Detect current country based on device settings
     */
    fun getCurrentCountry(context: Context): CountryInfo? {
        try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val countryCode = telephonyManager.networkCountryIso?.uppercase(Locale.getDefault())
            
            if (!countryCode.isNullOrEmpty()) {
                return getCountryByCode(countryCode)
            }
            
            // Fallback to device locale
            val localeCountry = Locale.getDefault().country
            if (localeCountry.isNotEmpty()) {
                return getCountryByCode(localeCountry)
            }
        } catch (e: Exception) {
            // Fallback to US if detection fails
            return getCountryByCode("US")
        }
        
        return getCountryByCode("US") // Default to US
    }
    
    /**
     * Format phone number with country code
     */
    fun formatPhoneWithCountryCode(countryInfo: CountryInfo, phoneNumber: String): String {
        val cleanNumber = phoneNumber.replace(Regex("[^\\d]"), "")
        return "${countryInfo.dialCode}$cleanNumber"
    }
    
    /**
     * Extract country code from phone number
     */
    fun extractCountryCode(phoneNumber: String): CountryInfo? {
        if (!phoneNumber.startsWith("+")) return null
        
        // Try to match the longest possible dial code first
        for (country in countries.sortedByDescending { it.dialCode.length }) {
            if (phoneNumber.startsWith(country.dialCode)) {
                return country
            }
        }
        
        return null
    }
    
    /**
     * Parse phone number into country and local parts
     */
    data class ParsedPhone(
        val countryInfo: CountryInfo,
        val localNumber: String,
        val fullNumber: String
    )
    
    fun parsePhoneNumber(phoneNumber: String): ParsedPhone? {
        val country = extractCountryCode(phoneNumber) ?: return null
        val localNumber = phoneNumber.removePrefix(country.dialCode)
        return ParsedPhone(country, localNumber, phoneNumber)
    }
    
    /**
     * Validate international phone number
     */
    fun isValidInternationalPhone(phoneNumber: String): Boolean {
        if (!phoneNumber.startsWith("+")) return false
        
        val parsed = parsePhoneNumber(phoneNumber) ?: return false
        val localDigits = parsed.localNumber.replace(Regex("[^\\d]"), "")
        
        // Most countries have between 7-15 digits for local numbers
        return localDigits.length in 7..15
    }
    
    /**
     * Get display text for country selector
     */
    fun getDisplayText(countryInfo: CountryInfo): String {
        return "${countryInfo.flag} ${countryInfo.dialCode}"
    }
    
    /**
     * Get full display text for country selector
     */
    fun getFullDisplayText(countryInfo: CountryInfo): String {
        return "${countryInfo.flag} ${countryInfo.countryName} (${countryInfo.dialCode})"
    }
}
