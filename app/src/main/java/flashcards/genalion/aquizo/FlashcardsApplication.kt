package flashcards.genalion.aquizo

import android.app.Application
import flashcards.genalion.aquizo.room.FlashcardsDatabase

class FlashcardsApplication: Application() {
     val database: FlashcardsDatabase by lazy { FlashcardsDatabase.getDatabase(this) }
}