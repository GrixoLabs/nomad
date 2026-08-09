package dev.grixo.nomad.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.grixo.nomad.data.database.entity.SignalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SignalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSignal(signal: SignalEntity)

    @Query("SELECT * FROM offline_signals ORDER BY id ASC")
    fun getAllSignals(): Flow<List<SignalEntity>>

    @Query("SELECT * FROM offline_signals ORDER BY id ASC LIMIT 50")
    suspend fun getSignalsChunk(): List<SignalEntity>

    @Query("SELECT COUNT(*) FROM offline_signals")
    suspend fun countSignals(): Int

    @Delete
    suspend fun deleteSignals(signals: List<SignalEntity>)

    @Query("DELETE FROM offline_signals WHERE id = :id")
    suspend fun deleteSignalById(id: Long)
}
