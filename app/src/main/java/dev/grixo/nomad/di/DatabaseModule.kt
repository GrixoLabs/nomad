package dev.grixo.nomad.di

import android.content.Context
import androidx.room.Room
import dev.grixo.nomad.data.database.NomadDatabase
import dev.grixo.nomad.data.database.dao.SignalDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NomadDatabase {
        return Room.databaseBuilder(
            context,
            NomadDatabase::class.java,
            NomadDatabase.DATABASE_NAME
        ).build()
    }

    @Provides
    fun provideSignalDao(database: NomadDatabase): SignalDao {
        return database.signalDao()
    }
}
