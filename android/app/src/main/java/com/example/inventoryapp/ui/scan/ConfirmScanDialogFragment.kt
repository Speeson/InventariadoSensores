package com.example.inventoryapp.ui.scan

import android.content.Context
import android.content.DialogInterface
import android.content.res.Configuration
import android.os.Bundle
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieAnimationView
import com.example.inventoryapp.R
import com.example.inventoryapp.data.local.OfflineQueue
import com.example.inventoryapp.data.local.OfflineSyncer
import com.example.inventoryapp.data.local.PendingType
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.repository.remote.RemoteScanRepository
import com.example.inventoryapp.data.repository.remote.ScanSendResult
import com.example.inventoryapp.databinding.DialogConfirmScanBinding
import com.example.inventoryapp.domain.model.Movement
import com.example.inventoryapp.domain.model.MovementType
import com.example.inventoryapp.ui.common.GradientIconUtil
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class ConfirmScanDialogFragment : DialogFragment() {

    private var _binding: DialogConfirmScanBinding? = null
    private val binding get() = _binding!!

    private val repo = RemoteScanRepository()
    private val gson = Gson()

    private var selectedType: MovementType = MovementType.OUT
    private var productExists: Boolean = true
    private var isOfflineMode: Boolean = false
    private var productId: Int? = null
    private var productName: String? = null
    private var lastQuantity: Int = 0
    private var lastLocationRaw: String = ""

    override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
        _binding = DialogConfirmScanBinding.inflate(layoutInflater)

        val barcode = requireArguments().getString(ARG_BARCODE).orEmpty()
        isOfflineMode = requireArguments().getBoolean(ARG_OFFLINE, false)

        setupUi(barcode)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .setCancelable(true)
            .create()

        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            val width = (resources.displayMetrics.widthPixels * 0.92f).toInt()
            dialog.window?.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
        }

        return dialog
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        parentFragmentManager.setFragmentResult(REQUEST_KEY, bundleOf())
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun setupUi(barcode: String) {        binding.tvBarcode.text = "Codigo de barras: $barcode"
        binding.btnClose.setOnClickListener { dismissAllowingStateLoss() }

        applyTypeSelection(MovementType.OUT)
        binding.btnTypeIn.setOnClickListener { applyTypeSelection(MovementType.IN) }
        binding.btnTypeOut.setOnClickListener { applyTypeSelection(MovementType.OUT) }

        setupLocationDropdown()
        binding.tilLocation.post { applyLocationDropdownIcon() }

        if (isOfflineMode) {
            binding.tvProductName.text = "Sin conexion: validacion pendiente"
            binding.tvProductName.setTextColor(
                ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark)
            )
            productExists = true
        } else {
            lifecycleScope.launch {
                try {
                    val res = NetworkModule.api.listProducts(barcode = barcode, limit = 1, offset = 0)
                    if (res.isSuccessful && res.body() != null && res.body()!!.items.isNotEmpty()) {
                        val p = res.body()!!.items.first()
                        productId = p.id
                        productName = p.name
                        binding.tvProductName.text = "${p.name} (SKU ${p.sku})"
                        productExists = true
                    } else {
                        binding.tvProductName.text = "Producto no encontrado"
                        binding.tvProductName.setTextColor(
                            ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark)
                        )
                        productExists = false
                        showProductNotFoundWarning(barcode)
                    }
                } catch (_: Exception) {
                    isOfflineMode = true
                    binding.tvProductName.text = "Sin conexion: validacion pendiente"
                    binding.tvProductName.setTextColor(
                        ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark)
                    )
                    productExists = true
                }
            }
        }

        binding.btnConfirm.setOnClickListener {
            if (!productExists && !isOfflineMode) {
                showProductNotFoundWarning(barcode)
                return@setOnClickListener
            }

            val quantity = binding.etQuantity.text.toString().toIntOrNull() ?: 0
            if (quantity <= 0) {
                binding.etQuantity.error = "Cantidad > 0"
                return@setOnClickListener
            }

            lastQuantity = quantity
            lastLocationRaw = binding.etLocation.text.toString().trim()
            val location = normalizeLocationInput(lastLocationRaw).ifEmpty { "default" }
            val movement = Movement(
                barcode = barcode,
                type = selectedType,
                quantity = quantity,
                location = location
            )

            binding.btnConfirm.isEnabled = false
            lifecycleScope.launch {
                try {
                    if (isOfflineMode) {
                        enqueueOffline(movement)
                        showSendResultDialog(success = true, msg = "Guardado offline. Se enviara al reconectar") {
                            dismissAllowingStateLoss()
                        }
                    } else {
                        val result = repo.sendFromBarcode(movement)
                        if (result.isSuccess) {
                            val payload = result.getOrNull()
                            val status = awaitFinalEventStatus(payload).uppercase(Locale.ROOT)
                            if (status == "FAILED" || status == "ERROR") {
                                showSendResultDialog(
                                    success = false,
                                    msg = "El evento se envio pero quedo en estado fallido. Puedes revisarlo en Alertas o Actividades.",
                                    eventStatus = payload?.status
                                )
                            } else {
                                showSendResultDialog(
                                    success = true,
                                    msg = buildSendMessage(payload),
                                    eventStatus = payload?.status
                                ) {
                                    dismissAllowingStateLoss()
                                }
                            }
                        } else {
                            val error = result.exceptionOrNull()
                            if (error is java.io.IOException) {
                                enqueueOffline(movement)
                                showSendResultDialog(
                                    success = true,
                                    msg = "Guardado offline. Se enviara al reconectar"
                                ) {
                                    dismissAllowingStateLoss()
                                }
                            } else {
                                showSendResultDialog(success = false, msg = error?.message ?: "Error")
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (e is java.io.IOException) {
                        enqueueOffline(movement)
                        showSendResultDialog(success = true, msg = "Guardado offline. Se enviara al reconectar") {
                            dismissAllowingStateLoss()
                        }
                    } else {
                        showSendResultDialog(success = false, msg = e.message ?: "Error")
                    }
                } finally {
                    if (isAdded) {
                        binding.btnConfirm.isEnabled = true
                    }
                }
            }
        }
    }

    private fun enqueueOffline(movement: Movement) {
        val payload = OfflineSyncer.ScanEventPayload(
            barcode = movement.barcode,
            type = movement.type,
            quantity = movement.quantity,
            location = movement.location?.ifBlank { "default" } ?: "default",
            source = "SCAN"
        )
        OfflineQueue(requireContext()).enqueue(PendingType.SCAN_EVENT, gson.toJson(payload))
    }

    private fun setupLocationDropdown() {
        binding.etLocation.apply {
            showSoftInputOnFocus = false
            isCursorVisible = false
            setDropDownBackgroundDrawable(
                ContextCompat.getDrawable(requireContext(), R.drawable.bg_liquid_dropdown_popup)
            )
            setOnClickListener {
                hideKeyboard()
                showDropDown()
            }
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    hideKeyboard()
                    showDropDown()
                }
            }
        }

        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.listLocations(limit = 200, offset = 0)
                if (res.isSuccessful && res.body() != null) {
                    val items = res.body()!!.items
                    val values = items.sortedBy { it.id }
                        .map { "(${it.id}) ${it.code}" }
                        .distinct()
                    val allValues = listOf("") + if (values.any { it.contains(") default") }) values else listOf("(0) default") + values
                    val adapter = object : ArrayAdapter<String>(
                        requireContext(),
                        android.R.layout.simple_list_item_1,
                        allValues
                    ) {
                        override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                            val view = super.getView(position, convertView, parent)
                            val text = view.findViewById<TextView>(android.R.id.text1)
                            text?.setTextColor(ContextCompat.getColor(requireContext(), R.color.liquid_popup_list_text))
                            view.setBackgroundResource(R.drawable.bg_liquid_dropdown_item)
                            return view
                        }

                        override fun getDropDownView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                            val view = super.getDropDownView(position, convertView, parent)
                            val text = view.findViewById<TextView>(android.R.id.text1)
                            text?.setTextColor(ContextCompat.getColor(requireContext(), R.color.liquid_popup_list_text))
                            view.setBackgroundResource(R.drawable.bg_liquid_dropdown_item)
                            return view
                        }
                    }
                    binding.etLocation.setAdapter(adapter)
                }
            } catch (_: Exception) {
                // Silent fallback to manual input.
            }
        }
    }

    private fun normalizeLocationInput(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("(") && trimmed.contains(") ")) {
            return trimmed.substringAfter(") ").trim()
        }
        return trimmed
    }

    private fun applyTypeSelection(type: MovementType) {
        selectedType = type
        val isIn = type == MovementType.IN
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val normalText = if (isDark) {
            ContextCompat.getColor(requireContext(), R.color.liquid_popup_body)
        } else {
            ContextCompat.getColor(requireContext(), R.color.liquid_popup_button_text)
        }
        val selectedText = if (isDark) {
            ContextCompat.getColor(requireContext(), android.R.color.white)
        } else {
            ContextCompat.getColor(requireContext(), R.color.liquid_popup_button_text)
        }

        binding.btnTypeIn.setBackgroundResource(
            if (isIn) R.drawable.bg_liquid_button_pressed else R.drawable.bg_liquid_button_pressable
        )
        binding.btnTypeOut.setBackgroundResource(
            if (!isIn) R.drawable.bg_liquid_button_pressed else R.drawable.bg_liquid_button_pressable
        )
        binding.btnTypeIn.setTextColor(if (isIn) selectedText else normalText)
        binding.btnTypeOut.setTextColor(if (!isIn) selectedText else normalText)
    }

    private fun buildSendMessage(payload: ScanSendResult?): String {
        if (payload == null) return "Evento enviado"
        val name = productName ?: payload.productName
        val idPart = productId?.let { " (ID $it)" } ?: ""
        val locationDisplay = formatLocationDisplay(lastLocationRaw)
        val quantityDisplay = formatQuantity(lastQuantity)
        return "Producto: $name$idPart\nUbicacion: $locationDisplay\nCantidad: $quantityDisplay"
    }

    private fun formatLocationDisplay(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return "-"
        val match = Regex("^\\((\\d+)\\)\\s*(.+)$").find(trimmed)
        return if (match != null) {
            "${match.groupValues[2]} (ID ${match.groupValues[1]})"
        } else {
            trimmed
        }
    }

    private fun formatQuantity(value: Int): String {
        if (value <= 0) return "-"
        return NumberFormat.getInstance(Locale("es", "ES")).format(value)
    }

    private fun applyLocationDropdownIcon() {
        binding.tilLocation.setEndIconTintList(null)
        val endIconId = com.google.android.material.R.id.text_input_end_icon
        binding.tilLocation.findViewById<android.widget.ImageView>(endIconId)?.let { iv ->
            GradientIconUtil.applyGradient(iv, R.drawable.triangle_down_lg)
            iv.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
        }
    }

    private fun showProductNotFoundWarning(barcode: String) {
        val view = layoutInflater.inflate(R.layout.dialog_liquid_scan_warning, null)
        val dialog = AlertDialog.Builder(requireContext()).setView(view).create()
        view.findViewById<TextView>(R.id.tvScanWarnMessage).text =
            "No se ha encontrado un producto con el codigo de barras $barcode."
        view.findViewById<Button>(R.id.btnScanWarnClose).setOnClickListener { dialog.dismiss() }
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val width = (resources.displayMetrics.widthPixels * 0.9f).toInt()
        dialog.window?.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private fun showSendResultDialog(
        success: Boolean,
        msg: String,
        eventStatus: String? = null,
        onConfirm: (() -> Unit)? = null
    ) {
        val view = layoutInflater.inflate(R.layout.dialog_liquid_scan_result, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(view)
            .setCancelable(false)
            .create()

        val title = view.findViewById<TextView>(R.id.tvScanResultTitle)
        val message = view.findViewById<TextView>(R.id.tvScanResultMessage)
        val lottie = view.findViewById<LottieAnimationView>(R.id.lottieScanResult)
        val btn = view.findViewById<Button>(R.id.btnScanResultConfirm)

        if (success) {
            title.text = "Evento guardado"
            lottie.setAnimation(R.raw.correct_create)
        } else {
            title.text = "Error al enviar"
            lottie.setAnimation(R.raw.wrong)
        }
        lottie.playAnimation()

        val normalizedStatus = eventStatus?.uppercase(Locale.ROOT)
        val statusMsg = when (normalizedStatus) {
            "PROCESSED" -> "Estado: procesado."
            "FAILED" -> "Estado: fallido."
            "ERROR" -> "Estado: fallido."
            null -> ""
            else -> "Estado: pendiente."
        }
        message.text = if (statusMsg.isBlank()) msg else "$msg\n\n$statusMsg"

        btn.text = if (success) "Confirmar" else "Cerrar"
        btn.setOnClickListener {
            dialog.dismiss()
            onConfirm?.invoke()
        }

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val width = (resources.displayMetrics.widthPixels * 0.9f).toInt()
        dialog.window?.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private suspend fun awaitFinalEventStatus(payload: ScanSendResult?): String {
        val initial = payload?.status?.uppercase(Locale.ROOT) ?: "PENDING"
        val eventId = payload?.eventId ?: return initial
        if (initial == "PROCESSED" || initial == "ERROR" || initial == "FAILED") return initial

        repeat(4) {
            delay(700)
            val res = runCatching { NetworkModule.api.listEvents(limit = 30, offset = 0) }.getOrNull()
            val status = res?.body()?.items?.firstOrNull { it.id == eventId }?.eventStatus?.uppercase(Locale.ROOT)
            if (status == "PROCESSED" || status == "ERROR" || status == "FAILED") {
                return status
            }
        }
        return initial
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        imm.hideSoftInputFromWindow(binding.etLocation.windowToken, 0)
    }

    companion object {
        const val TAG = "confirm_scan_dialog"
        const val REQUEST_KEY = "confirm_scan_closed"

        private const val ARG_BARCODE = "barcode"
        private const val ARG_OFFLINE = "offline"

        fun newInstance(barcode: String, offline: Boolean): ConfirmScanDialogFragment {
            return ConfirmScanDialogFragment().apply {
                arguments = bundleOf(
                    ARG_BARCODE to barcode,
                    ARG_OFFLINE to offline
                )
            }
        }
    }
}
