package app.naviplayer

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    private lateinit var holder: PlayerHolder
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Тема следует системной (день/ночь + Material You), иконки системных панелей подбираются автоматически.
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        holder = PlayerHolder(applicationContext)
        val store = SessionStore(this)
        setContent { NaviTheme { Root(store, holder) } }
    }

    override fun onDestroy() {
        holder.release()
        super.onDestroy()
    }
}
