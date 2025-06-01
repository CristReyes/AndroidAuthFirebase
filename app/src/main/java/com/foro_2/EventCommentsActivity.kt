package com.foro_2

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class EventCommentsActivity : AppCompatActivity() {

    private lateinit var commentsContainer: LinearLayout
    private lateinit var etComment: EditText
    private lateinit var btnSubmitComment: Button
    private lateinit var eventId: String

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_comments)

        commentsContainer = findViewById(R.id.commentsContainer)
        etComment = findViewById(R.id.etComment)
        btnSubmitComment = findViewById(R.id.btnSubmitComment)

        eventId = intent.getStringExtra("EVENT_ID") ?: run {
            Toast.makeText(this, "Evento no encontrado", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadComments()

        btnSubmitComment.setOnClickListener {
            val commentText = etComment.text.toString().trim()
            val user = auth.currentUser

            if (commentText.isNotEmpty() && user != null) {
                val comment = mapOf(
                    "userId" to user.uid,
                    "email" to user.email,
                    "text" to commentText,
                    "timestamp" to System.currentTimeMillis()
                )

                db.collection("events").document(eventId)
                    .collection("comments")
                    .add(comment)
                    .addOnSuccessListener {
                        etComment.text.clear()
                        loadComments()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Error al comentar: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    private fun loadComments() {
        commentsContainer.removeAllViews()

        db.collection("events").document(eventId)
            .collection("comments")
            .orderBy("timestamp")
            .get()
            .addOnSuccessListener { result ->
                for (document in result) {
                    val commentView = layoutInflater.inflate(R.layout.comment_item, commentsContainer, false)
                    val email = document.getString("email") ?: "Desconocido"
                    val text = document.getString("text") ?: ""
                    commentView.findViewById<TextView>(R.id.tvCommentUser).text = email
                    commentView.findViewById<TextView>(R.id.tvCommentText).text = text
                    commentsContainer.addView(commentView)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al cargar comentarios: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
