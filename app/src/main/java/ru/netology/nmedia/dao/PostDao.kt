package ru.netology.nmedia.dao

import kotlinx.coroutines.flow.Flow
//import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.OnConflictStrategy.REPLACE
import androidx.room.Query
import ru.netology.nmedia.entity.PostEntity
import ru.netology.nmedia.util.ConsolePrinter

@Dao
interface PostDao {
    @Query("SELECT * FROM PostEntity WHERE deleted = 0 AND hidden = 0 ORDER BY unconfirmed DESC, id DESC")
    fun getAll(): Flow<List<PostEntity>>

    @Query("SELECT COUNT(*)  == 0 FROM PostEntity WHERE deleted = 0 AND hidden = 0")
    suspend fun isEmpty(): Boolean

    @Query("SELECT COUNT(*) FROM PostEntity WHERE deleted = 0 AND hidden = 0")
    suspend fun countVisible(): Long

    @Query("SELECT MAX(id) FROM PostEntity WHERE deleted = 0 AND hidden = 0 AND unconfirmed = 0")
    suspend fun maxConfirmedVisible(): Long?

    @Query("SELECT MAX(id) FROM PostEntity WHERE unconfirmed = :unconfirmedStatus")
    suspend fun getMaxId(unconfirmedStatus: Int): Long?  // Берем с учетом unconfirmed

    @Query("SELECT * FROM PostEntity WHERE unconfirmed <> 0 AND deleted = 0 ORDER BY id ASC")
    suspend fun getAllUnconfirmed(): List<PostEntity>

    @Query("SELECT * FROM PostEntity WHERE unconfirmed = 0 AND unsaved <> 0 AND deleted = 0 ORDER BY id ASC")
    suspend fun getAllConfirmedUnsaved(): List<PostEntity>

    @Query("SELECT * FROM PostEntity WHERE deleted <> 0 ORDER BY unconfirmed ASC, id ASC")
    suspend fun getAllDeleted(): List<PostEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(post: PostEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(posts: List<PostEntity>)

    suspend fun insertReturningId(post: PostEntity): Long {
        // Если даже примем пост с подтвержденным id (а я такое не запрограммировала), то вставим все равно неподтвержденный
        val newId = (getMaxId(1) ?: 0) + 1
        insert(post.copy(unconfirmed = 1, id = newId))
        return newId
    }

    /*@Query("UPDATE PostEntity SET content = :content, videoLink = :videoLink WHERE id = :id")
    fun updateContentById(id: Long, content: String, videoLink: String) // Лучше весь пост передавать*/
    @Query(
        """UPDATE PostEntity 
        SET content = :content, unsaved = 1 
        WHERE id = :id AND unconfirmed = :unconfirmedStatus
        """
    )
    suspend fun updateContentById(unconfirmedStatus: Int, id: Long, content: String)
    // Не потребуется отдельно сбрасывать признак unsaved, поскольку
    // после удачной записи на сервер сработает insert, в котором уже будет этот признак сброшен

    suspend fun save(post: PostEntity) =
        if (post.id == 0L) {
            val newId = insertReturningId(post)
        } else insert(post.copy(unsaved = 1)) //updateContentById(post.unconfirmed, post.id, post.content)

    // поле в БД теперь называется likes (вместо countLikes - и по аналогии с сервером)
    @Query(
        """
        UPDATE PostEntity SET
        likes = likes + CASE WHEN likedByMe THEN 0 ELSE 1 END,
        likedByMe = 1 
        WHERE unconfirmed = :unconfirmedStatus AND id = :id 
        """
    )
    suspend fun likeById(unconfirmedStatus: Int, id: Long) // Лайкнуть неподтвержденный пост нельзя

    @Query(
        """
        UPDATE PostEntity SET
        likes = likes + CASE WHEN likedByMe THEN -1 ELSE 0 END,
        likedByMe = 0
        WHERE unconfirmed = :unconfirmedStatus AND id = :id 
        """
    )
    suspend fun dislikeById(
        unconfirmedStatus: Int,
        id: Long
    ) // раз у сервера есть dislike, то и мы себе добавим для подтвержденных с сервера постов

    /*  // Пока нам не дали api на это действие, так что заменим его ничего не делающим действием
          @Query(
            """
            UPDATE PostEntity SET
            countShare = countShare + 1
            WHERE id = :id
            """
        ) */
    @Query(
        """
            UPDATE PostEntity SET
            likes = likes
            WHERE id = :id AND unconfirmed = :unconfirmedStatus
            """
    )
    suspend fun shareById(unconfirmedStatus: Int, id: Long)


    @Query(
        """UPDATE PostEntity SET id = :persistentId, unconfirmed = 0 
        WHERE id = :temporaryId AND unconfirmed = 1"""
    )
    suspend fun confirmWithPersistentId(temporaryId: Long, persistentId: Long)

    @Query("UPDATE PostEntity SET deleted = 1 WHERE id = :id AND unconfirmed = :unconfirmedStatus")
    suspend fun removeById(
        unconfirmedStatus: Int,
        id: Long
    )   //@Query("DELETE FROM PostEntity WHERE id = :id")

//    @Query("UPDATE PostEntity SET deleted = 0 WHERE id = :id AND unconfirmed = :unconfirmedStatus")
//    suspend fun restoreById(unconfirmedStatus: Int, id: Long)

    @Query("DELETE FROM PostEntity WHERE id = :id AND unconfirmed = :unconfirmedStatus")
    suspend fun clearById(unconfirmedStatus: Int, id: Long)

    @Query("UPDATE PostEntity SET hidden = 0 WHERE hidden <> 0")
    suspend fun setAllVisible()
}


