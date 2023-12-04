package ru.netology.nmedia.uiview

import android.content.Intent
import android.net.Uri
import android.os.Bundle
//import androidx.core.content.ContextCompat.startActivity
import ru.netology.nmedia.R
import ru.netology.nmedia.activity.NewPostFragment.Companion.textArg
import ru.netology.nmedia.dto.Post
import ru.netology.nmedia.util.ARG_POST_ID
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import ru.netology.nmedia.activity.FeedFragment
import ru.netology.nmedia.activity.PostFragment
import ru.netology.nmedia.adapter.OnInteractionListener
import ru.netology.nmedia.enumeration.AttachmentType
import ru.netology.nmedia.model.PhotoModel
import ru.netology.nmedia.util.ARG_POST_UNCONFIRMED
import ru.netology.nmedia.viewmodel.PostViewModel

class PostInteractionListenerImpl(viewModelInput: PostViewModel, fragmentInput: Fragment) :
    OnInteractionListener {
    /* Необходимо применить интерфейс OnInteractionListener к конкретному фрагменту
    и для этого фрагмента (т.е., для view) реализовать все необходимые обработчики */
    private val viewModel: PostViewModel = viewModelInput
    private val fragmentParent = fragmentInput

    override fun onLike(post: Post) {
        viewModel.likeById(post.unconfirmed,post.id, !post.likedByMe)
    }

    override fun onShare(post: Post) {
        val intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, post.content)
            type = "text/plain"
        }
        // Следующая строка необязательная - интент на красивый выбор запускаемого приложения
        val shareIntent =
            Intent.createChooser(intent, fragmentParent.getString(R.string.chooser_share_post))
        // А здесь мы могли запустить наш intent без красоты, либо улучшенный shareIntent
        fragmentParent.startActivity(shareIntent)
        // Увеличиваем счетчик шаринга
        viewModel.shareById(post.unconfirmed, post.id)
    }

    override fun onRemove(post: Post) {
        viewModel.removeById(post.unconfirmed, post.id)
        if (!(fragmentParent is FeedFragment))
            fragmentParent.findNavController()
                .navigateUp() //  Закрытие текущего фрагмента (если это не стартовый FeedFragment)
    }

    override fun onEdit(post: Post) {
        viewModel.startEditing(post)
        // Если пост непустой, то запустим фрагмент редактирования поста newPostFragment

        // Поскольку мы уже нахдимся во фрагменте, то не нужен аргумент, задающий граф навигации
        // Но нам нужно знать, в каком мы фрагменте, чтобы задать правильный переход
        val action_from_to =
            when {
                (fragmentParent is FeedFragment) -> R.id.action_feedFragment_to_newPostFragment
                (fragmentParent is PostFragment) -> R.id.action_postFragment_to_newPostFragment
                else -> null
            }

        if (action_from_to != null)
            fragmentParent.findNavController().navigate(
                action_from_to,
                Bundle().apply {
                    textArg =
                        post.content.toString()  // В запускаемый фрагмент передаем данные редактируемого поста
                    //viewModel.setPhoto() НАДО ВЫЯСНИТЬ Uri и File
                }

            ) // Когда тот фрагмент закроется, опять окажемся здесь (по стеку)
    }

    override fun onVideoLinkClick(post: Post) {
        if (((post.videoLink ?: "") != "") and (post.attachment == null)
        ) {
            // Когда есть аттач или нет ссылки, то не переходим по ссылке

            // Тут по-другому создается интент
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(post.videoLink))

            // Следующая строка необязательная - интент на красивый выбор запускаемого приложения
            val shareIntent =
                Intent.createChooser(intent, fragmentParent.getString(R.string.chooser_share_post))
            // А здесь мы могли запустить наш intent без красоты, либо улучшенный shareIntent
            fragmentParent.startActivity(shareIntent)
        } else if (post.attachment?.type == AttachmentType.IMAGE) {
            // Запуск фрагмента ImageFragment
            // Поскольку мы уже нахдимся во фрагменте, то не нужен аргумент, задающий граф навигации
            // Но нам нужно знать, в каком мы фрагменте, чтобы задать правильный переход
            val action_from_to =
                when {
                    (fragmentParent is FeedFragment) -> R.id.action_feedFragment_to_imageFragment
                    (fragmentParent is PostFragment) -> R.id.action_postFragment_to_imageFragment
                    else -> null
                }
            if (action_from_to != null)
                fragmentParent.findNavController().navigate(
                    action_from_to,
                    Bundle().apply {
                        // Передаем ключевые элементы поста
                        putLong(ARG_POST_ID, post.id)
                        putInt(ARG_POST_UNCONFIRMED, post.unconfirmed)
                    }

                ) // Когда тот фрагмент закроется, опять окажемся здесь (по стеку)
        }
    }

    override fun onViewSingle(post: Post) {
        if (!(fragmentParent is FeedFragment)) return
        // Если мы тут, то это FeedFragment
        fragmentParent.findNavController().navigate(
            R.id.action_feedFragment_to_postFragment,
            Bundle().apply {
                putLong(ARG_POST_ID, post.id)
                putInt(ARG_POST_UNCONFIRMED, post.unconfirmed)
                // Почему-то написание еще одного синглтона не кажется хорошей идеей
                // Не писать же синглтон под каждый аргумент - чем это проще putLong?

                //textArg =
                //    post.content.toString()  // В запускаемый фрагмент передаем данные редактируемого поста
            }
        )
    }
}
