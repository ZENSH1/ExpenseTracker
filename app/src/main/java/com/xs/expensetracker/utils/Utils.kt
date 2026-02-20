package com.xs.expensetracker.utils

import android.util.Log

object Utils {
    fun Any.log(){
        Log.d("ExpenseTracker", this.toString())
    }
}