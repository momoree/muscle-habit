package com.oashobi.musclehabit.data.db

import androidx.room.AutoMigration
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
        BodyPhotoEntity::class,
    ],
    // 2: 体型写真（body_photos）を追加（フェーズ3）。表を足すだけなので自動の移行で記録はそのまま残る
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao
    abstract fun visitDao(): VisitDao
    abstract fun bodyRecordDao(): BodyRecordDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun workoutGroupDao(): WorkoutGroupDao
    abstract fun syncDao(): SyncDao
    abstract fun bodyPhotoDao(): BodyPhotoDao
}
