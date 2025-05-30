package com.foro_2

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
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
    private val attendeesListeners = mutableListOf<ListenerRegistration>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseAuth = FirebaseAuth.getInstance()
        userEventsContainer = binding.userEventsContainer

        setupUI()
        loadChartRealtime()
    }

    private fun setupUI() {
        // Configuración de la interfaz de usuario
        binding.welcomeText.text = "Hola, ${firebaseAuth.currentUser?.email ?: "Usuario"} 👋"

        // Listeners de botones
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
        reloadUserEvents()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Limpieza de listeners
        eventsListener?.remove()
        attendeesListeners.forEach { it.remove() }
        attendeesListeners.clear()
    }

    private fun reloadUserEvents() {
        val user = firebaseAuth.currentUser ?: return
        userEventsContainer.removeAllViews()

        db.collection("events")
            .get()
            .addOnSuccessListener { eventsSnapshot ->
                eventsSnapshot.forEach { eventDoc ->
                    val eventId = eventDoc.id

                    db.collection("events").document(eventId)
                        .collection("attendees").document(user.uid)
                        .get()
                        .addOnSuccessListener { attendeeDoc ->
                            if (attendeeDoc.exists()) {
                                addEventToView(eventDoc, eventId, user.uid)
                            }
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Error al verificar asistencia", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al cargar eventos", Toast.LENGTH_SHORT).show()
            }
    }

    private fun addEventToView(eventDoc: QueryDocumentSnapshot, eventId: String, userId: String) {
        try {
            val eventView = layoutInflater.inflate(R.layout.event_item_simple, userEventsContainer, false)

            // Configuración de las vistas del evento
            eventView.findViewById<TextView>(R.id.tvEventTitle).text =
                eventDoc.getString("title") ?: "Evento"
            eventView.findViewById<TextView>(R.id.tvEventDateTime).text =
                "${eventDoc.getString("date") ?: "-"} | ${eventDoc.getString("time") ?: "-"}"
            eventView.findViewById<TextView>(R.id.tvEventLocation).text =
                eventDoc.getString("location") ?: "-"

            // Listener para cancelar asistencia
            /*.findViewById<Button>(R.id.btnCancelAttendance).setOnClickListener {
                showCancelConfirmationDialog(
                    eventDoc.getString("title") ?: "Evento",
                    eventId,
                    userId
                )
            }*/

            userEventsContainer.addView(eventView)
        } catch (e: Exception) {
            Log.e("HomeActivity", "Error al inflar layout de evento", e)
            Toast.makeText(this, "Error al mostrar evento", Toast.LENGTH_SHORT).show()
        }
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
                reloadUserEvents()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al cancelar asistencia", Toast.LENGTH_SHORT).show()
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
        // Limpiar listeners anteriores
        eventsListener?.remove()
        attendeesListeners.forEach { it.remove() }
        attendeesListeners.clear()

        eventsListener = db.collection("events").addSnapshotListener { eventsSnapshot, e ->
            if (e != null || eventsSnapshot == null) {
                Toast.makeText(this, "Error al cargar eventos", Toast.LENGTH_SHORT).show()
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

            eventsSnapshot.documents.forEach { doc ->
                val listener = doc.reference.collection("attendees")
                    .addSnapshotListener { attendeesSnapshot, _ ->
                        totalAssistances += attendeesSnapshot?.size() ?: 0
                        processed++

                        if (processed == totalEvents) {
                            setChart(totalEvents.toFloat(), totalAssistances.toFloat())
                        }
                    }
                attendeesListeners.add(listener)
            }
        }
    }
}