package ru.netology.nmedia.uiview

import android.widget.ImageView
import com.bumptech.glide.Glide
import ru.netology.nmedia.R
import ru.netology.nmedia.dto.Post
import ru.netology.nmedia.model.photoModel
import ru.netology.nmedia.util.BASE_URL

fun loadImage(post: Post?, imageView: ImageView) {
    // Сначала сбросим старое изображение
    imageView.setImageDrawable(null)
    if (post != null) {
        if (post.unsavedAttach == 1) {
            // Если изображение не сохранено на сервере, то загрузим  локальное
            try {
                imageView.setImageURI(photoModel(post.attachment?.url ?: "")?.uri)
            } catch (e: Exception) {
                imageView.setImageResource(R.drawable.ic_error_100dp)
            }
        } else {
            // Сохраненное изображение загрузим с сервера/кэша (ранее было в папке images, теперь - media
            val imgUrl =
                "$BASE_URL/media/${post.attachment?.url ?: ""}" // Если нет аттача, то мы сюда не попадем, но все же обработаем null
            // Условие для загрузки: wrap_content для высоты imageView
            Glide.with(imageView)
                .load(imgUrl)
                //.placeholder(R.drawable.ic_loading_100dp)
                .error(R.drawable.ic_error_100dp)
                .timeout(10_000)
                .into(imageView)

        }
    }
}

//Ниже идет работающее решение от Романа Лешина, которое работало даже при высоте match_parent для photo
/*Glide.with(binding.photo)
    .load(imgUrl)
    .error(R.drawable.ic_error_100dp)
    .timeout(10_000)
    .into(
        object : CustomTarget<Drawable>() {
            override fun onResourceReady(
                resource: Drawable,
                transition: Transition<in Drawable>?
            ) {
                binding.photo.setImageDrawable(resource)
                val layoutParams = binding.photo.layoutParams
                val width = resource.intrinsicWidth
                val height = resource.intrinsicHeight

                val displayMetrics =
                    binding.root.context.resources.displayMetrics
                val screenWidth = displayMetrics.widthPixels
                layoutParams.width = screenWidth

                val calculatedHeight =
                    (screenWidth.toFloat() / width.toFloat() * height).toInt()
                layoutParams.height = calculatedHeight
                binding.photo.layoutParams = layoutParams
            }

            override fun onLoadCleared(placeholder: Drawable?) {
                binding.photo.setImageDrawable(placeholder)
            }
        }
    )*/




