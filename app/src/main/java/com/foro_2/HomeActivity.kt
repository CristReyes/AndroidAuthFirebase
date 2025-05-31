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

            // Setear textos
            eventView.findViewById<TextView>(R.id.tvEventTitle).text =
                eventDoc.getString("title") ?: "Evento"
            eventView.findViewById<TextView>(R.id.tvEventDateTime).text =
                "${eventDoc.getString("date") ?: "-"} | ${eventDoc.getString("time") ?: "-"}"
            eventView.findViewById<TextView>(R.id.tvEventLocation).text =
                eventDoc.getString("location") ?: "-"
            eventView.findViewById<TextView>(R.id.tvEventDescription).text =
                eventDoc.getString("description") ?: ""

            // Referencias a botones y rating
            val btnAttend = eventView.findViewById<Button>(R.id.btnAttendEvent)
            val btnEdit = eventView.findViewById<Button>(R.id.btnEditEvent)
            val btnDelete = eventView.findViewById<Button>(R.id.btnDeleteEvent)
            val btnViewComments = eventView.findViewById<Button>(R.id.btnViewComments)
            val ratingBar = eventView.findViewById<RatingBar>(R.id.ratingBar)
            val tvAverageRating = eventView.findViewById<TextView>(R.id.tvAverageRating)

            // Verificar si usuario ya asiste
            db.collection("events")
                .document(eventId)
                .collection("attendees")
                .document(userId)
                .get()
                .addOnSuccessListener { attendeeDoc ->
                    if (attendeeDoc.exists()) {
                        // Ocultar controles no necesarios
                        btnAttend.visibility = View.GONE
                        btnEdit.visibility = View.GONE
                        btnDelete.visibility = View.GONE
                        btnViewComments.visibility = View.GONE
                        ratingBar.visibility = View.GONE
                        tvAverageRating.visibility = View.GONE

                        // Opcional: Mostrar botón para cancelar asistencia si quieres
                        val btnCancel = Button(this).apply {
                            text = "❌ Cancelar Asistencia"
                            setBackgroundColor(Color.RED)
                            setTextColor(Color.WHITE)
                            setOnClickListener {
                                AlertDialog.Builder(this@HomeActivity)
                                    .setTitle("Cancelar Asistencia")
                                    .setMessage("¿Deseas cancelar tu asistencia a \"${eventDoc.getString("title")}\"?")
                                    .setPositiveButton("Sí") { _, _ ->
                                        cancelAttendance(eventId, userId)
                                    }
                                    .setNegativeButton("No", null)
                                    .show()
                            }
                        }
                        //(eventView.findViewById<LinearLayout>(R.id.linearLayoutButtons)).addView(btnCancel)
                    }
                }

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
        db.collection("events").addSnapshotListener { eventsSnapshot, e ->
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