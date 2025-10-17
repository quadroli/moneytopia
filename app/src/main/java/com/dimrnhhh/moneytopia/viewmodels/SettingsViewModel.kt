package com.dimrnhhh.moneytopia.viewmodels

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dimrnhhh.moneytopia.database.realm
import com.dimrnhhh.moneytopia.models.Expense
import com.dimrnhhh.moneytopia.utils.PreferenceManager
import io.realm.kotlin.ext.query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.time.LocalDateTime
import com.dimrnhhh.moneytopia.models.Recurrence

data class SettingsState(
    val currencySymbol: String = "$"
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val preferenceManager = PreferenceManager(application)

    private val _uiState = MutableStateFlow(SettingsState())
    val uiState: StateFlow<SettingsState> = _uiState.asStateFlow()

    init {
        loadCurrencySymbol()
    }

    private fun loadCurrencySymbol() {
        viewModelScope.launch {
            val symbol = preferenceManager.getCurrencySymbol()
            _uiState.update { it.copy(currencySymbol = symbol) }
        }
    }

    fun saveCurrencySymbol(symbol: String) {
        viewModelScope.launch {
            preferenceManager.saveCurrencySymbol(symbol)
            _uiState.update { it.copy(currencySymbol = symbol) }
        }
    }

    fun exportExpenses(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val expenses = realm.query<Expense>().find()
                val contentResolver = context.contentResolver
                contentResolver.openFileDescriptor(uri, "w")?.use {
                    FileOutputStream(it.fileDescriptor).use { fos ->
                        fos.write("Date,Category,Amount,Note\n".toByteArray())
                        expenses.forEach { expense ->
                            val line = "${expense.date},${expense.category},${expense.amount},${expense.note}\n"
                            fos.write(line.toByteArray())
                        }
                    }
                }
            } catch (e: Exception) {
                // Handle exception
            }
        }
    }

    fun importExpenses(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val contentResolver = context.contentResolver
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        reader.readLine() // Skip header
                        realm.write {
                            var line = reader.readLine()
                            while (line != null) {
                                val tokens = line.split(",")
                                val date = LocalDateTime.parse(tokens[0])
                                val category = tokens[1]
                                val amount = tokens[2].toDouble()
                                val note = tokens[3]

                                val existing = this.query<Expense>(
                                    "_dateValue == $0 AND category == $1 AND amount == $2 AND note == $3",
                                    date.toString(), category, amount, note
                                ).first().find()

                                if (existing == null) {
                                    val expense = Expense(
                                        date = date,
                                        category = category,
                                        amount = amount,
                                        note = note,
                                        recurrence = Recurrence.None
                                    )
                                    copyToRealm(expense)
                                }
                                line = reader.readLine()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Handle exception
            }
        }
    }
}
