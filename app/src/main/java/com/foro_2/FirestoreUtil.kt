package com.foro_2

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions

// Your existing FirestoreUtil object
object FirestoreUtil {
    private val db = FirebaseFirestore.getInstance()
    private val eventsCollection = db.collection("events")
    private val usersCollection = db.collection("users") // Add users collection

    // 1. Function to create/update the user document in Firestore
    fun createUserDocument(
        userId: String,
        email: String,
        role: String = "normal", // Default role
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val userMap = hashMapOf(
            "email" to email,
            "role" to role
        )

        usersCollection.document(userId)
            .set(userMap, SetOptions.merge()) // Use merge to avoid overwriting existing data
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e) }
    }

    // 2. Function to get the user's role
    fun getUserRole(userId: String, onSuccess: (String?) -> Unit, onFailure: (Exception) -> Unit) {
        usersCollection.document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val role = document.getString("role")
                    onSuccess(role)
                } else {
                    onSuccess(null) // User document doesn't exist
                }
            }
            .addOnFailureListener { e -> onFailure(e) }
    }


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
        event.id = docRef.id // Set the document ID on the event object
        docRef.set(event.toMap()) // Use the toMap() function
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    fun deleteEvent(eventId: String, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        eventsCollection.document(eventId)
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    // Update event function
    fun updateEvent(event: Event, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        eventsCollection.document(event.id)
            .set(event.toMap()) // Use the toMap() function
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    fun saveRating(eventId: String, userId: String, rating: Int, onComplete: () -> Unit) {
        val ratingRef = db
            .collection("events")
            .document(eventId)
            .collection("ratings")
            .document(userId)

        ratingRef.set(mapOf("value" to rating))
            .addOnSuccessListener { onComplete() }
    }

    fun getAverageRating(eventId: String, callback: (Double) -> Unit) {
        db.collection("events")
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