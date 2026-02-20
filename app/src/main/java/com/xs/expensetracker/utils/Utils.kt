package com.xs.expensetracker.utils

import android.content.Context
import android.util.Log
import android.widget.Toast

object Utils {
    fun Any.log(){
        Log.d("ExpenseTracker", this.toString())
    }

    fun showToast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }

}
