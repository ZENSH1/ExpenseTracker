package com.xs.expensetracker.utils

object FirebaseConst {
    //KEYS
    const val SHARED_WITH = "sharedWith"
    const val TRACKERS = "trackers"
    const val SOURCES = "sources"
    const val RECEIPTS = "receipts"
    const val TYPE = "type"
    const val TOTAL_AMOUNT = "totalAmount"
    const val AMOUNT = "amount"
    const val DATE = "date"


    //MESSAGES
    const val TRACK_NAME_CANNOT_BE_EMPTY = "Track name cannot be empty"
    const val TRACK_NAME_ALREADY_EXISTS = "Track name already exists"
    const val TRACK_NOT_FOUND = "Track not found"
    const val TRACK_DELETED = "Track deleted"
    const val SOURCE_NOT_FOUND = "Source not found"
    const val SOURCE_DELETED = "Source deleted"
    const val RECEIPT_NOT_FOUND = "Receipt not found"
    const val RECEIPT_DELETED = "Receipt deleted"
    const val OWNER_ID_MISSING = "OwnerId missing"
}