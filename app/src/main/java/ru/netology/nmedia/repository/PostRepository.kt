package ru.netology.nmedia.repository

import kotlinx.coroutines.flow.Flow
import ru.netology.nmedia.dto.Post
import ru.netology.nmedia.model.PhotoModel

interface PostRepository {
    val data: Flow<List<Post>>
    suspend fun getAll()
    suspend fun setAllVisible()
//    suspend fun countHidden(): Int
    suspend fun save(post: Post)
    suspend fun saveWithAttachment(post: Post, model: PhotoModel?)
    suspend fun removeById(unconfirmedStatus:Int, id: Long)
    suspend fun likeById(unconfirmedStatus:Int, id: Long, setLikedOn: Boolean)
    suspend fun shareById(unconfirmedStatus:Int, id: Long) // Пока ничего в нем не будет

    fun getNewerCount(id: Long): Flow<Int>

}

