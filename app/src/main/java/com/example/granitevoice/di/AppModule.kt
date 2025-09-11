package com.example.granitevoice.di

import android.content.Context
import androidx.room.Room
import com.example.granitevoice.data.local.dao.MasterItemDao
import com.example.granitevoice.data.local.db.AppDatabase
import com.example.granitevoice.data.remote.client.GraniteClient
import com.example.granitevoice.data.remote.MasterItemsGraniteSource
import com.example.granitevoice.data.repository.MasterItemRepositoryImpl
import com.example.granitevoice.domain.repository.MasterItemRepository
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "granite_database"
        ).build()
    }

    @Provides
    @Singleton
    fun provideMasterItemDao(db: AppDatabase): MasterItemDao {
        return db.masterItemDao()
    }

    @Provides
    @Singleton
    fun provideMasterItemRepository(dao: MasterItemDao, graniteSource: MasterItemsGraniteSource): MasterItemRepository {
        return MasterItemRepositoryImpl(dao, graniteSource)
    }

    @Provides
    @Singleton
    fun provideMasterItemsGraniteSource( // <-- Rename the provides function
        graniteClient: GraniteClient,
        gson: Gson
    ): MasterItemsGraniteSource { // <-- Use the new name as the return type
        return MasterItemsGraniteSource(graniteClient, gson)
    }

    @Provides
    @Singleton
    fun provideGraniteClient(): GraniteClient {
        return GraniteClient()
    }

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return Gson()
    }
}