package com.foro_2

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ViewEventsActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout
    private lateinit var auth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_events)

        container = findViewById(R.id.eventListContainer)
        auth = FirebaseAuth.getInstance()

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
                val btnAttend = view.findViewById<Button>(R.id.btnAttendEvent)
                val tvAttendeeCount = view.findViewById<TextView>(R.id.tvAttendeeCount)

                db.collection("events")
                    .document(event.id)
                    .collection("attendees")
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val count = snapshot.size()
                        tvAttendeeCount.text = "Asistentes: $count"
                    }
                    .addOnFailureListener {
                        tvAttendeeCount.text = "Asistentes: -"
                    }

                // Botón editar
                btnEdit.setOnClickListener {
                    val intent = Intent(this, EditEventActivity::class.java)
                    intent.putExtra("EVENT_ID", event.id)
                    startActivity(intent)
                }

                // Botón eliminar
                btnDelete.setOnClickListener {
                    AlertDialog.Builder(this)
                        .setTitle("¿Eliminar evento?")
                        .setMessage("¿Estás seguro de eliminar \"${event.title}\"?")
                        .setPositiveButton("Sí") { _, _ ->
                            FirestoreUtil.deleteEvent(event.id, {
                                Toast.makeText(this, "Evento eliminado", Toast.LENGTH_SHORT).show()
                                recreate()
                            }, {
                                Toast.makeText(this, "Error al eliminar", Toast.LENGTH_SHORT).show()
                            })
                        }
                        .setNegativeButton("Cancelar", null)
                        .show()
                }

                // Botón asistir
                btnAttend.setOnClickListener {
                    val user = auth.currentUser
                    if (user != null) {
                        val attendeeRef = db.collection("events")
                            .document(event.id)
                            .collection("attendees")
                            .document(user.uid)

                        attendeeRef.get()
                            .addOnSuccessListener { document ->
                                if (document.exists()) {
                                    Toast.makeText(this, "Ya estás registrado como asistente", Toast.LENGTH_SHORT).show()
                                } else {
                                    val attendeeData = mapOf(
                                        "userId" to user.uid,
                                        "email" to user.email,
                                        "timestamp" to System.currentTimeMillis()
                                    )

                                    attendeeRef.set(attendeeData)
                                        .addOnSuccessListener {
                                            Toast.makeText(this, "Te has registrado como asistente ✅", Toast.LENGTH_SHORT).show()

                                            // Actualizar contador
                                            val currentText = tvAttendeeCount.text.toString()
                                            val regex = Regex("""\d+""")
                                            val match = regex.find(currentText)
                                            val currentCount = match?.value?.toIntOrNull() ?: 0
                                            val updatedCount = currentCount + 1
                                            tvAttendeeCount.text = "Asistentes: $updatedCount"
                                        }
                                        .addOnFailureListener {
                                            Toast.makeText(this, "Error al registrar asistencia ❌", Toast.LENGTH_SHORT).show()
                                        }
                                }
                            }
                            .addOnFailureListener {
                                Toast.makeText(this, "Error al verificar asistencia", Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        Toast.makeText(this, "Debes iniciar sesión", Toast.LENGTH_SHORT).show()
                    }
                }


                container.addView(view)
            }
        }
    }
}
