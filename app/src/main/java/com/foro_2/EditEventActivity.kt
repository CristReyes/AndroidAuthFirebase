package com.foro_2

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.foro_2.databinding.ActivityCreateEventBinding
import com.google.firebase.firestore.FirebaseFirestore

class EditEventActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateEventBinding
    private lateinit var eventId: String
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar ViewBinding (reutilizando el layout de creación)
        binding = ActivityCreateEventBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Obtener el ID del evento a editar
        eventId = intent.getStringExtra("EVENT_ID") ?: run {
            Toast.makeText(this, "Error: No se encontró el evento", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Cambiar texto del botón principal
        binding.btnCreateEvent.text = "Actualizar Evento"

        // Cargar datos del evento
        loadEventData()

        // Botón para actualizar evento
        binding.btnCreateEvent.setOnClickListener {
            updateEvent()
        }

        // Botón para volver
        binding.btnBackToHome.setOnClickListener {
            finish()
        }
    }

    private fun loadEventData() {
        db.collection("events").document(eventId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    binding.etTitle.setText(document.getString("title"))
                    binding.etDate.setText(document.getString("date"))
                    binding.etTime.setText(document.getString("time"))
                    binding.etLocation.setText(document.getString("location"))
                    binding.etDescription.setText(document.getString("description"))
                } else {
                    Toast.makeText(this, "El evento no existe", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al cargar el evento", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun updateEvent() {
        val updatedEvent = Event(
            id = eventId,
            title = binding.etTitle.text.toString(),
            date = binding.etDate.text.toString(),
            time = binding.etTime.text.toString(),
            location = binding.etLocation.text.toString(),
            description = binding.etDescription.text.toString()
        )

        FirestoreUtil.updateEvent(updatedEvent,
            onSuccess = {
                Toast.makeText(this, "Evento actualizado exitosamente", Toast.LENGTH_SHORT).show()
                finish()
            },
            onFailure = {
                Toast.makeText(this, "Error al actualizar: ${it.message}", Toast.LENGTH_LONG).show()
            }
        )
    }
}