package com.foro_2

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
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

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var userEventsContainer: LinearLayout
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseAuth = FirebaseAuth.getInstance()
        val currentUser = firebaseAuth.currentUser

        // Bienvenida personalizada
        binding.welcomeText.text = "Hola, ${currentUser?.email ?: "Usuario"} 👋"

        // Botones
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

        // Cargar gráfico circular
        loadChartRealtime()

        // Referencia al contenedor de eventos del usuario
        userEventsContainer = findViewById(R.id.userEventsContainer)
    }

    override fun onResume() {
        super.onResume()
        reloadUserEvents()
    }

    private fun reloadUserEvents() {
        val user = firebaseAuth.currentUser ?: return
        userEventsContainer.removeAllViews()

        db.collection("events")
            .get()
            .addOnSuccessListener { eventsSnapshot ->
                for (eventDoc in eventsSnapshot) {
                    val eventId = eventDoc.id

                    db.collection("events").document(eventId)
                        .collection("attendees").document(user.uid)
                        .get()
                        .addOnSuccessListener { attendeeDoc ->
                            if (attendeeDoc.exists()) {
                                val title = eventDoc.getString("title") ?: "Evento"
                                val date = eventDoc.getString("date") ?: "-"
                                val time = eventDoc.getString("time") ?: "-"
                                val location = eventDoc.getString("location") ?: "-"

                                val eventView = layoutInflater.inflate(R.layout.event_item_mini, userEventsContainer, false)
                                eventView.findViewById<TextView>(R.id.tvMiniEventTitle).text = title
                                eventView.findViewById<TextView>(R.id.tvMiniEventDateTime).text = "$date | $time"
                                eventView.findViewById<TextView>(R.id.tvMiniEventLocation).text = location

                                val btnCancel = eventView.findViewById<Button>(R.id.btnCancelAttendance)
                                btnCancel.setOnClickListener {
                                    AlertDialog.Builder(this)
                                        .setTitle("Cancelar Asistencia")
                                        .setMessage("¿Deseas cancelar tu asistencia a \"$title\"?")
                                        .setPositiveButton("Sí") { _, _ ->
                                            db.collection("events")
                                                .document(eventId)
                                                .collection("attendees")
                                                .document(user.uid)
                                                .delete()
                                                .addOnSuccessListener {
                                                    Toast.makeText(this, "Asistencia cancelada", Toast.LENGTH_SHORT).show()
                                                    reloadUserEvents()
                                                }
                                                .addOnFailureListener {
                                                    Toast.makeText(this, "Error al cancelar", Toast.LENGTH_SHORT).show()
                                                }
                                        }
                                        .setNegativeButton("No", null)
                                        .show()
                                }

                                userEventsContainer.addView(eventView)
                            }
                        }
                }
            }
    }

    private fun setChart(events: Float, assistances: Float) {
        val entries = listOf(
            PieEntry(events, "Eventos Creados"),
            PieEntry(assistances, "Eventos Asistidos")
        )

        val dataSet = PieDataSet(entries, "")
        dataSet.colors = listOf(Color.parseColor("#4CAF50"), Color.parseColor("#2196F3"))
        dataSet.sliceSpace = 3f
        dataSet.valueTextSize = 16f
        dataSet.valueTextColor = Color.WHITE

        val pieData = PieData(dataSet)

        binding.pieChart.apply {
            data = pieData
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
            var totalAssistances = 0
            var processed = 0

            if (totalEvents == 0) {
                setChart(0f, 0f)
                return@addSnapshotListener
            }

            for (doc in eventsSnapshot.documents) {
                doc.reference.collection("attendees").addSnapshotListener { attendeesSnapshot, _ ->
                    totalAssistances += attendeesSnapshot?.size() ?: 0
                    processed++

                    // Espera a que se procesen todos
                    if (processed == totalEvents) {
                        setChart(totalEvents.toFloat(), totalAssistances.toFloat())
                    }
                }
            }
        }
    }

}
