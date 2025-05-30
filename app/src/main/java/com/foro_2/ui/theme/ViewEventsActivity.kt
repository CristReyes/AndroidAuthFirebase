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
import com.google.firebase.firestore.ListenerRegistration

class ViewEventsActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout
    private lateinit var auth: FirebaseAuth
    private var eventsListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_events)

        container = findViewById(R.id.eventListContainer)
        auth = FirebaseAuth.getInstance()
    }

    override fun onResume() {
        super.onResume()
        setupEventsListener()
    }

    override fun onPause() {
        super.onPause()
        eventsListener?.remove()
    }

    private fun setupEventsListener() {
        eventsListener = FirestoreUtil.listenToEvents { events ->
            container.removeAllViews()

            if (events.isEmpty()) {
                Toast.makeText(this, "No hay eventos disponibles", Toast.LENGTH_SHORT).show()
                return@listenToEvents
            }

            for (event in events) {
                val view = layoutInflater.inflate(R.layout.event_item_simple, container, false)

                // Configurar vistas con los datos del evento
                view.findViewById<TextView>(R.id.tvEventTitle).text = event.title
                view.findViewById<TextView>(R.id.tvEventDateTime).text = "${event.date} | ${event.time}"
                view.findViewById<TextView>(R.id.tvEventLocation).text = event.location
                view.findViewById<TextView>(R.id.tvEventDescription).text = event.description

                // Configurar botones
                setupEventButtons(view, event)

                container.addView(view)
            }
        }
    }

    private fun setupEventButtons(view: android.view.View, event: Event) {
        val btnEdit = view.findViewById<Button>(R.id.btnEditEvent)
        val btnDelete = view.findViewById<Button>(R.id.btnDeleteEvent)
        val btnAttend = view.findViewById<Button>(R.id.btnAttendEvent)
        val tvAttendeeCount = view.findViewById<TextView>(R.id.tvAttendeeCount)

        // Cargar contador de asistentes
        loadAttendeeCount(event.id, tvAttendeeCount)

        // Botón editar
        btnEdit.setOnClickListener {
            val intent = Intent(this, EditEventActivity::class.java)
            intent.putExtra("EVENT_ID", event.id)
            startActivity(intent)
        }

        // Botón eliminar
        btnDelete.setOnClickListener {
            showDeleteConfirmationDialog(event)
        }

        // Botón asistir
        btnAttend.setOnClickListener {
            handleAttendance(event.id, tvAttendeeCount)
        }
    }

    private fun loadAttendeeCount(eventId: String, textView: TextView) {
        FirestoreUtil.getAttendeesCount(eventId,
            onSuccess = { count ->
                textView.text = "Asistentes: $count"
            },
            onFailure = {
                textView.text = "Asistentes: -"
            }
        )
    }

    private fun showDeleteConfirmationDialog(event: Event) {
        AlertDialog.Builder(this)
            .setTitle("¿Eliminar evento?")
            .setMessage("¿Estás seguro de eliminar \"${event.title}\"?")
            .setPositiveButton("Sí") { _, _ ->
                FirestoreUtil.deleteEvent(event.id,
                    onSuccess = {
                        Toast.makeText(this, "Evento eliminado", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = {
                        Toast.makeText(this, "Error al eliminar", Toast.LENGTH_SHORT).show()
                    }
                )
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun handleAttendance(eventId: String, countTextView: TextView) {
        val user = auth.currentUser ?: run {
            Toast.makeText(this, "Debes iniciar sesión", Toast.LENGTH_SHORT).show()
            return
        }

        FirestoreUtil.toggleAttendance(
            eventId = eventId,
            userId = user.uid,
            userEmail = user.email ?: "",
            onSuccess = { isAttending ->
                val message = if (isAttending) {
                    "Te has registrado como asistente ✅"
                } else {
                    "Has cancelado tu asistencia"
                }
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                loadAttendeeCount(eventId, countTextView)
            },
            onFailure = {
                Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        )
    }
}