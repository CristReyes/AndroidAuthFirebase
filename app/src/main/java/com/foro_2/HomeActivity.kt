package com.foro_2

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.foro_2.databinding.ActivityHomeBinding
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.google.firebase.auth.FirebaseAuth

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var firebaseAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseAuth = FirebaseAuth.getInstance()

        // Bienvenida personalizada
        val user = firebaseAuth.currentUser
        binding.welcomeText.text = "Hola, ${user?.email ?: "Usuario"} 👋"

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

        // Gráfico circular
        loadChart()
    }

    private fun loadChart() {
        val entries = listOf(
            PieEntry(60f, "Eventos Creados"),
            PieEntry(40f, "Eventos Asistidos")
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
}
