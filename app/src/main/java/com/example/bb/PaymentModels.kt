package com.example.bb

data class InitialPaymentTransactionDto(
    val id: String = "",
    val status: String = "",
    val amount: Long = 0,
    val currency: String = "IRT",
    val environment: String = "SANDBOX",
    val paymentUrl: String? = null,
    val refId: String? = null,
    val createdAt: String? = null,
    val verifiedAt: String? = null
)

data class InitialPaymentStatusResponse(
    val status: String = "",
    val message: String? = null,
    val initialAccessStatus: String = "NOT_REQUIRED",
    val paymentRequired: Boolean = false,
    val amount: Long = 0,
    val currency: String = "IRT",
    val environment: String = "SANDBOX",
    val transaction: InitialPaymentTransactionDto? = null,
    val code: String? = null
)

data class InitialPaymentRequestResponse(
    val status: String = "",
    val message: String? = null,
    val code: String? = null,
    val initialAccessStatus: String? = null,
    val paymentRequired: Boolean = true,
    val transactionId: String? = null,
    val paymentUrl: String? = null,
    val amount: Long = 0,
    val currency: String = "IRT",
    val environment: String = "SANDBOX"
)
