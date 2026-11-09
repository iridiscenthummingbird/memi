package ua.mynotes

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Settings.init(this)
        NotesRepo.init(this)
    }
}
