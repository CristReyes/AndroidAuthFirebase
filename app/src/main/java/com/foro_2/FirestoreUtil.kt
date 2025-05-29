package com.foro_2

import com.google.firebase.firestore.FirebaseFirestore

object FirestoreUtil {
    private val db = FirebaseFirestore.getInstance()
    private val eventsCollection = db.collection("events")

    fun addEvent(event: Event, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        val docRef = eventsCollection.document()
        event.id = docRef.id
        docRef.set(event).addOnSuccessListener { onSuccess() }.addOnFailureListener { onFailure(it) }
    }

    fun deleteEvent(eventId: String, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        eventsCollection.document(eventId)
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    fun getEventById(eventId: String, onResult: (Event?) -> Unit) {
        eventsCollection.document(eventId)
            .get()
            .addOnSuccessListener { snapshot ->
                val event = snapshot.toObject(Event::class.java)
                onResult(event)
            }
    }

    fun getAllEvents(onResult: (List<Event>) -> Unit) {
        eventsCollection.get().addOnSuccessListener { snapshot ->
            val events = snapshot.documents.mapNotNull { it.toObject(Event::class.java) }
            onResult(events)
        }
    }

    fun updateEvent(event: Event, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        eventsCollection.document(event.id).set(event)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }
}
