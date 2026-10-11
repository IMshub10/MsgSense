package com.summer.core.ner

object NerEntityTypes {
    const val ACCOUNT = "ACCOUNT"
    const val AMOUNT = "AMOUNT"
    const val BALANCE = "BALANCE"
    const val BANK = "BANK"
    const val CARD_TYPE = "CARD_TYPE"
    const val DATE = "DATE"
    const val DIRECTION = "DIRECTION"
    const val LIMIT = "LIMIT"
    const val MERCHANT = "MERCHANT"
    const val REF_ID = "REF_ID"
    const val TXN_TYPE = "TXN_TYPE"
    const val UPI_ID = "UPI_ID"

    val allowed = setOf(
        ACCOUNT,
        AMOUNT,
        BALANCE,
        BANK,
        CARD_TYPE,
        DATE,
        DIRECTION,
        LIMIT,
        MERCHANT,
        REF_ID,
        TXN_TYPE,
        UPI_ID,
    )

    fun requireKnown(type: String): String {
        require(type in allowed) { "Unsupported NER entity type: $type" }
        return type
    }
}
