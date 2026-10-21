package com.pori.assistant

import android.app.Application
import com.pori.assistant.core.PoriController

class PoriApplication : Application() {
    /** The app and the service share one controller. */
    val controller: PoriController by lazy { PoriController(this) }
}
