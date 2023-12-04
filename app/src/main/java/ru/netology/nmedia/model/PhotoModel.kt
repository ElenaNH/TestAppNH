package ru.netology.nmedia.model

import android.net.Uri
//import ru.netology.nmedia.util.ConsolePrinter
import java.io.File

data class PhotoModel(
    val uri: Uri,
    val file: File,

    )

fun photoModel(stringUri: String): PhotoModel? {
    //Может получиться, что локальный файл уже удален, пока мы сохранялись,
    // пока восстанавливали соединение
    val model =
        try {
            // Странно, что приходится вычищать этот префикс,
            // но иначе в файле и в uri появляются какие-то левые символы /file%3A/
            // Тут явно какой-то подвох
            // В примерах в и-нете никто ничего не вычищает, а все работает!
            val file =
                if (stringUri.substring(0, 8) == "file:///")
                    File(stringUri.substring(7, stringUri.length))
                else File(stringUri)

            // Если не проверить существование файла,
            // то будет устанавливаться пустое изображение
            if (file.exists()) {
                val uri = Uri.fromFile(file)
                PhotoModel(uri, file)
            } else null
        } catch (e: Exception) {
            null
        }

    return model
}
