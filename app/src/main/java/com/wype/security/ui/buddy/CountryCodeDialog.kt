package com.wype.security.ui.buddy

import android.app.Dialog
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wype.security.R
import com.wype.security.utils.CountryCodeHelper

/**
 * Dialog for selecting country codes with search functionality
 */
class CountryCodeDialog(
    private val context: Context,
    private val currentCountry: CountryCodeHelper.CountryInfo?,
    private val onCountrySelected: (CountryCodeHelper.CountryInfo) -> Unit
) {
    
    fun show() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_country_selector, null)
        val searchEditText = dialogView.findViewById<EditText>(R.id.et_search_country)
        val listView = dialogView.findViewById<ListView>(R.id.lv_countries)
        
        val allCountries = CountryCodeHelper.getAllCountries()
        var filteredCountries = allCountries.toMutableList()
        
        val adapter = CountryAdapter(context, filteredCountries)
        listView.adapter = adapter
        
        // Setup search functionality
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().toLowerCase()
                filteredCountries.clear()
                
                if (query.isEmpty()) {
                    filteredCountries.addAll(allCountries)
                } else {
                    filteredCountries.addAll(allCountries.filter { country ->
                        country.countryName.toLowerCase().contains(query) ||
                        country.dialCode.contains(query) ||
                        country.countryCode.toLowerCase().contains(query)
                    })
                }
                adapter.notifyDataSetChanged()
            }
        })
        
        // Setup item click listener
        listView.setOnItemClickListener { _, _, position, _ ->
            val selectedCountry = filteredCountries[position]
            onCountrySelected(selectedCountry)
            dialog.dismiss()
        }
        
        // Highlight current country
        currentCountry?.let { current ->
            val position = filteredCountries.indexOfFirst { it.dialCode == current.dialCode }
            if (position >= 0) {
                listView.post {
                    listView.setSelection(position)
                    listView.setItemChecked(position, true)
                }
            }
        }
        
        dialog = MaterialAlertDialogBuilder(context)
            .setTitle("Select Country Code")
            .setView(dialogView)
            .setNegativeButton("Cancel", null)
            .create()
            
        dialog.show()
    }
    
    private lateinit var dialog: Dialog
    
    private class CountryAdapter(
        context: Context,
        private val countries: MutableList<CountryCodeHelper.CountryInfo>
    ) : ArrayAdapter<CountryCodeHelper.CountryInfo>(context, 0, countries) {
        
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context)
                .inflate(R.layout.item_country_selector, parent, false)
                
            val country = getItem(position)!!
            
            val flagAndCode = view.findViewById<TextView>(R.id.tv_flag_and_code)
            val countryName = view.findViewById<TextView>(R.id.tv_country_name)
            
            flagAndCode.text = CountryCodeHelper.getDisplayText(country)
            countryName.text = country.countryName
            
            return view
        }
    }
}
