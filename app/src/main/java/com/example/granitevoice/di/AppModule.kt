package com.example.granitevoice.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.example.granitevoice.data.local.dao.MasterItemDao
import com.example.granitevoice.data.local.dao.OrderDao
import com.example.granitevoice.data.local.db.AppDatabase
import com.example.granitevoice.data.local.preferences.GranitePreferences
import com.example.granitevoice.data.remote.client.GraniteClient
import com.example.granitevoice.data.remote.client.OpenAIClient
import com.example.granitevoice.data.remote.MasterItemsGraniteSource
import com.example.granitevoice.data.remote.OrdersGraniteSource
import com.example.granitevoice.data.remote.StockDetailGraniteSource
import com.example.granitevoice.data.remote.OrderProgressGraniteSource
import com.example.granitevoice.data.repository.MasterItemRepositoryImpl
import com.example.granitevoice.data.repository.OrderRepositoryImpl
import com.example.granitevoice.data.repository.StockRepositoryImpl
import com.example.granitevoice.data.repository.VoiceProcessingRepositoryImpl
import com.example.granitevoice.data.repository.FuzzyMatchingRepositoryImpl
import com.example.granitevoice.domain.model.VoiceProcessingConfig
import com.example.granitevoice.domain.repository.MasterItemRepository
import com.example.granitevoice.domain.repository.OrderRepository
import com.example.granitevoice.domain.repository.StockRepository
import com.example.granitevoice.domain.repository.VoiceProcessingRepository
import com.example.granitevoice.domain.repository.FuzzyMatchingRepository
import com.example.granitevoice.domain.usecase.GetOrdersUseCase
import com.example.granitevoice.domain.usecase.ProcessOrderStatusUseCase
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
        )
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4
            )
            .build()
    }

    @Provides
    @Singleton
    fun provideMasterItemDao(db: AppDatabase): MasterItemDao {
        return db.masterItemDao()
    }

    @Provides
    @Singleton
    fun provideOrderDao(db: AppDatabase): OrderDao {
        return db.orderDao()
    }

    // Repository Interfaces (Domain Layer)
    @Provides
    @Singleton
    fun provideMasterItemRepository(
        dao: MasterItemDao,
        graniteSource: MasterItemsGraniteSource
    ): MasterItemRepository {
        return MasterItemRepositoryImpl(dao, graniteSource)
    }

    @Provides
    @Singleton
    fun provideStockRepository(
        stockDetailSource: StockDetailGraniteSource
    ): StockRepository {
        return StockRepositoryImpl(stockDetailSource)
    }

    @Provides
    @Singleton
    fun provideFuzzyMatchingRepository(): FuzzyMatchingRepository {
        return FuzzyMatchingRepositoryImpl()
    }

    @Provides
    @Singleton
    fun provideVoiceProcessingRepository(
        openAIClient: OpenAIClient,
        fuzzyMatchingRepository: FuzzyMatchingRepository
    ): VoiceProcessingRepository {
        return VoiceProcessingRepositoryImpl(
            openAIClient,
            fuzzyMatchingRepository
        )
    }

    @Provides
    @Singleton
    fun provideOrderRepository(
        orderDao: OrderDao,
        ordersGraniteSource: OrdersGraniteSource,
        fuzzyMatchingRepository: FuzzyMatchingRepository
    ): OrderRepository {
        return OrderRepositoryImpl(orderDao, ordersGraniteSource)
    }

    // Use Cases (Domain Layer)
    @Provides
    @Singleton
    fun provideOrderUseCase(
        orderRepository: OrderRepositoryImpl,
        orderProgressGraniteSource: OrderProgressGraniteSource
    ): GetOrdersUseCase {
        return GetOrdersUseCase(orderRepository, orderProgressGraniteSource)
    }

    @Provides
    @Singleton
    fun provideProcessOrderStatusUseCase(
        getOrdersUseCase: GetOrdersUseCase,
        voiceProcessingRepository: VoiceProcessingRepository,
        config: VoiceProcessingConfig
    ): ProcessOrderStatusUseCase {
        return ProcessOrderStatusUseCase(
            getOrdersUseCase,
            voiceProcessingRepository,
            config
        )
    }

    // TODO: Implement these repository implementations
    // @Provides
    // @Singleton
    // fun provideAudioTranscriptionRepository(
    //     openAIClient: OpenAIClient
    // ): AudioTranscriptionRepository {
    //     return OpenAIAudioTranscriptionRepositoryImpl(openAIClient)
    // }

    // @Provides
    // @Singleton
    // fun provideSyncRepository(
    //     workManager: WorkManager,
    //     masterItemRepository: MasterItemRepository
    // ): SyncRepository {
    //     return WorkManagerSyncRepositoryImpl(workManager, masterItemRepository)
    // }

    // Data Sources (Data Layer)
    @Provides
    @Singleton
    fun provideMasterItemsGraniteSource(
        graniteClient: GraniteClient,
        gson: Gson
    ): MasterItemsGraniteSource {
        return MasterItemsGraniteSource(graniteClient, gson)
    }

    @Provides
    @Singleton
    fun provideOrdersGraniteSource(
        graniteClient: GraniteClient,
        gson: Gson
    ): OrdersGraniteSource {
        return OrdersGraniteSource(graniteClient, gson)
    }

    @Provides
    @Singleton
    fun provideStockDetailGraniteSource(
        graniteClient: GraniteClient,
        gson: Gson
    ): StockDetailGraniteSource {
        return StockDetailGraniteSource(graniteClient, gson)
    }

    @Provides
    @Singleton
    fun provideOrderProgressGraniteSource(
        graniteClient: GraniteClient,
        gson: Gson
    ): OrderProgressGraniteSource {
        return OrderProgressGraniteSource(graniteClient, gson)
    }

    // External Clients (Data Layer)
    @Provides
    @Singleton
    fun provideGraniteClient(granitePreferences: GranitePreferences): GraniteClient {
        return GraniteClient(granitePreferences)
    }

    @Provides
    @Singleton
    fun provideOpenAIClient(@ApplicationContext context: Context): OpenAIClient {
        return OpenAIClient(context)
    }

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }

    // Configuration & Preferences
    @Provides
    @Singleton
    fun provideGranitePreferences(@ApplicationContext context: Context): GranitePreferences {
        return GranitePreferences(context)
    }

    @Provides
    @Singleton
    fun provideVoiceProcessingConfig(): VoiceProcessingConfig {
        return VoiceProcessingConfig(
            maxAlternatives = 5,
            minimumConfidence = 0.6f,
            useAiFuzzyMatching = true,
            enableProcessingLogging = true,
            maxProcessingSteps = 4,
            enableStockLookup = true,
            stockLookupTimeoutMs = 10000L
        )
    }

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return Gson()
    }
}