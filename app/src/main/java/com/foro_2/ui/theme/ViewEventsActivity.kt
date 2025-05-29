package com.foro_2

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ViewEventsActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_events)

        container = findViewById(R.id.eventListContainer)

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

                container.addView(view)
            }
        }
    }
}
