package com.foro_2

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

// Your existing FirestoreUtil object
object FirestoreUtil {
    private val db = FirebaseFirestore.getInstance()
    private val eventsCollection = db.collection("events")

    // Función para escuchar cambios en los eventos
    fun listenToEvents(onEventsChanged: (List<Event>) -> Unit): ListenerRegistration {
        return eventsCollection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w("FirestoreUtil", "Listen failed.", error)
                return@addSnapshotListener
            }

            val events = snapshot?.documents?.mapNotNull { doc ->
                Event(
                    id = doc.id,
                    title = doc.getString("title") ?: "",
                    date = doc.getString("date") ?: "",
                    time = doc.getString("time") ?: "",
                    location = doc.getString("location") ?: "",
                    description = doc.getString("description") ?: ""
                )
            } ?: emptyList()

            onEventsChanged(events)
        }
    }

    // Función para obtener el conteo de asistentes
    fun getAttendeesCount(eventId: String, onSuccess: (Int) -> Unit, onFailure: (Exception) -> Unit) {
        eventsCollection.document(eventId)
            .collection("attendees")
            .get()
            .addOnSuccessListener { snapshot ->
                onSuccess(snapshot.size())
            }
            .addOnFailureListener { e ->
                onFailure(e)
            }
    }

    // Función para alternar asistencia
    fun toggleAttendance(
        eventId: String,
        userId: String,
        userEmail: String,
        onSuccess: (Boolean) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val attendeeRef = eventsCollection.document(eventId)
            .collection("attendees")
            .document(userId)

        attendeeRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                // Si ya está registrado, eliminar asistencia
                attendeeRef.delete()
                    .addOnSuccessListener { onSuccess(false) }
                    .addOnFailureListener { onFailure(it) }
            } else {
                // Si no está registrado, añadir asistencia
                val attendeeData = mapOf(
                    "userId" to userId,
                    "email" to userEmail,
                    "timestamp" to System.currentTimeMillis()
                )
                attendeeRef.set(attendeeData)
                    .addOnSuccessListener { onSuccess(true) }
                    .addOnFailureListener { onFailure(it) }
            }
        }.addOnFailureListener { onFailure(it) }
    }

    // Resto de tus funciones existentes (addEvent, deleteEvent, etc.)
    fun addEvent(event: Event, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        val docRef = eventsCollection.document()
        event.id = docRef.id
        docRef.set(event.toMap()) // This will now work
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    fun deleteEvent(eventId: String, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        eventsCollection.document(eventId)
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    fun updateEvent(event: Event, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        eventsCollection.document(event.id)
            .set(event.toMap()) // This will now work
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    fun saveRating(eventId: String, userId: String, rating: Int, onComplete: () -> Unit) {
        val ratingRef = FirebaseFirestore.getInstance()
            .collection("events")
            .document(eventId)
            .collection("ratings")
            .document(userId)

        ratingRef.set(mapOf("value" to rating))
            .addOnSuccessListener { onComplete() }
    }

    fun getAverageRating(eventId: String, callback: (Double) -> Unit) {
        FirebaseFirestore.getInstance()
            .collection("events")
            .document(eventId)
            .collection("ratings")
            .get()
            .addOnSuccessListener { snapshot ->
                val ratings = snapshot.mapNotNull { it.getLong("value")?.toDouble() }
                val average = if (ratings.isNotEmpty()) ratings.average() else 0.0
                callback(average)
            }
    }
}