package com.auto.odo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.auto.odo.data.dao.*
import com.auto.odo.data.entity.*

@Database(
    entities = [
        VehicleEntity::class,
        FuelLogEntity::class,
        ServiceLogEntity::class,
        ExpenseLogEntity::class,
        TripLogEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun fuelLogDao(): FuelLogDao
    abstract fun serviceLogDao(): ServiceLogDao
    abstract fun expenseLogDao(): ExpenseLogDao
    abstract fun tripLogDao(): TripLogDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE fuel_logs ADD COLUMN latitude REAL")
                db.execSQL("ALTER TABLE fuel_logs ADD COLUMN longitude REAL")
                db.execSQL("ALTER TABLE trip_logs ADD COLUMN route TEXT")
                db.execSQL("ALTER TABLE trip_logs ADD COLUMN startPlace TEXT")
                db.execSQL("ALTER TABLE trip_logs ADD COLUMN endPlace TEXT")
            }
        }

        @Volatile
        internal var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "odo_database"
                ).addMigrations(MIGRATION_1_2).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
