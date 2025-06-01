package com.foro_2

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View // Importa View para View.GONE/VISIBLE
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import android.util.Log // Importa Log para depuración

class ViewEventsActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout
    private lateinit var auth: FirebaseAuth
    private var eventsListener: ListenerRegistration? = null
    private var currentUserRole: String? = null // ¡NUEVO! Para almacenar el rol del usuario

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_events)

        container = findViewById(R.id.eventListContainer)
        auth = FirebaseAuth.getInstance()
    }

    override fun onResume() {
        super.onResume()
        // ¡IMPORTANTE! Primero carga el rol, luego configura los listeners de eventos.
        // Esto asegura que `currentUserRole` esté disponible cuando `setupEventButtons` se llame.
        loadUserRoleAndSetupEventsListener()
    }

    override fun onPause() {
        super.onPause()
        eventsListener?.remove()
    }

    // --- NUEVA FUNCIÓN para cargar el rol y luego los eventos ---
    private fun loadUserRoleAndSetupEventsListener() {
        val user = auth.currentUser
        if (user != null) {
            FirestoreUtil.getUserRole(user.uid,
                onSuccess = { role ->
                    currentUserRole = role
                    Log.d("ViewEventsActivity", "Rol de usuario cargado: $currentUserRole")
                    setupEventsListener() // Una vez que el rol está cargado, configura el listener de eventos
                },
                onFailure = { exception ->
                    Log.e("ViewEventsActivity", "Error al cargar el rol: ${exception.message}", exception)
                    Toast.makeText(this, "Error al cargar rol del usuario.", Toast.LENGTH_SHORT).show()
                    currentUserRole = "normal" // Por seguridad, asume rol normal si falla
                    setupEventsListener() // Continúa mostrando eventos aunque no se cargue el rol correctamente
                }
            )
        } else {
            currentUserRole = "guest" // Usuario no autenticado
            Log.d("ViewEventsActivity", "Usuario no autenticado. Rol: $currentUserRole")
            setupEventsListener() // Continúa mostrando eventos para invitados si aplica
        }
    }
    // --- FIN NUEVA FUNCIÓN ---

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

                // Configurar botones, ahora usando el `currentUserRole`
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

        // ¡NUEVO! Controla la visibilidad de los botones de editar y eliminar
        if (currentUserRole == "admin") {
            btnEdit.visibility = View.VISIBLE
            btnDelete.visibility = View.VISIBLE
        } else {
            btnEdit.visibility = View.GONE
            btnDelete.visibility = View.GONE
        }

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
        val btnShare = view.findViewById<Button>(R.id.btnShare)

        btnShare.setOnClickListener {
            val title = event.title
            val date = event.date
            val time = event.time
            val location = event.location
            val description = event.description
            val userEmail = auth.currentUser?.email

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

    private fun shareEvent(title: String, date: String, time: String, location: String, description: String, userEmail: String?) {
        val shareText = StringBuilder()
        shareText.append("¡No te pierdas este evento!\n\n")
        shareText.append("Título: $title\n")
        shareText.append("Fecha: $date\n")
        shareText.append("Hora: $time\n")
        shareText.append("Ubicación: $location\n\n")
        shareText.append("Descripción:\n$description\n\n")
        shareText.append("¡Únete a la comunidad!\n")

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Invita a un evento: $title")
            putExtra(Intent.EXTRA_TEXT, shareText.toString())
        }

        if (!userEmail.isNullOrEmpty()) {
            shareIntent.putExtra(Intent.EXTRA_EMAIL, arrayOf(userEmail))
        }

        val chooser = Intent.createChooser(shareIntent, "Compartir evento a través de...")
        startActivity(chooser)
    }
}