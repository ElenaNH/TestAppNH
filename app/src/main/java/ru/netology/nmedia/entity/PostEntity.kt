package ru.netology.nmedia.entity

import androidx.room.*
import retrofit2.http.POST
import ru.netology.nmedia.dto.Attachment
import ru.netology.nmedia.dto.Post
import ru.netology.nmedia.enumeration.AttachmentType
//@PrimaryKey(autoGenerate = true)  // Тут нужен двупольный ключ во избежание конфликта идентификаторов
@Entity(primaryKeys = ["unconfirmed","id"])
data class PostEntity(
    val id: Long,
    val author: String,
    val authorAvatar: String,
    val content: String,
    val published: String,
    val likedByMe: Boolean,
    val likes: Int = 0,
    @Embedded
    var attachment: AttachmentEmbeddable?,
//    val attachment: Attachment? = null,
    val unconfirmed: Int,   // ОТСУТСТВУЕТ В СЕРВЕРНОЙ БД
    val unsaved: Int,       // ОТСУТСТВУЕТ В СЕРВЕРНОЙ БД
    val deleted: Int = 0,   // ОТСУТСТВУЕТ В СЕРВЕРНОЙ БД
    val hidden: Int,        // ОТСУТСТВУЕТ В СЕРВЕРНОЙ БД
    val unsavedAttach: Int = 0,       // ОТСУТСТВУЕТ В СЕРВЕРНОЙ БД
) {
    // С одной стороны мы можем добавлять свои поля для своих целей
    // С другой стороны - серверное поле content мы уже используем для записи данных,
    // которые только готовимся отправить
    // Так что не страшно, если мы и поля из Embedded attachment так же задействуем

    fun toDto(): Post {
        // Сначала проверим наличие ссылки внутри поста (возьмем первую подходящую)
        val regex = "(https?://)?([\\w-]{1,32})(\\.[\\w-]{1,32})+[^\\s@]*".toRegex()
        val match = regex.find(content)
        // Если ссылка есть в тексте, то поместим ее в отдельное поле
        // Если нет ссылки, то поле ссылки будет пустым

        return if (deleted == 0)
            Post(
                id,
                author,
                authorAvatar,
                content,
                match?.value,
                published,
                likedByMe,
                likes,
                0,
                0,
                attachment?.toDto(),
                unconfirmed,
                unsaved,
                hidden,
                unsavedAttach,
            )
        else
            // Подумать - не лучше ли выбрасывать ошибку
            Post.getEmptyPost()     // Если пост удален, то вернем пустой неподтвержденный пост текущего автора
    }


    companion object {
        fun fromDto(dto: Post) =
            PostEntity(
                dto.id,
                dto.author,
                dto.authorAvatar,
                dto.content,
                dto.published,
                dto.likedByMe,
                dto.likes,
                AttachmentEmbeddable.fromDto(dto.attachment),
                dto.unconfirmed,
                dto.unsaved,
                0,
                dto.hidden,
                dto.unsavedAttach,
            ) // Мы все-таки будем следить, чтобы удаленные энтити не превращались в посты

    }
}

data class AttachmentEmbeddable(
    var url: String,
    var description: String?,
    var type: String,
) {
    fun toDto() = Attachment(url, description, AttachmentType.valueOf(type))

    companion object {
        fun fromDto(dto: Attachment?) = dto?.let {
            AttachmentEmbeddable(it.url, it.description, it.type.toString())
        }
    }
}


// Функции расширения для списков

fun List<PostEntity>.toDto(): List<Post> = map(PostEntity::toDto)
    //.filter { it.id != 0L } - пока у нас и так будут ненулевые id, ведь в базе есть автонумератор
fun List<Post>.fromDto(): List<PostEntity> = map(PostEntity::fromDto)
    .filter { it.deleted == 0 } // Берем только неудаленные записи таблицы "энтити"
