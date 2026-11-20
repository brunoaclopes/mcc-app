package mcc.phone

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import mcc.phone.ui.MccTheme
import mcc.phone.ui.Root
import mcc.phone.ui.pendingTab

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED && android.os.Build.VERSION.SDK_INT >= 33)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        Recipes.refresh()
        handle(intent)
        setContent {
            MccTheme { Root(calculateWindowSizeClass(this)) }
        }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handle(intent) }

    /** App shortcuts, the widget and the tile open a given tab. */
    private fun handle(i: Intent?) { i?.getStringExtra("tab")?.let { pendingTab.value = it } }

    override fun onStart() { super.onStart(); PhoneApp.app.onVisible(true) }
    override fun onStop() { PhoneApp.app.onVisible(false); super.onStop() }
}
