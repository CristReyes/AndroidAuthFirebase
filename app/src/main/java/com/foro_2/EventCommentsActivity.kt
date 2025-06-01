package com.foro_2

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.foro_2.databinding.ActivityEventCommentsBinding // Asegúrate de que esta importación sea correcta
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class EventCommentsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEventCommentsBinding // Declaración del binding

    private lateinit var commentsContainer: LinearLayout
    private lateinit var etComment: EditText
    private lateinit var btnSubmitComment: Button
    private lateinit var eventId: String

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Inicializar ViewBinding y establecer el contenido una SOLA VEZ
        binding = ActivityEventCommentsBinding.inflate(layoutInflater)
        setContentView(binding.root) // Esto infla R.layout.activity_event_comments y lo hace accesible vía 'binding'

        // ELIMINA LA SIGUIENTE LÍNEA. ESTÁ CAUSANDO EL PROBLEMA:
        // setContentView(R.layout.activity_event_comments)

        // Ahora puedes acceder a tus vistas usando 'binding.'
        // Ya no necesitas findViewById para estas, si tienen IDs en tu XML y están en activity_event_comments.xml
        commentsContainer = binding.commentsContainer // Asumiendo que el ID es commentsContainer en el XML
        etComment = binding.etComment // Asumiendo que el ID es etComment en el XML
        btnSubmitComment = binding.btnSubmitComment // Asumiendo que el ID es btnSubmitComment en el XML


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
                        // No es necesario recargar todos los comentarios cada vez.
                        // Firebase Firestore tiene listeners en tiempo real que podrías usar,
                        // o simplemente añadir el comentario a la vista si la lista no es muy larga.
                        // Por ahora, tu loadComments() actual funciona, pero tenlo en cuenta.
                        loadComments()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Error al comentar: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            } else if (user == null) {
                Toast.makeText(this, "Debes iniciar sesión para comentar.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "El comentario no puede estar vacío.", Toast.LENGTH_SHORT).show()
            }
        }

        // Botón para volver a ViewEventsActivity (el objetivo correcto)
        // Usa el binding correctamente aquí
        binding.btnBackToHome.setOnClickListener {
            // No necesitas crear una nueva Intent a menos que quieras pasar datos.
            // finish() por sí solo regresará a la actividad anterior en la pila,
            // que en este caso debería ser ViewEventsActivity.
            finish()
        }

        // Si realmente quieres especificar la actividad de destino (ej. si la pila no es confiable)
        // puedes usar:
        // binding.btnBackToHome.setOnClickListener {
        //     val intent = Intent(this, ViewEventsActivity::class.java)
        //     intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP) // Limpia la pila para ir a una instancia existente
        //     startActivity(intent)
        //     finish()
        // }
    }

    private fun loadComments() {
        commentsContainer.removeAllViews() // Asegúrate de que commentsContainer se inicializó correctamente con binding.commentsContainer

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