package com.revenge.core.db.daos

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.revenge.core.db.entities.RecommendationExclusionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecommendationExclusionDao {
  @Upsert suspend fun upsert(entity: RecommendationExclusionEntity)

  @Upsert suspend fun upsertAll(entities: List<RecommendationExclusionEntity>)

  @Query("SELECT * FROM recommendation_exclusions ORDER BY excludedAtMillis DESC")
  suspend fun getAll(): List<RecommendationExclusionEntity>

  @Query("SELECT * FROM recommendation_exclusions ORDER BY excludedAtMillis DESC")
  fun observeAll(): Flow<List<RecommendationExclusionEntity>>

  @Query("SELECT COUNT(*) FROM recommendation_exclusions") suspend fun count(): Int

  @Query("SELECT EXISTS(SELECT 1 FROM recommendation_exclusions WHERE trackKey = :trackKey)")
  suspend fun isExcluded(trackKey: String): Boolean

  @Query("DELETE FROM recommendation_exclusions WHERE trackKey = :trackKey")
  suspend fun delete(trackKey: String)

  @Query("DELETE FROM recommendation_exclusions") suspend fun clear()
}
