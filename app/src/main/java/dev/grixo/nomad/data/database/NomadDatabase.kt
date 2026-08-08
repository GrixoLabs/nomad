package dev.grixo.nomad.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.grixo.nomad.data.database.dao.SignalDao
import dev.grixo.nomad.data.database.entity.SignalEntity

@Database(entities = [SignalEntity::class], version = 1, exportSchema = false)
abstract class NomadDatabase : RoomDatabase() {
    abstract fun signalDao(): SignalDao

    companion object {
        const val DATABASE_NAME = "nomad_db"
    }
}
