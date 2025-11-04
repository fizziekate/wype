package com.wype.security.ui.buddy

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wype.security.R
import com.wype.security.utils.CountryCodeHelper

/**
 * Dialog for manual phone number input with country code selection
 */
class PhoneInputDialog(
    private val context: Context,
    private val currentName: String = "",
    private val currentPhone: String = "",
    private val onPhoneEntered: (name: String, phone: String) -> Unit
) {
    
    private var selectedCountry: CountryCodeHelper.CountryInfo? = null
    private lateinit var btnCountrySelector: Button
    private lateinit var tvPhonePreview: TextView
    private lateinit var etPhoneNumber: EditText
    
    fun show() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_phone_input, null)
        
        val etName = dialogView.findViewById<EditText>(R.id.et_buddy_name)
        btnCountrySelector = dialogView.findViewById<Button>(R.id.btn_country_selector)
        etPhoneNumber = dialogView.findViewById<EditText>(R.id.et_phone_number)
        tvPhonePreview = dialogView.findViewById<TextView>(R.id.tv_phone_preview)
        
        // Auto-detect current country
        selectedCountry = CountryCodeHelper.getCurrentCountry(context)
        updateCountryButton(btnCountrySelector)
        
        // Parse existing phone number if provided
        if (currentPhone.isNotEmpty()) {
            val parsed = CountryCodeHelper.parsePhoneNumber(currentPhone)
            if (parsed != null) {
                selectedCountry = parsed.countryInfo
                updateCountryButton(btnCountrySelector)
                etPhoneNumber.setText(parsed.localNumber)
            } else {
                // If not international format, show as is
                etPhoneNumber.setText(currentPhone)
            }
        }
        
        // Set current name if provided
        if (currentName.isNotEmpty()) {
            etName.setText(currentName)
        }
        
        // Country selector button click
        btnCountrySelector.setOnClickListener {
            showCountrySelector()
        }
        
        // Phone number text watcher for live preview
        etPhoneNumber.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: Editable?) {
                updatePhonePreview(tvPhonePreview, s.toString())
            }
        })
        
        // Initial preview update
        updatePhonePreview(tvPhonePreview, etPhoneNumber.text.toString())
        
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle("Enter Buddy Contact")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val name = etName.text.toString().trim()
                val localNumber = etPhoneNumber.text.toString().trim()
                
                if (name.isEmpty()) {
                    Toast.makeText(context, "Please enter a name", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                if (localNumber.isEmpty()) {
                    Toast.makeText(context, "Please enter a phone number", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                val country = selectedCountry
                if (country == null) {
                    Toast.makeText(context, "Please select a country code", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                val fullPhone = CountryCodeHelper.formatPhoneWithCountryCode(country, localNumber)
                
                if (!CountryCodeHelper.isValidInternationalPhone(fullPhone)) {
                    Toast.makeText(context, "Please enter a valid phone number", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                onPhoneEntered(name, fullPhone)
            }
            .setNegativeButton("Cancel", null)
            .create()
            
        dialog.show()
    }
    
    private fun showCountrySelector() {
        CountryCodeDialog(context, selectedCountry) { country ->
            selectedCountry = country
            updateCountryButton(btnCountrySelector)
            updatePhonePreview(tvPhonePreview, etPhoneNumber.text.toString())
        }.show()
    }
    
    private fun updateCountryButton(button: Button) {
        selectedCountry?.let { country ->
            button.text = CountryCodeHelper.getDisplayText(country)
        } ?: run {
            button.text = "Select Country"
        }
    }
    
    private fun updatePhonePreview(textView: TextView, localNumber: String) {
        val country = selectedCountry
        if (country != null && localNumber.isNotEmpty()) {
            val fullNumber = CountryCodeHelper.formatPhoneWithCountryCode(country, localNumber)
            textView.text = "Full number: $fullNumber"
            textView.visibility = android.view.View.VISIBLE
        } else {
            textView.visibility = android.view.View.GONE
        }
    }
}
