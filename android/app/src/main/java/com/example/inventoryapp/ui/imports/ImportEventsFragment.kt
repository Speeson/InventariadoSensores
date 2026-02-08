package com.example.inventoryapp.ui.imports

import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.ImportSummaryResponseDto
import com.example.inventoryapp.ui.common.ApiErrorFormatter
import okhttp3.MultipartBody

class ImportEventsFragment : ImportFormFragment() {

    override val titleLabel: String = "Importar eventos CSV"
    override val sendLabel: String = "Enviar CSV (Eventos)"

    override suspend fun uploadCsv(
        filePart: MultipartBody.Part,
        dryRun: Boolean,
        fuzzyThreshold: Double
    ): ImportSummaryResponseDto? {
        val res = NetworkModule.api.importEventsCsv(
            file = filePart,
            dryRun = dryRun,
            fuzzyThreshold = fuzzyThreshold
        )
        if (!res.isSuccessful) {
            throw Exception(ApiErrorFormatter.format(res.code()))
        }
        return res.body()
    }
}
