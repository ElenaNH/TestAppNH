package ru.netology.nmedia.repository

import androidx.core.net.toFile
import kotlinx.coroutines.flow.*
//import kotlinx.coroutines.flow.Flow
//import kotlinx.coroutines.flow.flow
//import kotlinx.coroutines.flow.flowOn
//import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Response
import ru.netology.nmedia.api.PostsApi
import ru.netology.nmedia.dao.PostDao
import ru.netology.nmedia.dto.Attachment
import ru.netology.nmedia.dto.Media
import ru.netology.nmedia.dto.Post
import ru.netology.nmedia.entity.PostEntity
import ru.netology.nmedia.entity.fromDto
import ru.netology.nmedia.entity.toDto
import ru.netology.nmedia.enumeration.AttachmentType
import ru.netology.nmedia.enumeration.PostSelectionType
import ru.netology.nmedia.util.ConsolePrinter
//import java.io.IOException
//import java.util.concurrent.TimeUnit
//import kotlin.Exception
//import java.lang.RuntimeException
import ru.netology.nmedia.error.ApiError
import ru.netology.nmedia.model.*
import java.io.File
import android.net.Uri
import kotlin.RuntimeException


class PostRepositoryImpl(private val postDao: PostDao) : PostRepository {
    override val data: Flow<List<Post>> = postDao.getAll()
        .map { it.toDto() }  //.map { it.map { entity -> entity.copy(hidden = 0) }.toDto() } - скрытых мы не достанем оттуда
        .flowOn(Dispatchers.Default)


    override suspend fun getAll() {

        // ******************************************************************************
        // К сожалению, в интерфейсе PostRepository не задан никакой аналог pushAllPostponed
        // поэтому нельзя отдельно запараллелить отправку отложенных действий из вьюмодели
        // Приходится сюда добавлять кучу действий - больше деть их некуда
        // ******************************************************************************

        // Запросим список постов с сервера
        val response = PostsApi.retrofitService.getAll()
        if (!response.isSuccessful) {
            throw RuntimeException(response.message())
        }
        val posts = response.body() ?: throw RuntimeException("body is null")
        // Если данные пришли хорошие, то

        // Исключим из обновления несохраненные изменения и запланированные удаления
        val confirmedUnsavedEntities = postDao.getAllConfirmedUnsaved()
        val waitedDeleteEntities = postDao.getAllDeleted()
        val excludeIds = confirmedUnsavedEntities.map { it.id }
            .plus(waitedDeleteEntities.map { it.id })
            .toSet()

        val postEntitiesForInserting = posts.fromDto()
            .filterNot { excludeIds.contains(it.id) }
            .map { entity ->
                entity.copy(hidden = 0)
            } // Если была команда на полное обновление, то ничего не скрываем, все visible

        // Обновим только то, что не нужно прежде проталкивать на сервер (только новое и не измененное)
        if (postEntitiesForInserting.count() > 0)
            postDao.insert(postEntitiesForInserting)

        // Сюда дошли, значит можно вкинуть на сервер кучу подвисших новых/удаляемых постов
        // Пока отдельными частями протолкнем
        // При неуспехе мы вываливаемся отсюда в вызывающую функцию
        pushLocalSelected(PostSelectionType.SELECTION_DELETED)
        pushLocalSelected(PostSelectionType.SELECTION_UNCONFIRMED)
        pushLocalSelected(PostSelectionType.SELECTION_CONFIRMED_UNSAVED)
    }

    suspend fun pushLocalSelected(postSelectionType: PostSelectionType) {
        // Не обрабатываем ошибку, а вылетаем наверх
        ConsolePrinter.printText("push ${postSelectionType.name} started")
        when (postSelectionType) {
            PostSelectionType.SELECTION_UNCONFIRMED -> {
                postDao.getAllUnconfirmed()
                    .toDto()
                    .forEach {
                        save(it)
                    }
            }

            PostSelectionType.SELECTION_CONFIRMED_UNSAVED -> {
                postDao.getAllConfirmedUnsaved()
                    .toDto()
                    .forEach {
                        save(it)
                    }
            }

            PostSelectionType.SELECTION_DELETED -> {
                postDao.getAllDeleted()
                    .forEach { entity ->
                        removeById(
                            entity.unconfirmed,
                            entity.id
                        ) // Удаленный энтити преобразовался бы в пустой пост, а нам нужен непустой
                    }
            }
        }
        ConsolePrinter.printText("push ${postSelectionType.name} finished")
    }

