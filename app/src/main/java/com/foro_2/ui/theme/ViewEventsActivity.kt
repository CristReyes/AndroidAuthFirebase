package com.foro_2

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class ViewEventsActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout

    // Launcher para esperar resultado de la edición
    private val editEventLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == RESULT_OK) {
            recreate()  // Recargar eventos automáticamente
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_events)

        container = findViewById(R.id.eventListContainer)

        FirestoreUtil.getAllEvents { events ->
            if (events.isEmpty()) {
                Toast.makeText(this, "No hay eventos disponibles", Toast.LENGTH_SHORT).show()
            }

            for (event in events) {
                val view = layoutInflater.inflate(R.layout.event_item_simple, container, false)

                view.findViewById<TextView>(R.id.tvEventTitle).text = event.title
                view.findViewById<TextView>(R.id.tvEventDateTime).text = "${event.date} | ${event.time}"
                view.findViewById<TextView>(R.id.tvEventLocation).text = event.location
                view.findViewById<TextView>(R.id.tvEventDescription).text = event.description

                val btnEdit = view.findViewById<Button>(R.id.btnEditEvent)
                val btnDelete = view.findViewById<Button>(R.id.btnDeleteEvent)

                btnEdit.setOnClickListener {
                    val intent = Intent(this, EditEventActivity::class.java)
                    intent.putExtra("EVENT_ID", event.id)
                    editEventLauncher.launch(intent)
                }

                btnDelete.setOnClickListener {
                    AlertDialog.Builder(this)
                        .setTitle("¿Eliminar evento?")
                        .setMessage("¿Estás seguro de eliminar \"${event.title}\"?")
                        .setPositiveButton("Sí") { _, _ ->
                            FirestoreUtil.deleteEvent(event.id, {
                                Toast.makeText(this, "Evento eliminado", Toast.LENGTH_SHORT).show()
                                recreate() // ← Recarga la actividad para actualizar la lista
                            }, {
                                Toast.makeText(this, "Error al eliminar", Toast.LENGTH_SHORT).show()
                            })
                        }
                        .setNegativeButton("Cancelar", null)
                        .show()
                }

                container.addView(view)
            }
        }
    }
}
