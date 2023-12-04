package ru.netology.nmedia.activity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
//import androidx.fragment.app.viewModels
import androidx.fragment.app.activityViewModels
import ru.netology.nmedia.uiview.PostInteractionListenerImpl
import ru.netology.nmedia.adapter.PostViewHolder
import ru.netology.nmedia.databinding.FragmentFeedBinding
import ru.netology.nmedia.databinding.FragmentPostBinding
import ru.netology.nmedia.util.ARG_POST_ID
import ru.netology.nmedia.util.ARG_POST_UNCONFIRMED
import ru.netology.nmedia.viewmodel.PostViewModel

class PostFragment : Fragment() {
    //  viewModels используем теперь с аргументом, чтобы сделать общую viewModel для всех фрагментов
//    private val viewModel: PostViewModel by viewModels(ownerProducer = ::requireParentFragment)
    private val viewModel: PostViewModel by activityViewModels()
    private val interactionListener by lazy { PostInteractionListenerImpl(viewModel, this) }
    private lateinit var binding: FragmentPostBinding // надо сделать by lazy

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = FragmentPostBinding.inflate(
            inflater,
            container,
            false
        )

        subscribe()

        return binding.root
    }

    private fun subscribe() {

        // Подписка на список сообщений
        viewModel.data.observe(viewLifecycleOwner) { state ->
            // далее перерисовка только нашего поста
            // Фильтровать неудобно, потому что вдруг нет такого id? Тогда список будет пустой
            // Это придется обрабатывать, усложняя код
            val current_post_id = arguments?.getLong(ARG_POST_ID) ?: 0
            val current_post_unconfirmed = arguments?.getInt(ARG_POST_UNCONFIRMED) ?: 0
            state.posts.forEach { post ->
                if ((post.id == current_post_id) && (post.unconfirmed == current_post_unconfirmed)) {

                    val currentViewHolder = PostViewHolder(binding.post, interactionListener)
                    currentViewHolder.bind(post)

                    return@forEach  // Если один нашли, то остальные не обрабатываем, даже с таким же id
                }
            }
        }
        // Подписка на однократную ошибку
        viewModel.postActionFailed.observe(viewLifecycleOwner) { // Сообщаем однократно
            whenPostActionFailed(binding.root, viewModel, it)
        }

    }

    /* // Если уж передавать аргумент, то это должен быть целый пост;
    // что-то не хочется разводить огород из синглтонов для каждого поля
    companion object {
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            PostFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    //putString(ARG_PARAM2, param2)
                }
            }
    }*/
}