    override suspend fun save(post: Post) {
        // Можно я удалю saveWithAttachment и перенесу все в save?
        // Меня остановило то, что у всех же есть такая функция, значит, и мне нужна?

        saveWithAttachment(post, null)
    }

    override suspend fun saveWithAttachment(post: Post, model1: PhotoModel?) {
        // Сохранение с аттачем означает, что новый/замененный аттач отправляется на сервер
        // Сохранение без аттача означает, что аттач либо не изменился (отсутствует или старый), либо удален

        // Сначала добавляем в локальную БД в "неподтвержденном" статусе
        // Если задана модель, то флаг unsavedAttach = 1, пока аттач не прогружен на сервер
        val newPost = (post.id == 0L)
        val unconfirmedPost = newPost || (post.unconfirmed != 0)
        //val unsavedPost = unconfirmedPost || (post.unsaved != 0)
        if (newPost) ConsolePrinter.printText("New post before inserting...")

        val entity = PostEntity.fromDto(post)  //unsavedAttach уже выставлен как надо
        val postIdLoc = if (newPost) {
            postDao.insertReturningId(entity) // Генерируем id: это расплата за другие удобства
        } else {
            postDao.save(entity)
            post.id
        }
        val model =
            if ((post.attachment == null) || (post.unsavedAttach == 0)) null
            else photoModel(post.attachment.url)
        if (model == null) ConsolePrinter.printText("NO PhotoModel for post.id=${post.id}")

        ConsolePrinter.printText(
            "${if (newPost) "Added new" else "Updated content of"} " +
                    "local post $postIdLoc, " +
                    "attach is ${if (entity.attachment == null) "" else "NOT "}null"
        )

        // Будем выбрасывать исключение во вьюмодель только после некоторой обработки
        var response: Response<Post>? = null
        try {
            if (model == null) {
                // при ошибке фотомодели почистим аттач
                val attach = if (post.unsavedAttach == 1) null else post.attachment
                // Отправляем запрос сохранения на сервер - не будет нового аттача, но может пропасть старый
                response = PostsApi.retrofitService.save(
                    if (unconfirmedPost) post.copy(
                        id = 0,
                        unconfirmed = 0,
                        attachment = attach  // Почищенный либо не измененный аттач
                    ) else post
                )
            } else {
                // Сначала отправляем аттач на сервер
                ConsolePrinter.printText("TRY PhotoModel uploading")
                val media = upload(model)
                ConsolePrinter.printText("PhotoModel uploaded")
                // Затем отправляем запрос сохранения на сервер с измененным аттачем
                val attach = Attachment(
                    url = media.id,
                    description = post.attachment?.description,
                    type = post.attachment?.type ?: AttachmentType.IMAGE
                )
                ConsolePrinter.printText("Created attach by PhotoModel")
                response = PostsApi.retrofitService.save(
                    if (unconfirmedPost) post.copy(
                        id = 0,
                        unconfirmed = 0,
                        attachment = attach
                    ) else post.copy(attachment = attach)
                )
            }

            ConsolePrinter.printText("HAVE GOT SAVE RESPONSE")
        } catch (e: Exception) {
            ConsolePrinter.printText("HAVE NOT GOT SAVE RESPONSE")
            // Просто выбрасываем ошибку, а пост висит в очереди на запись
            throw RuntimeException(e.message.toString())
        }
        if (!(response?.isSuccessful ?: false)) {
            throw RuntimeException(response?.message() ?: "No server response")
        }
        val responsePost = response?.body() ?: throw RuntimeException("body is null")


        ConsolePrinter.printText(
            "${if (newPost) "Added response" else "Updated response"} " +
                    "local post $postIdLoc, unsavedUri=${responsePost?.unsavedAttach ?: "null"}, " +
                    "attach is ${if (responsePost.attachment == null) "" else "NOT "}null"
        )

        // Если вернулся ожидаемый Post,а не null, то

        // Если это записался неподтвержденный пост, то отметим его подтвержденным серверным id при unconfirmed = 0
        if (unconfirmedPost && (responsePost.id != 0L)) {
            // Конфликт ключей даже при асинхронности нам не грозит, ведь ключ двупольный
            // Синхронизируем ключ поста с данными сервера
            postDao.confirmWithPersistentId(postIdLoc, responsePost.id)
            ConsolePrinter.printText("POST CONFIRMED")
        }
        // Обновляем пришедший пост
        // К этому моменту первичный ключ железно синхронизирован с сервером, можем обновлять целиком запись
        postDao.insert(PostEntity.fromDto(responsePost).copy(unconfirmed = 0))


        // ПОРЯДОК:
        // Мы однократно ожидаем ответ сервера.
        // Если ответ есть, то в лок.БД отмечаем подтверждение и правильный id, затем обновляем все поля
        // Если ответ не пришел - тогда пост остается unconfirmed
        // При любом следующем запросе loadPosts:
        // отправляем серверу все unconfirmed в порядке возрастания id
        // затем отправляем все измененные и все удаляемые
        // это можно делать даже асинхронно, но мы пока оставим так

    } // END fun saveWithAttachment

