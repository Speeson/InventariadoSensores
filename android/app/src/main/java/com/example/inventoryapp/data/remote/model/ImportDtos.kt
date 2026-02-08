package com.example.inventoryapp.data.remote.model

data class ImportErrorDto(
    val row_number: Int,
    val error_code: String,
    val message: String
)

data class ImportReviewDto(
    val row_number: Int,
    val reason: String,
    val suggestions: Map<String, Any>? = null
)

data class ImportSummaryResponseDto(
    val batch_id: Int,
    val dry_run: Boolean,
    val total_rows: Int,
    val ok_rows: Int,
    val error_rows: Int,
    val review_rows: Int,
    val errors: List<ImportErrorDto>,
    val reviews: List<ImportReviewDto>
)

data class ImportReviewItemDto(
    val id: Int,
    val batch_id: Int,
    val row_number: Int,
    val reason: String,
    val payload: Map<String, Any>,
    val suggestions: Map<String, Any>? = null
)

data class ImportReviewListResponseDto(
    val items: List<ImportReviewItemDto>,
    val total: Int,
    val limit: Int,
    val offset: Int
)

data class BasicOkDto(
    val ok: Boolean
)
