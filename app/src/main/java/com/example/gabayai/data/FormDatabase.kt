package com.example.gabayai.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "forms")
data class FormEntity(
    @PrimaryKey val id: String,
    val name: String,
    val anchorsJson: String,
    val mandatoryAnchorsJson: String,
    val minimumAnchorCount: Int,
)

@Entity(tableName = "fields", foreignKeys = [ForeignKey(
    entity = FormEntity::class, parentColumns = ["id"], childColumns = ["formId"], onDelete = ForeignKey.CASCADE,
)], indices = [Index("formId")])
data class FieldEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val label: String,
    val formId: String?,
    val grounding: String,
    val sourceExcerpt: String?,
    val sourceUrl: String?,
    val sourceAsset: String?,
    val sourceSha256: String?,
    val sourceReviewedOn: String?,
)

@Entity(tableName = "aliases", primaryKeys = ["fieldId", "value"], foreignKeys = [ForeignKey(
    entity = FieldEntity::class, parentColumns = ["id"], childColumns = ["fieldId"], onDelete = ForeignKey.CASCADE,
)], indices = [Index("fieldId")])
data class AliasEntity(val fieldId: String, val value: String)

@Entity(tableName = "explanations", primaryKeys = ["fieldId", "language"], foreignKeys = [ForeignKey(
    entity = FieldEntity::class, parentColumns = ["id"], childColumns = ["fieldId"], onDelete = ForeignKey.CASCADE,
)], indices = [Index("fieldId")])
data class ExplanationEntity(
    val fieldId: String,
    val language: String,
    val title: String,
    val text: String,
    val example: String?,
    val translationReview: String,
)

@Dao
interface FormDao {
    @Query("SELECT * FROM forms ORDER BY id") suspend fun forms(): List<FormEntity>
    @Query("SELECT * FROM fields ORDER BY id") suspend fun fields(): List<FieldEntity>
    @Query("SELECT * FROM aliases ORDER BY fieldId, value") suspend fun aliases(): List<AliasEntity>
    @Query("SELECT * FROM explanations ORDER BY fieldId, language") suspend fun explanations(): List<ExplanationEntity>
    @Query("DELETE FROM fields") suspend fun deleteFields()
    @Query("DELETE FROM forms") suspend fun deleteForms()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertForms(rows: List<FormEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertFields(rows: List<FieldEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAliases(rows: List<AliasEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertExplanations(rows: List<ExplanationEntity>)
}

@Database(entities = [FormEntity::class, FieldEntity::class, AliasEntity::class, ExplanationEntity::class], version = 1, exportSchema = true)
abstract class FormDatabase : RoomDatabase() {
    abstract fun formDao(): FormDao

    companion object {
        @Volatile private var instance: FormDatabase? = null

        fun get(context: Context): FormDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, FormDatabase::class.java, "gabay-definitions.db")
                .build().also { instance = it }
        }
    }
}