    private suspend fun upload(photoModel: PhotoModel): Media {
        val part = MultipartBody.Part.createFormData(
            "file",
            photoModel.file.name,
            photoModel.file.asRequestBody()
        )

        val response = PostsApi.retrofitService.saveMedia(part)

        if (!response.isSuccessful) {
            throw RuntimeException(response.errorBody()?.string())
        }

        return requireNotNull(response.body())
    }

    override suspend fun removeById(unconfirmedStatus: Int, id: Long) {
        // Сначала удаляем в локальной БД (оптимистичная модель)
        ConsolePrinter.printText("removeById($unconfirmedStatus, $id)")
        postDao.removeById(unconfirmedStatus, id)
        if (unconfirmedStatus != 0) {
            postDao.clearById(unconfirmedStatus, id)
            return  // Если пост не был подтвержден, то на сервер не отправляем
        }
        // Если запрос к серверу вызовет исключение, то аккуратно переправим его выше
        try {
            // отправляем запрос удаления на сервер
            val response = PostsApi.retrofitService.removeById(id)
            ConsolePrinter.printText("HAVE GOT DELETE RESPONSE")
            if (!response.isSuccessful) {
                throw RuntimeException("No server response")
            }
            val responseUnit = response?.body() ?: throw RuntimeException("body is null")
        } catch (e: Exception) {
            ConsolePrinter.printText("HAVE NOT GOT DELETE RESPONSE")
            throw RuntimeException(e.message.toString())
        }
        // Если мы тут, то сервер вернул ожидаемый Unit,а не null, тогда:
        // Мы уже ранее пометили пост к удалению в локальной БД
        // Остается почистить запись в локальной БД, чтобы не скапливать мусор
        postDao.clearById(unconfirmedStatus, id)
        ConsolePrinter.printText("POST CLEARED")
    }

    override suspend fun likeById(unconfirmedStatus: Int, id: Long, setLikedOn: Boolean) {
        // Лайкаем только подтвержденные посты и только если сервер доступен

        // Сначала обрабатываем лайк в локальной БД (оптимистичная модель)
        if (setLikedOn) postDao.likeById(unconfirmedStatus, id)
        else postDao.dislikeById(unconfirmedStatus, id)

        // Неподтвержденные - сразу возвращаем в нелайкнутое состояние
        if (unconfirmedStatus != 0) {
            // Поскольку нам надо добиться перерисовки сердечка, то чуть подождем
            delay(500)
            restoreLikesByIdAndThrow(
                unconfirmedStatus,
                id,
                setLikedOn,
                "Cannot like unconfirmed post"
            )
        }

        // Команду для подтвержденных постов направляем серверу
        // Будем выбрасывать исключение во вьюмодель только после некоторой обработки
        var response: Response<Post>? = null
        try {
            // Затем отправляем запрос лайка/дизлайка на сервер
            response =
                if (setLikedOn) PostsApi.retrofitService.likeById(id)
                else PostsApi.retrofitService.dislikeById(id)
            ConsolePrinter.printText("HAVE GOT LIKE RESPONSE")
        } catch (e: Exception) {
            ConsolePrinter.printText("HAVE NOT GOT LIKE RESPONSE")
            // Придется восстановить лайки в то состояние, что было до изменения в локальной БД
            restoreLikesByIdAndThrow(unconfirmedStatus, id, setLikedOn, e.message.toString())
        }
        if (!(response?.isSuccessful ?: false)) {
            delay(500)  // Пусть сердечко успеет восстановиться
            restoreLikesByIdAndThrow(
                unconfirmedStatus,
                id,
                setLikedOn,
                response?.message() ?: "No server response for like"
            )
        }
        response?.body() ?: restoreLikesByIdAndThrow(
            unconfirmedStatus,
            id,
            setLikedOn,
            "body is null"
        )

        // Если вернулся ожидаемый Post,а не null, то
        // ничего уже не делаем, ведь все сделали до отправки запроса на сервер
    }

