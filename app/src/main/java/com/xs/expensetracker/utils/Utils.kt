package com.xs.expensetracker.utils

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object Utils {
    fun Any.log() {
        Log.d("ExpenseTracker", this.toString())
    }

    fun showToast(context: Context, message: String) {
        (context as? Activity)?.runOnUiThread {
            runCatching {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }



    suspend inline fun <T>runInBackground(crossinline block: suspend () -> T):T {
        return withContext(Dispatchers.IO) {
            block()
        }
    }

}
