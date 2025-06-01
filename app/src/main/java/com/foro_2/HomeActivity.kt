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
    private lateinit var myEventsTitle: TextView
    private val db = FirebaseFirestore.getInstance()
    private var eventsListener: ListenerRegistration? = null
    private val attendeesListeners = mutableListOf<ListenerRegistration>()
    private val eventViewsMap = mutableMapOf<String, View>()

    // ¡NUEVO! Variable para almacenar el rol del usuario actual
    private var currentUserRole: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseAuth = FirebaseAuth.getInstance()
        myEventsTitle = binding.myEventsTitle
        userEventsContainer = binding.userEventsContainer

        setupUI()
        loadChartRealtime()
    }

    private fun setupUI() {
        binding.welcomeText.text = "Hola, ${firebaseAuth.currentUser?.email ?: "Usuario"} 👋"

        // El setOnClickListener para btnCreateEvent ya está aquí,
        // su visibilidad será controlada por updateUIAfterRoleLoad()
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
        // ¡NUEVO! Llama a esta función para cargar el rol y actualizar la UI
        loadUserRoleAndSetupUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        eventsListener?.remove()
        attendeesListeners.forEach { it.remove() }
        attendeesListeners.clear()
        eventViewsMap.clear()
    }

    // --- NUEVAS FUNCIONES PARA LA GESTIÓN DE ROLES ---
    private fun loadUserRoleAndSetupUI() {
        val user = firebaseAuth.currentUser
        if (user != null) {
            FirestoreUtil.getUserRole(user.uid,
                onSuccess = { role ->
                    currentUserRole = role
                    updateUIAfterRoleLoad() // Llama a la función para actualizar la UI
                },
                onFailure = { exception ->
                    Log.e("HomeActivity", "Error al cargar el rol: ${exception.message}", exception)
                    Toast.makeText(this, "Error al cargar rol del usuario.", Toast.LENGTH_SHORT).show()
                    // Si falla la carga, por seguridad, asumimos rol normal
                    currentUserRole = "normal"
                    updateUIAfterRoleLoad()
                }
            )
        } else {
            // Usuario no autenticado (ej. debería ser redirigido a Login, pero si llega aquí)
            currentUserRole = "guest"
            updateUIAfterRoleLoad()
        }
    }

    private fun updateUIAfterRoleLoad() {
        // Controla la visibilidad del botón "Crear Nuevo Evento"
        if (currentUserRole == "admin") {
            binding.btnCreateEvent.visibility = View.VISIBLE
        } else {
            binding.btnCreateEvent.visibility = View.GONE
        }
        // Puedes añadir aquí cualquier otra lógica de UI que dependa del rol
    }
    // --- FIN DE NUEVAS FUNCIONES ---


    private fun loadUserEventsAndAttendeeCounts() {
        val user = firebaseAuth.currentUser ?: return
        userEventsContainer.removeAllViews()
        attendeesListeners.forEach { it.remove() }
        attendeesListeners.clear()
        eventViewsMap.clear()

        var eventFound = false

        db.collection("events")
            .get()
            .addOnSuccessListener { eventsSnapshot ->
                val totalEvents = eventsSnapshot.size()
                var processedEvents = 0

                if (totalEvents == 0) { // If there are no events at all, hide everything immediately
                    userEventsContainer.visibility = View.GONE
                    //myEventsTitle.visibility = View.GONE
                    return@addOnSuccessListener // Exit the success listener early
                }

                eventsSnapshot.forEach { eventDoc ->
                    val eventId = eventDoc.id

                    db.collection("events").document(eventId)
                        .collection("attendees").document(user.uid)
                        .get()
                        .addOnSuccessListener { attendeeDoc ->
                            if (attendeeDoc.exists()) {
                                addEventToView(eventDoc, eventId, user.uid)
                                eventFound = true
                            }
                            processedEvents++
                            checkFinalVisibility(totalEvents, processedEvents, eventFound)
                        }
                        .addOnFailureListener {
                            processedEvents++
                            checkFinalVisibility(totalEvents, processedEvents, eventFound)
                        }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al cargar eventos: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("HomeActivity", "Error al cargar eventos en HomeActivity", e)
                userEventsContainer.visibility = View.GONE
                //myEventsTitle.visibility = View.GONE // Ensure title is hidden on failure
            }
    }

    // Oculta/Muestra bloque de eventos a los cuales a confirmado asistencia
    fun checkFinalVisibility(total: Int, processed: Int, found: Boolean) {
        if (processed == total) {
            val visibility = if (found) View.VISIBLE else View.GONE
            userEventsContainer.visibility = visibility
            myEventsTitle.visibility = visibility
        }
    }



    private fun addEventToView(eventDoc: QueryDocumentSnapshot, eventId: String, userId: String) {
        try {
            val eventView = layoutInflater.inflate(R.layout.event_item_simple, userEventsContainer, false)
            eventView.tag = eventId

            eventView.findViewById<TextView>(R.id.tvEventTitle).text = eventDoc.getString("title") ?: "Evento"
            eventView.findViewById<TextView>(R.id.tvEventDateTime).text = "${eventDoc.getString("date") ?: "-"} | ${eventDoc.getString("time") ?: "-"}"
            eventView.findViewById<TextView>(R.id.tvEventLocation).text = eventDoc.getString("location") ?: "-"
            eventView.findViewById<TextView>(R.id.tvEventDescription).text = eventDoc.getString("description") ?: ""

            val btnAttend = eventView.findViewById<Button>(R.id.btnAttendEvent)
            val btnEdit = eventView.findViewById<Button>(R.id.btnEditEvent)
            val btnDelete = eventView.findViewById<Button>(R.id.btnDeleteEvent)
            val btnViewComments = eventView.findViewById<Button>(R.id.btnViewComments)
            val ratingBar = eventView.findViewById<RatingBar>(R.id.ratingBar)
            val tvAverageRating = eventView.findViewById<TextView>(R.id.tvAverageRating)
            val tvAttendeeCount = eventView.findViewById<TextView>(R.id.tvAttendeeCount)
            val btnShare = eventView.findViewById<TextView>(R.id.btnShare)

            // Ocultar los controles no necesarios para eventos a los que el usuario ya asiste
            // (Esta parte de tu lógica existente no cambia)
            btnAttend.visibility = View.GONE
            btnEdit.visibility = View.GONE
            btnDelete.visibility = View.GONE
            btnViewComments.visibility = View.GONE
            ratingBar.visibility = View.GONE
            btnShare.visibility = View.GONE
            tvAverageRating.visibility = View.GONE

            setupRealtimeAttendeeCountListener(eventId, tvAttendeeCount)

            userEventsContainer.addView(eventView)
            eventViewsMap[eventId] = eventView
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
        attendeesListeners.add(listener)
    }

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