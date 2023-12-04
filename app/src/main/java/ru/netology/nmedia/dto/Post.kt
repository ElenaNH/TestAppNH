package ru.netology.nmedia.dto

import android.graphics.drawable.Drawable
import ru.netology.nmedia.enumeration.AttachmentType
import ru.netology.nmedia.util.currentAuthor

fun Long.statisticsToString(): String {
    val stat = this
    return when {
        (stat >= 1_000_000L) -> {
            val mega = stat / 1_000_000L
            val megaDecimal = (stat % 1_000_000L) / 100_000L
            if (megaDecimal == 0L) "${mega}M" else "${mega}.${megaDecimal}M"
        }
        (stat >= 10_000L) -> "${stat / 1_000L}K"
        (stat >= 1_000L) -> {
            val kilo = stat / 1_000L
            val kiloDecimal = (stat % 1_000L) / 100L
            if (kiloDecimal == 0L) "${kilo}K" else "${kilo}.${kiloDecimal}K"
        }
        (stat < 0L) -> "XX"
        else -> "$stat"
    }
}

data class Attachment(
    val url: String,
    val description: String? = null, // этот параметр не удаляем, хоть и не используем его пока полноценно
    val type: AttachmentType,
)

/*ВНИМАНИЕ! поле likes должно называться именно так,
* чтобы работало преобразование gson.fromJson в объект Post из текста, пришедшего с сервера
* НО!!!
* Правильно было использовать PostEntity, который конвертировать далее через toDto*/
data class Post(
    val id: Long,
    val author: String,
    val authorAvatar: String,
    val content: String,
    val videoLink: String? = null,
    val published: String,  // val date: Int = (System.currentTimeMillis() / 86400000).toInt()
    val likedByMe: Boolean = false,
    val likes: Int,
    val countShare: Int,
    val countViews: Int,
    val attachment: Attachment? = null,
    val unconfirmed: Int = 0, // Все пришедшее с сервера будет "confirmed"
    val unsaved: Int = 0, // Все пришедшее с сервера будет "saved" (ведь на сервере нет этого поля)
    val hidden: Int,
    val unsavedAttach: Int = 0,
) {

 companion object {
        fun getEmptyPost(): Post {
            return Post(
                id = 0,
                author = currentAuthor(),
                authorAvatar = "",
                content = "",
                published = "",
                likedByMe = false,
                likes = 0,
                countShare = 0,
                countViews = 0,
                attachment = null,
                unconfirmed = 1,
                unsaved = 1,
                hidden = 0,
            )       // Новый пустой пост должен быть видим
        }
    }




}
