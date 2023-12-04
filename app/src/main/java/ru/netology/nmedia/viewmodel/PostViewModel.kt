package ru.netology.nmedia.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ru.netology.nmedia.db.AppDb
import ru.netology.nmedia.dto.Attachment
import ru.netology.nmedia.dto.Post
import ru.netology.nmedia.enumeration.AttachmentType
import ru.netology.nmedia.enumeration.PostActionType
import ru.netology.nmedia.model.FeedModel
import ru.netology.nmedia.model.FeedModelState
import ru.netology.nmedia.model.PhotoModel
import ru.netology.nmedia.repository.*
import ru.netology.nmedia.util.ConsolePrinter
import ru.netology.nmedia.util.SingleLiveEvent
import java.io.File

//import java.io.IOException
//import kotlin.concurrent.thread


private val emptyPost = Post.getEmptyPost()

class PostViewModel(application: Application) : AndroidViewModel(application) {
    // упрощённый вариант
    private val repository: PostRepository =
        PostRepositoryImpl(AppDb.getInstance(application).postDao())

    val data: LiveData<FeedModel> =
        repository.data
            .map { FeedModel(posts = it, empty = it.isEmpty()) }
            .asLiveData(Dispatchers.Default)

    val edited = MutableLiveData(emptyPost)
    val draft = MutableLiveData(emptyPost)  // И будем сохранять это только "in memory"

    val newerCount: LiveData<Int> = data.switchMap {
        repository.getNewerCount(it.posts
            .filter { it.unconfirmed == 0 }
            .firstOrNull()?.id ?: 0L
        )
            .asLiveData(Dispatchers.Default)
    }

    private val _dataState = MutableLiveData<FeedModelState>() //MutableLiveData(FeedModelState())
    val dataState: LiveData<FeedModelState>
        get() = _dataState

    private val _photo = MutableLiveData<PhotoModel?>()
    val photo: LiveData<PhotoModel?>
        get() = _photo

    private val _postCreated = SingleLiveEvent<Unit>()
    val postCreated: LiveData<Unit>
        get() = _postCreated
    private val _postSavingStarted = SingleLiveEvent<Unit>()
    val postSavingStarted: LiveData<Unit>
        get() = _postSavingStarted

    private val _postActionFailed = SingleLiveEvent<PostActionType>()  // Однократная ошибка
    val postActionFailed: LiveData<PostActionType>
        get() = _postActionFailed


// 1) раньше выводили однократную ошибку, но теперь нас устраивает многократный вывод ошибки
// с кнопкой refresh для всех постов - либо однократный для создания/редактирования поста
// Сообщение об успехе не выводим - просто обновляемся
// 2) посты при ошибке не перезапрашиваем - только по кнопке или по свайпу или при открытии
// (избегаем бесконечного цикла при отказе сервера)
// 3) репозиторий
// при ошибке лайка самовосстанавливается
// при иной ошибке устанавливает различные состояния ожидания, которые синхронизируются при обновлении
// 4) viewModel подписана на данные репозитория, поэтому сама перерисуется при его восстановлении


// С какой периодичностью перезапрашивать посты?
// А то все обновится на сервере, а мы не увидим
// Вариант - при каждом успешном действии (т.е., когда точно был контакт с сервером)
// Решение: Пока сделаем только при загрузке и принудительном обновлении


    init {
        loadPosts()
    }

    fun loadPosts() = refreshOrLoadPosts(refreshingState = false)

    fun refresh() = refreshOrLoadPosts(refreshingState = true)

    private fun refreshOrLoadPosts(refreshingState: Boolean) = viewModelScope.launch {
        if (refreshingState) {
            _dataState.value = FeedModelState(refreshing = true)  // Начинаем обновление
        } else {
            _dataState.value = FeedModelState(loading = true)  // Начинаем загрузку
        }
        try {
            repository.getAll()
            _dataState.value = FeedModelState()     // При успехе
        } catch (e: Exception) {
            _dataState.value = FeedModelState(error = true)  // При ошибке
        }
    }

    fun setPhoto(uri: Uri, file: File) {
        // Новый аттач добавляем в пост
        val attach = Attachment(url = uri.toString(), description = null, AttachmentType.IMAGE)
        edited.value = edited.value?.copy(attachment = attach, unsavedAttach = 1)
        // В конце установим фотомодель, чтобы обработчик учел присвоенный выше аттач
        _photo.value = PhotoModel(uri, file)
    }

