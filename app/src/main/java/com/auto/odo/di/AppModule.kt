package com.auto.odo.di

import android.content.Context
import com.auto.odo.core.UserSessionManager
import com.auto.odo.data.AppDatabase
import com.auto.odo.data.dao.*
import com.auto.odo.data.repository.*
import com.auto.odo.domain.repository.*
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
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    fun provideVehicleDao(db: AppDatabase): VehicleDao = db.vehicleDao()

    @Provides
    fun provideFuelLogDao(db: AppDatabase): FuelLogDao = db.fuelLogDao()

    @Provides
    fun provideServiceLogDao(db: AppDatabase): ServiceLogDao = db.serviceLogDao()

    @Provides
    fun provideExpenseLogDao(db: AppDatabase): ExpenseLogDao = db.expenseLogDao()

    @Provides
    fun provideTripLogDao(db: AppDatabase): TripLogDao = db.tripLogDao()

    @Provides
    @Singleton
    fun provideVehicleRepository(dao: VehicleDao): VehicleRepository {
        return VehicleRepositoryImpl(dao)
    }

    @Provides
    @Singleton
    fun provideFuelLogRepository(dao: FuelLogDao): FuelLogRepository {
        return FuelLogRepositoryImpl(dao)
    }

    @Provides
    @Singleton
    fun provideServiceLogRepository(dao: ServiceLogDao): ServiceLogRepository {
        return ServiceLogRepositoryImpl(dao)
    }

    @Provides
    @Singleton
    fun provideExpenseLogRepository(dao: ExpenseLogDao): ExpenseLogRepository {
        return ExpenseLogRepositoryImpl(dao)
    }

    @Provides
    @Singleton
    fun provideTripLogRepository(dao: TripLogDao): TripLogRepository {
        return TripLogRepositoryImpl(dao)
    }

    @Provides
    @Singleton
    fun provideUserSessionManager(@ApplicationContext context: Context): UserSessionManager {
        return UserSessionManager(context)
    }
}
