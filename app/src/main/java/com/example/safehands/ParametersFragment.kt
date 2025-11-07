package com.example.safehands

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.appcompat.app.AlertDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions

class ParametersFragment : Fragment() {

    private var pressureMax: String? = null
    private var pressureMin: String? = null
    private var pressureBpm: String? = null
    private var glucose: String? = null
    private var oxygen: String? = null
    private var temperature: String? = null

    private lateinit var btnPressure: Button
    private lateinit var btnGlucose: Button
    private lateinit var btnOxygen: Button
    private lateinit var btnTemperature: Button
    private lateinit var btnSendPressure: Button
    private lateinit var btnSendGlucose: Button
    private lateinit var btnSendOxygen: Button
    private lateinit var btnSendTemperature: Button

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var functions: FirebaseFunctions
    private var parentUid: String? = null


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_parameters, container, false)

        functions = FirebaseFunctions.getInstance("europe-west1")
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        val prefs = requireContext().getSharedPreferences("safehands", Context.MODE_PRIVATE)
        parentUid = prefs.getString("parentUid", null)

        btnPressure = view.findViewById(R.id.btnPressure)
        btnGlucose = view.findViewById(R.id.btnGlucose)
        btnOxygen = view.findViewById(R.id.btnOxygen)
        btnTemperature = view.findViewById(R.id.btnTemperature)

        btnSendPressure = view.findViewById(R.id.btnSendPressure)
        btnSendGlucose = view.findViewById(R.id.btnSendGlucose)
        btnSendOxygen = view.findViewById(R.id.btnSendOxygen)
        btnSendTemperature = view.findViewById(R.id.btnSendTemperature)

        updateSendButton()

        btnPressure.setOnClickListener { showPressureDialog() }
        btnGlucose.setOnClickListener { showSingleInputDialog("Glicemia (mg/dL)") { glucose = it; updateSendButton() } }
        btnOxygen.setOnClickListener { showSingleInputDialog("Ossigenazione (%)") { oxygen = it; updateSendButton() } }
        btnTemperature.setOnClickListener { showSingleInputDialog("Temperatura (°C)") { temperature = it; updateSendButton() } }

        btnSendPressure.setOnClickListener {
            if (pressureMax != null || pressureMin != null || pressureBpm != null) {
                saveParameterToFirestore(
                    mapOf(
                        "parameter" to "pressione",
                        "massima" to pressureMax,
                        "minima" to pressureMin,
                        "bpm" to pressureBpm
                    )
                )
                pressureMax = null
                pressureMin = null
                pressureBpm = null
                updateSendButton()
            } else {
                Toast.makeText(requireContext(), "Nessun dato da inviare", Toast.LENGTH_SHORT).show()
            }
        }

        btnSendGlucose.setOnClickListener {
            glucose?.let {
                saveParameterToFirestore(
                    mapOf(
                        "parameter" to "glicemia",
                        "value" to it
                    )
                )
                glucose = null
                updateSendButton()
            } ?: Toast.makeText(requireContext(), "Nessun dato da inviare", Toast.LENGTH_SHORT).show()
        }

        btnSendOxygen.setOnClickListener {
            oxygen?.let {
                saveParameterToFirestore(
                    mapOf(
                        "parameter" to "ossigenazione",
                        "value" to it
                    )
                )
                oxygen = null
                updateSendButton()
            } ?: Toast.makeText(requireContext(), "Nessun dato da inviare", Toast.LENGTH_SHORT).show()
        }

        btnSendTemperature.setOnClickListener {
            temperature?.let {
                saveParameterToFirestore(
                    mapOf(
                        "parameter" to "temperatura",
                        "value" to it
                    )
                )
                temperature = null
                updateSendButton()
            } ?: Toast.makeText(requireContext(), "Nessun dato da inviare", Toast.LENGTH_SHORT).show()
        }

        return view
    }

    private fun saveParameterToFirestore(data: Map<String, Any?>) {
        if (parentUid == null) {
            Toast.makeText(requireContext(), "Errore: UID non trovato", Toast.LENGTH_SHORT).show()
            return
        }

        val payload = hashMapOf(
            "parentUid" to parentUid!!,
            "data" to data
        )

        functions
            .getHttpsCallable("saveParameter")
            .call(payload)
            .addOnSuccessListener { result ->
                Toast.makeText(requireContext(), "${data["parameter"]} inviato!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Errore: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateSendButton() {
        val activeColor = Color.parseColor("#81D4FA")
        val inactiveColor = Color.parseColor("#6C757D")

        btnSendPressure.setBackgroundColor(if (pressureMax != null || pressureMin != null || pressureBpm != null) activeColor else inactiveColor)
        btnSendGlucose.setBackgroundColor(if (!glucose.isNullOrEmpty()) activeColor else inactiveColor)
        btnSendOxygen.setBackgroundColor(if (!oxygen.isNullOrEmpty()) activeColor else inactiveColor)
        btnSendTemperature.setBackgroundColor(if (!temperature.isNullOrEmpty()) activeColor else inactiveColor)
    }

    private fun showSingleInputDialog(parameterName: String, onSave: (String) -> Unit) {
        val editText = EditText(requireContext())
        editText.hint = "Inserisci valore"
        editText.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL

        when (parameterName) {
            "Glicemia (mg/dL)" -> editText.setText(glucose)
            "Ossigenazione (%)" -> editText.setText(oxygen)
            "Temperatura (°C)" -> editText.setText(temperature)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(parameterName)
            .setView(editText)
            .setPositiveButton("Salva") { _, _ ->
                val value = editText.text.toString()
                if (value.isNotEmpty()) {
                    onSave(value)
                    Toast.makeText(requireContext(), "$parameterName salvato: $value", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showPressureDialog() {
        val layout = LinearLayout(requireContext())
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(32,16,32,16)

        val etMax = EditText(requireContext())
        etMax.hint = "Massima"
        etMax.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        etMax.setText(pressureMax ?: "")
        layout.addView(etMax)

        val etMin = EditText(requireContext())
        etMin.hint = "Minima"
        etMin.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        etMin.setText(pressureMin ?: "")
        layout.addView(etMin)

        val etBpm = EditText(requireContext())
        etBpm.hint = "Media battiti/min"
        etBpm.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        etBpm.setText(pressureBpm ?: "")
        layout.addView(etBpm)

        AlertDialog.Builder(requireContext())
            .setTitle("Pressione")
            .setView(layout)
            .setPositiveButton("Salva") { _, _ ->
                pressureMax = etMax.text.toString()
                pressureMin = etMin.text.toString()
                pressureBpm = etBpm.text.toString()
                Toast.makeText(requireContext(), "Pressione salvata: $pressureMax/$pressureMin, BPM $pressureBpm", Toast.LENGTH_SHORT).show()
                updateSendButton()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }
}