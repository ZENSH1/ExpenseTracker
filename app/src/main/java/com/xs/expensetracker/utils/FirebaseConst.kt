package com.xs.expensetracker.utils

object FirebaseConst {
    // Collections
    const val TRACKERS = "trackers"
    const val SOURCES  = "sources"
    const val RECEIPTS = "receipts"

    // Tracker fields
    const val NAME        = "name"
    const val OWNER_ID    = "ownerId"
    const val SHARED_WITH = "sharedWith"
    const val GRAND_TOTAL = "grandTotal"
    const val CREATED_AT  = "createdAt"

    // Source fields
    const val TOTAL_AMOUNT = "totalAmount"
    const val TYPE         = "type"

    // Receipt fields
    const val AMOUNT = "amount"
    const val DATE   = "date"

    // Error messages
    const val TRACK_NAME_CANNOT_BE_EMPTY = "Tracker name cannot be empty"
    const val OWNER_ID_MISSING           = "Owner ID is missing"
    const val TRACK_NOT_FOUND            = "Tracker not found"
    const val SOURCE_NOT_FOUND           = "Source not found"
    const val RECEIPT_NOT_FOUND          = "Receipt not found"
    const val USER_ALREADY_SHARED        = "Tracker is already shared with this user"
}