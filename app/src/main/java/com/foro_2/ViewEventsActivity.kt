package com.foro_2

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RatingBar
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
        val ratingBar = view.findViewById<RatingBar>(R.id.ratingBar)
        val tvAverageRating = view.findViewById<TextView>(R.id.tvAverageRating)
        val user = auth.currentUser

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

        val btnViewComments = view.findViewById<Button>(R.id.btnViewComments)
        btnViewComments.setOnClickListener {
            val intent = Intent(this, EventCommentsActivity::class.java)
            intent.putExtra("EVENT_ID", event.id)
            startActivity(intent)
        }

        // Mostrar el promedio actual
        FirestoreUtil.getAverageRating(event.id) { avg ->
            tvAverageRating.text = "Promedio: %.1f ★".format(avg)
        }

        // Guardar calificación del usuario
        ratingBar.setOnRatingBarChangeListener { _, rating, _ ->
            if (user != null) {
                FirestoreUtil.saveRating(event.id, user.uid, rating.toInt()) {
                    FirestoreUtil.getAverageRating(event.id) { avg ->
                        tvAverageRating.text = "Promedio: %.1f ★".format(avg)
                    }
                }
            }
        }

        // Accionar el boton de compartir evento
        val btnShare = view.findViewById<Button>(R.id.btnShare) // Obtén la referencia al botón

        btnShare.setOnClickListener {
            // Aquí ya tienes el objeto 'event', lo cual es más conveniente
            val title = event.title
            val date = event.date
            val time = event.time
            val location = event.location
            val description = event.description
            val userEmail = auth.currentUser?.email // Obtener el correo del usuario logueado

            shareEvent(title, date, time, location, description, userEmail)
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

    //Acción de compartir el evento

    private fun shareEvent(title: String, date: String, time: String, location: String, description: String, userEmail: String?) {
        // Construir el texto a compartir
        val shareText = StringBuilder()
        shareText.append("¡No te pierdas este evento!\n\n")
        shareText.append("Título: $title\n")
        shareText.append("Fecha: $date\n")
        shareText.append("Hora: $time\n")
        shareText.append("Ubicación: $location\n\n")
        shareText.append("Descripción:\n$description\n\n")
        shareText.append("¡Únete a la comunidad!\n")

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain" // Tipo de contenido que se compartirá
            putExtra(Intent.EXTRA_SUBJECT, "Invita a un evento: $title") // Asunto para correos, etc.
            putExtra(Intent.EXTRA_TEXT, shareText.toString()) // El cuerpo del mensaje
        }

        // Para compartir específicamente por correo, puedes añadir un destinatario por defecto (el usuario actual)
        // Aunque el selector de compartir ya permite elegir Gmail/Outlook, esto pre-rellena el 'Para'.
        if (!userEmail.isNullOrEmpty()) {
            shareIntent.putExtra(Intent.EXTRA_EMAIL, arrayOf(userEmail))
        }

        // Crea un chooser para que el usuario seleccione la aplicación
        val chooser = Intent.createChooser(shareIntent, "Compartir evento a través de...")
        startActivity(chooser)
    }
}