    private suspend fun restoreLikesByIdAndThrow(
        unconfirmedStatus: Int,
        id: Long,
        setLikedOn: Boolean,
        exeptionText: String
    ) {
        ConsolePrinter.printText("CALL restoreLikesByIdAndThrow")

        // Тут условие противоположное условию основной ф-ции, т.к. надо вернуть назад
        if (!setLikedOn) postDao.likeById(unconfirmedStatus, id)
        else postDao.dislikeById(unconfirmedStatus, id)
        throw RuntimeException(exeptionText)
    }

    override suspend fun shareById(unconfirmedStatus: Int, id: Long) {
        TODO("Not yet implemented")
    }

    override fun getNewerCount(id: Long): Flow<Int> = flow<Int> {
        // Пока я не типизировала интом (flow<Int>), справа отображалось {this:FlowCollector<Nothing>
        // Почему??? Ведь в п римерах было просто flow!!!
        while (true) {
            delay(120_000L)  // delay(10_000L)
            var response: Response<List<Post>>
            try {
                response = PostsApi.retrofitService.getNewer(id)
                ConsolePrinter.printText("HAVE GOT NEWER RESPONSE")
////
                if (!response.isSuccessful) {
                    throw ApiError(response.code(), response.message())
                }
                val body = response.body() ?: throw ApiError(response.code(), response.message())
                //val doEmit = (data.count() != 0) - ЭТО НЕЛЬЗЯ БЫЛО ДЕЛАТЬ!!!
                val maxVisibleLimit = postDao.maxConfirmedVisible() ?: 0L
                // К пустому списку добавляем посты сразу // emit(body.size) ПРИ ЭТОМ НЕ ДЕЛАЕМ, т.к. все отобразили
                // воспользуемся, что если есть хоть один подтвержденный пост, то maxVisible > 0
                val hiddenPlan = if (maxVisibleLimit > 0L) 1 else 0
                ConsolePrinter.printText("getNewerCount - maxVisibleLimit = $maxVisibleLimit & hiddenPlan = $hiddenPlan")
                var doEmit: Boolean = true
                postDao.insert(body.fromDto().map {
                    it.copy(
                        hidden = if (hiddenPlan == 0) 0
                        else if (it.id <= maxVisibleLimit) 0
                        else 1
                    ) // Чтобы ранее показанные не скрывались, но обновление происходило
                    // Вдруг мы уже отобразили ранее скрытые, пока ждали ответ сервера
                })
                ConsolePrinter.printText("getNewerCount = ${body.size} (inserted)")
                if ((hiddenPlan != 0) && (body.size > 0))
                    emit(body.size) // Если пришедшие посты видимы, то не будет эмиссии
            } catch (e: Exception) {
                ConsolePrinter.printText("HAVE NOT GOT NEWER RESPONSE OR ERROR PROCESSING")
                // Нельзя прерывать Flow, поэтому ошибку игнорируем //throw RuntimeException(e.message.toString())
                ConsolePrinter.printText(e.message.toString())
            }
        }
    }  // .catch { e: Throwable -> throw AppError.from(e) }
        .flowOn(Dispatchers.Default)

    override suspend fun setAllVisible() {
        postDao.setAllVisible()
    }

//    override suspend fun countHidden(): Int {
//        return postDao.countHidden() ?: 0
//    }

}
