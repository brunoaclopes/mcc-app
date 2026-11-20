package mcc.phone

import android.app.Application
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import mcc.app.RecipeStore
import mcc.app.ui.T

class PhoneApp : Application() {
    lateinit var prefs: PhonePrefs; private set
    lateinit var store: RecipeStore; private set
    lateinit var shopping: Shopping; private set
    lateinit var notifier: Notifier; private set
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var visible = 0

    override fun onCreate() {
        super.onCreate()
        app = this
        prefs = PhonePrefs(this)
        P.set(prefs.languageCode())
        T.init(this)
        mcc.phone.ui.Mc.dynamicOn.value = prefs.dynamicColor
        store = RecipeStore(this) { prefs.repoUrl }
        shopping = Shopping(prefs)
        notifier = Notifier(this)
        notifier.channels()
        // event notifications: whenever the link is up (in the background via LinkService, or while the app is open)
        scope.launch { LinkClient.events.collect { notifier.event(it) } }
        // widget + Quick Settings tile follow the machine (throttled: state arrives every second while cooking)
        scope.launch { Surfaces.follow(this@PhoneApp) }
        syncLink()
    }

    fun onVisible(v: Boolean) { visible += if (v) 1 else -1; syncLink() }

    /** Background on: the foreground service owns the link. Off: the link lives only while the app is open. */
    fun syncLink() {
        val svc = Intent(this, LinkService::class.java)
        when {
            !prefs.configured -> { stopService(svc); LinkClient.stop() }
            prefs.background -> runCatching { startForegroundService(svc) }.onFailure { if (visible > 0) LinkClient.start(prefs) }
            visible > 0 -> { stopService(svc); LinkClient.start(prefs) }
            else -> { stopService(svc); LinkClient.stop() }
        }
    }

    companion object { lateinit var app: PhoneApp; private set }
}
