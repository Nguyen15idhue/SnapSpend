package com.snapspend.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey val id: Long,
    val amount: Long,
    val category: String,
    val imageUrl: String?,
    val note: String?,
    val expenseDate: String,
    val aiConfidence: Double? = null
)

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY expenseDate DESC, id DESC")
    fun observeAll(): Flow<List<ExpenseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(expense: ExpenseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(expenses: List<ExpenseEntity>)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM expenses")
    suspend fun clear()

    /** Thay toàn bộ cache bằng dữ liệu mới nhất từ server (tránh còn bản ghi đã xóa). */
    @androidx.room.Transaction
    suspend fun replaceAll(expenses: List<ExpenseEntity>) {
        clear()
        upsertAll(expenses)
    }
}

@Database(entities = [ExpenseEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : androidx.room.RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    companion object {
        // DB cũ (v1) thiếu cột aiConfidence -> thêm dần, không xóa dữ liệu.
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expenses ADD COLUMN aiConfidence REAL")
            }
        }

        fun create(context: Context) = androidx.room.Room.databaseBuilder(
            context, AppDatabase::class.java, "snapspend.db"
        ).addMigrations(MIGRATION_1_2).build()
    }
}
