package dev.grixo.nomad.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.grixo.nomad.data.repository.DeviceRepositoryImpl
import dev.grixo.nomad.data.repository.JournalRepositoryImpl
import dev.grixo.nomad.data.repository.SignalRepositoryImpl
import dev.grixo.nomad.data.repository.UserRepositoryImpl
import dev.grixo.nomad.domain.repository.DeviceRepository
import dev.grixo.nomad.domain.repository.JournalRepository
import dev.grixo.nomad.domain.repository.SignalRepository
import dev.grixo.nomad.domain.repository.UserRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindDeviceRepository(impl: DeviceRepositoryImpl): DeviceRepository

    @Binds
    @Singleton
    abstract fun bindSignalRepository(impl: SignalRepositoryImpl): SignalRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository

    @Binds
    @Singleton
    abstract fun bindJournalRepository(impl: JournalRepositoryImpl): JournalRepository
}
