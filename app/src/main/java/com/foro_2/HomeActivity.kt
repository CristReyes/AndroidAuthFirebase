package com.foro_2

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.foro_2.databinding.ActivityHomeBinding
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QueryDocumentSnapshot

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var userEventsContainer: LinearLayout
    private val db = FirebaseFirestore.getInstance()
    private var eventsListener: ListenerRegistration? = null
    private val attendeesListeners = mutableListOf<ListenerRegistration>() // Para limpiar los listeners de asistentes
    private val eventViewsMap = mutableMapOf<String, View>() // Para mapear eventId a su vista

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseAuth = FirebaseAuth.getInstance()
        userEventsContainer = binding.userEventsContainer

        setupUI()
        loadChartRealtime() // Este ya maneja el gráfico en tiempo real
    }

    private fun setupUI() {
        binding.welcomeText.text = "Hola, ${firebaseAuth.currentUser?.email ?: "Usuario"} 👋"

        binding.btnCreateEvent.setOnClickListener {
            startActivity(Intent(this, CreateEventActivity::class.java))
        }

        binding.btnViewEvents.setOnClickListener {
            startActivity(Intent(this, ViewEventsActivity::class.java))
        }

        binding.signOutButton.setOnClickListener {
            firebaseAuth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        // Aquí cargamos los eventos y establecemos los listeners para el conteo de asistentes
        loadUserEventsAndAttendeeCounts()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Es crucial remover todos los listeners para evitar fugas de memoria
        eventsListener?.remove()
        attendeesListeners.forEach { it.remove() }
        attendeesListeners.clear()
        eventViewsMap.clear() // Limpiar el mapa también
    }

    private fun loadUserEventsAndAttendeeCounts() {
        val user = firebaseAuth.currentUser ?: return
        userEventsContainer.removeAllViews() // Limpiamos la vista antes de añadir nuevos eventos
        attendeesListeners.forEach { it.remove() } // Removemos listeners antiguos de asistentes
        attendeesListeners.clear() // Limpiamos la lista de listeners
        eventViewsMap.clear() // Limpiamos el mapa de vistas

        db.collection("events")
            .get() // Obtener todos los eventos una vez
            .addOnSuccessListener { eventsSnapshot ->
                eventsSnapshot.forEach { eventDoc ->
                    val eventId = eventDoc.id

                    // Solo añadimos eventos a los que el usuario actual asiste
                    db.collection("events").document(eventId)
                        .collection("attendees").document(user.uid)
                        .get() // Verificamos si el usuario actual asiste (una vez)
                        .addOnSuccessListener { attendeeDoc ->
                            if (attendeeDoc.exists()) {
                                // Si el usuario asiste, añadimos la vista del evento
                                addEventToView(eventDoc, eventId, user.uid)
                            }
                        }
                        .addOnFailureListener {
                            Log.e("HomeActivity", "Error al verificar asistencia del usuario para $eventId", it)
                            // No mostrar Toast aquí para cada error potencial, solo si es crítico
                        }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al cargar eventos: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("HomeActivity", "Error al cargar eventos en HomeActivity", e)
            }
    }

    private fun addEventToView(eventDoc: QueryDocumentSnapshot, eventId: String, userId: String) {
        try {
            val eventView = layoutInflater.inflate(R.layout.event_item_simple, userEventsContainer, false)
            eventView.tag = eventId // Asigna un tag para identificar la vista del evento

            // Setear textos
            eventView.findViewById<TextView>(R.id.tvEventTitle).text =
                eventDoc.getString("title") ?: "Evento"
            eventView.findViewById<TextView>(R.id.tvEventDateTime).text =
                "${eventDoc.getString("date") ?: "-"} | ${eventDoc.getString("time") ?: "-"}"
            eventView.findViewById<TextView>(R.id.tvEventLocation).text =
                eventDoc.getString("location") ?: "-"
            eventView.findViewById<TextView>(R.id.tvEventDescription).text =
                eventDoc.getString("description") ?: ""

            // Referencias a botones y rating (mantener la visibilidad original si no hay cambios)
            val btnAttend = eventView.findViewById<Button>(R.id.btnAttendEvent)
            val btnEdit = eventView.findViewById<Button>(R.id.btnEditEvent)
            val btnDelete = eventView.findViewById<Button>(R.id.btnDeleteEvent)
            val btnViewComments = eventView.findViewById<Button>(R.id.btnViewComments)
            val ratingBar = eventView.findViewById<RatingBar>(R.id.ratingBar)
            val tvAverageRating = eventView.findViewById<TextView>(R.id.tvAverageRating)
            val tvAttendeeCount = eventView.findViewById<TextView>(R.id.tvAttendeeCount) // TextView para el conteo de asistentes
            val btnShare = eventView.findViewById<TextView>(R.id.btnShare)

            // Ocultar los controles no necesarios para eventos a los que el usuario ya asiste
            btnAttend.visibility = View.GONE
            btnEdit.visibility = View.GONE
            btnDelete.visibility = View.GONE
            btnViewComments.visibility = View.GONE
            ratingBar.visibility = View.GONE
            btnShare.visibility = View.GONE

            // Establecer el listener para el conteo de asistentes en tiempo real
            setupRealtimeAttendeeCountListener(eventId, tvAttendeeCount)

            userEventsContainer.addView(eventView)
            eventViewsMap[eventId] = eventView // Guardar la vista en el mapa
        } catch (e: Exception) {
            Log.e("HomeActivity", "Error al inflar layout de evento o configurar vista", e)
            Toast.makeText(this, "Error al mostrar evento", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRealtimeAttendeeCountListener(eventId: String, textView: TextView) {
        val listener = db.collection("events").document(eventId)
            .collection("attendees")
            .addSnapshotListener { attendeesSnapshot, e ->
                if (e != null) {
                    Log.w("HomeActivity", "Error al escuchar conteo de asistentes para $eventId", e)
                    textView.text = "Asistentes: -"
                    return@addSnapshotListener
                }

                if (attendeesSnapshot != null) {
                    val count = attendeesSnapshot.size()
                    textView.text = "Asistentes: $count"
                } else {
                    textView.text = "Asistentes: 0"
                }
            }
        attendeesListeners.add(listener) // Añadir a la lista para limpiar en onDestroy
    }

    // Mantener la función showCancelConfirmationDialog si es usada en otro lugar, pero no se llama aquí.
    private fun showCancelConfirmationDialog(title: String, eventId: String, userId: String) {
        AlertDialog.Builder(this)
            .setTitle("Cancelar Asistencia")
            .setMessage("¿Deseas cancelar tu asistencia a \"$title\"?")
            .setPositiveButton("Sí") { _, _ ->
                cancelAttendance(eventId, userId)
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun cancelAttendance(eventId: String, userId: String) {
        db.collection("events")
            .document(eventId)
            .collection("attendees")
            .document(userId)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Asistencia cancelada", Toast.LENGTH_SHORT).show()
                // La vista se actualizará automáticamente si el usuario deja de asistir
                // debido al listener de asistentes o se recargará en onResume.
                // Si el evento desaparece de la vista del usuario, remuévelo explícitamente.
                eventViewsMap[eventId]?.let { viewToRemove ->
                    userEventsContainer.removeView(viewToRemove)
                    eventViewsMap.remove(eventId)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al cancelar asistencia: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("HomeActivity", "Error al cancelar asistencia", e)
            }
    }

    private fun setChart(events: Float, assistances: Float) {
        val entries = listOf(
            PieEntry(events, "Eventos Creados"),
            PieEntry(assistances, "Eventos Asistidos")
        )

        val dataSet = PieDataSet(entries, "").apply {
            colors = listOf(Color.parseColor("#4CAF50"), Color.parseColor("#2196F3"))
            sliceSpace = 3f
            valueTextSize = 16f
            valueTextColor = Color.WHITE
        }

        binding.pieChart.apply {
            data = PieData(dataSet)
            description.isEnabled = false
            isRotationEnabled = false
            centerText = "Participación"
            setCenterTextSize(18f)
            setEntryLabelColor(Color.BLACK)
            animateY(1000)
            invalidate()
        }
    }

    private fun loadChartRealtime() {
        // Este listener ya está configurado para el tiempo real para el gráfico
        db.collection("events").addSnapshotListener { eventsSnapshot, e ->
            if (e != null || eventsSnapshot == null) {
                Toast.makeText(this, "Error al cargar eventos para el gráfico", Toast.LENGTH_SHORT).show()
                setChart(0f, 0f)
                return@addSnapshotListener
            }

            val totalEvents = eventsSnapshot.size()
            if (totalEvents == 0) {
                setChart(0f, 0f)
                return@addSnapshotListener
            }

            var totalAssistances = 0
            var processed = 0

            for (doc in eventsSnapshot.documents) {
                // Aquí usamos .get() para obtener el conteo de asistentes para el gráfico.
                // Si quisieras que el gráfico fuera extremadamente reactivo a cada cambio individual,
                // necesitarías listeners anidados o una lógica más compleja, pero para el gráfico
                // general, un `get()` dentro del listener de eventos suele ser suficiente.
                doc.reference.collection("attendees").get()
                    .addOnSuccessListener { attendeesSnapshot ->
                        totalAssistances += attendeesSnapshot.size()
                        processed++

                        if (processed == totalEvents) {
                            setChart(totalEvents.toFloat(), totalAssistances.toFloat())
                        }
                    }
                    .addOnFailureListener {
                        processed++
                        if (processed == totalEvents) {
                            setChart(totalEvents.toFloat(), totalAssistances.toFloat())
                        }
                    }
            }
        }
    }
}