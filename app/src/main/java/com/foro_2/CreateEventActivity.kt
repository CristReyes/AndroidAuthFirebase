package com.foro_2

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.foro_2.databinding.ActivityCreateEventBinding

class CreateEventActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateEventBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar ViewBinding
        binding = ActivityCreateEventBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Botón para crear evento
        binding.btnCreateEvent.setOnClickListener {
            val event = Event(
                title = binding.etTitle.text.toString(),
                date = binding.etDate.text.toString(),
                time = binding.etTime.text.toString(),
                location = binding.etLocation.text.toString(),
                description = binding.etDescription.text.toString()
            )

            FirestoreUtil.addEvent(event,
                onSuccess = {
                    Toast.makeText(this, "Evento creado exitosamente", Toast.LENGTH_SHORT).show()
                    finish()
                },
                onFailure = {
                    Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_LONG).show()
                }
            )
        }

        // Botón para volver al Home
        binding.btnBackToHome.setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        }
    }
}
