package com.oashobi.musclehabit.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class Converters {
    @TypeConverter fun dateToString(v: LocalDate?): String? = v?.toString()
    @TypeConverter fun stringToDate(v: String?): LocalDate? = v?.let(LocalDate::parse)
    @TypeConverter fun timeToString(v: LocalTime?): String? = v?.toString()
    @TypeConverter fun stringToTime(v: String?): LocalTime? = v?.let(LocalTime::parse)
    @TypeConverter fun instantToMillis(v: Instant?): Long? = v?.toEpochMilli()
    @TypeConverter fun millisToInstant(v: Long?): Instant? = v?.let(Instant::ofEpochMilli)
}

@Database(
    entities = [
        ScheduleEntity::class,
        VisitEntity::class,
        BodyRecordEntity::class,
        ExerciseEntity::class,
        WorkoutEntryEntity::class,
        WorkoutSetEntity::class,
        WorkoutGroupEntity::class,
        WorkoutGroupItemEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao
    abstract fun visitDao(): VisitDao
    abstract fun bodyRecordDao(): BodyRecordDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun workoutGroupDao(): WorkoutGroupDao
}