    fun clearPhoto() {
        // Удаляем аттач (пусть не отображается то, что удалили)
        edited.value = edited.value?.copy(attachment = null, unsavedAttach = 0)
        // В конце очистим фотомодель, чтобы обработчик учел удаленный выше аттач
        _photo.value = null
    }

    fun save() {
        // Тут просто вызываем метод репозитория
        // А уже в репозитории делаем так:
        // Если пост новый - создаем запись в локальной БД
        // Отправляем для неподтвержденного поста запрос на сервер с заменой id = 0
        // а если пост подтвержденный, то с его собственным id
        // Если от сервера приходит ошибка, то статусы поста не меняются
        // Если приходит успешный ответ с постом и с присвоенным id, то
        // "черновому" посту меняем id и сбрасываем признак unconfirmed
        // После этого уже пост точно подтвержден, так что обновляем его целиком

        viewModelScope.launch {
            //supervisorScope {
            async {
                // Эта корутина будет отвечать за запись - ее не ждем

                try {
                    edited.value?.let { post ->
                        repository.save(post) // с аттачем или без - записано внутри поста
                        _postCreated.value = Unit  // Однократное событие

                        ConsolePrinter.printText("MY SAVING TRY FINISHED")
                    }
                } catch (e: Exception) {
                    ConsolePrinter.printText("MY SAVING CATCH STARTED: ${e.message.toString()}")
                    // Тут надо просто оставить запись в локальной БД в неподтвержденном статусе
                    _postActionFailed.value = PostActionType.ACTION_POST_SAVING
                }
            }

            launch {
                // Эта корутина будет отвечать за выход из режима редактирования

                _postSavingStarted.value = Unit // Однократное событие

                ConsolePrinter.printText("Before quitEditing() call...")
                quitEditing() // сбрасываем редактирование при попытке записи (заменим на emptyPost)
                ConsolePrinter.printText("After quitEditing() call...")
                // Черновик сбросим, т.к. у нас будет либо подтвержденный, либо неподтвержденный пост
                setDraft(emptyPost)

            }

        }

    }


    fun changeContent(content: String) {
        val text = content.trim()
        if (edited.value?.content == text) {
            return
        }
        edited.value =
            edited.value?.copy(content = text)
    }

    fun likeById(unconfirmedStatus: Int, id: Long, setLikedOn: Boolean) {
        viewModelScope.launch {
            try {
                repository.likeById(unconfirmedStatus, id, setLikedOn)
                _dataState.value = FeedModelState()     // При успехе
                ConsolePrinter.printText("MY LIKING TRY FINISHED")
            } catch (e: Exception) {
                ConsolePrinter.printText("MY LIKING CATCH STARTED: ${e.message.toString()}")
                _postActionFailed.value =
                    PostActionType.ACTION_POST_LIKE_CHANGE // Признак ошибки
            }
        }
        // завершение обработки лайка
    }

    fun shareById(unconfirmedStatus: Int, id: Long) {
        // TODO()  //Наш сервер пока не обрабатывает шаринг, поэтому не наращиваем счетчик

    }

    fun removeById(unconfirmedStatus: Int, id: Long) {
        viewModelScope.launch {
            try {
                repository.removeById(unconfirmedStatus, id)
                _dataState.value = FeedModelState()     // При успехе
                ConsolePrinter.printText("MY TRY FINISHED")
            } catch (e: Exception) {
                ConsolePrinter.printText("MY CATCH STARTED: ${e.message.toString()}")
                _postActionFailed.value =
                    PostActionType.ACTION_POST_DELETION // Установим признак ошибки
            }
        }
    }


    fun setAllVisible() {
        viewModelScope.launch {
            repository.setAllVisible()
        }
    }


    //-------------------------------------------------
    fun startEditing(post: Post) {
        edited.value = post
        if (post.id == 0L) clearPhoto() // Здесь стираем картинку черновика
    }

    fun quitEditing() {
        edited.value = emptyPost
    }

    fun setDraft(post: Post?) {
        // Черновик только для нового поста и только если вышли без сохранения
        if (post?.id == 0L)
            draft.value = post?.copy(content = post.content.trim())
                ?: emptyPost
    }

    fun getDraftContent(): String {
        return draft.value?.content ?: ""
    }
//    fun setDraftContent(draftContent: String) {
//        draft.value = draft.value?.copy(content = draftContent.trim()) // Главный поток
//    }
//    fun postDraftContent(draftContent: String) {
//        draft.postValue(draft.value?.copy(content = draftContent.trim())) // Фоновый поток!!!
//    }

}
