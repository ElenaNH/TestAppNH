package ru.netology.nmedia.activity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
//import androidx.fragment.app.viewModels       // Вместо этого используем androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
//import androidx.navigation.Navigation.findNavController  // этот не подходит
//import androidx.navigation.findNavController  // и этот не подходит (но он использовался для перехода из активити)
//import android.view.Gravity
//import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver
import com.google.android.material.snackbar.Snackbar
import androidx.core.view.isVisible
import ru.netology.nmedia.R
import ru.netology.nmedia.activity.NewPostFragment.Companion.textArg
import ru.netology.nmedia.uiview.PostInteractionListenerImpl // Было до клиент-серверной модели
import ru.netology.nmedia.adapter.PostsAdapter
import ru.netology.nmedia.viewmodel.PostViewModel
import ru.netology.nmedia.databinding.FragmentFeedBinding
import ru.netology.nmedia.dto.Post
import ru.netology.nmedia.enumeration.PostActionType
import ru.netology.nmedia.util.ConsolePrinter


class FeedFragment : Fragment() {
    //  viewModels используем теперь с аргументом, чтобы сделать общую viewModel для всех фрагментов
//    val viewModel: PostViewModel by viewModels(ownerProducer = ::requireParentFragment)
    private val viewModel: PostViewModel by activityViewModels()

    // interactionListener должен быть доступен также из фрагмента PostFragment
    private val interactionListener by lazy { PostInteractionListenerImpl(viewModel, this) }

    // создаем привязку к элементам макета по первому обращению к ним
    //private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private lateinit var binding: FragmentFeedBinding // как сделать by lazy ????

    val adapter by lazy { PostsAdapter(interactionListener) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = makeBinding(
            container
        )   // в лекции layoutInflater, а в примерах переданный параметр inflater

        // Содержимое onCreate, оставшееся от activity, должно быть здесь
        binding.list.adapter =
            adapter   // val adapter = PostsAdapter(interactionListener) вынесли выше и отдали by lazy
        subscribe()     // все подписки, которые могут нам потребоваться в данной активити
        setListeners()  // все лиснеры всех элементов данной активити

        return binding.root
    }


    private fun makeBinding(
        container: ViewGroup?
    ): FragmentFeedBinding {
        return FragmentFeedBinding.inflate(
            layoutInflater,
            container,
            false  // false означает, что система сама добавить этот view, когда посчитает нужным
        )  // в лекции layoutInflater, а в примерах переданный параметр inflater
    }

    private fun subscribe() {
        // Подписки:

        // Подписка на FeedModel - список сообщений и состояние этого списка
        viewModel.dataState.observe(viewLifecycleOwner) { state ->
            binding.progress.isVisible = state.loading
            if (state.error) {
                Snackbar.make(binding.root, R.string.error_loading, Snackbar.LENGTH_LONG)
                    .setAction(R.string.retry_loading) { viewModel.loadPosts() }
                    .show()
            }
            binding.refreshLayout.isRefreshing = state.refreshing
        }
        viewModel.data.observe(viewLifecycleOwner) { data ->
            adapter.submitList(data.posts)
            binding.emptyText.isVisible = data.empty
        }
        viewModel.newerCount.observe(viewLifecycleOwner) {
            // Если ответ сервера нулевой, то видимость плашки не меняем
            // Если ненулевой, то по нашей логике первая пачка постов будет сразу видна
            // А вторая и последующие пачки обновлений будут скрыты
            // Вот тогда и покажем плашку
            if (it > 0)
                binding.someUnread.isVisible = true
            // else - ничего не делаем, кнопка пропадет после обновления recyclerview и прокрутки
        }

        // Подписка на адаптер
        adapter.registerAdapterDataObserver(object : AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                if (positionStart == 0) {
                    binding.someUnread.isVisible = false
                    binding.list.smoothScrollToPosition(0)
                }
            }
        })

        // Подписка на однократную ошибку
        viewModel.postActionFailed.observe(viewLifecycleOwner) { // Сообщаем однократно
            whenPostActionFailed(binding.root, viewModel, it)
            if (it == PostActionType.ACTION_POST_LIKE_CHANGE) {
                adapter.submitList(viewModel.data.value?.posts)
            }
        }

    }

    private fun setListeners() {
        // Обработчики кликов

        binding.fab.setOnClickListener {
            // Запуск фрагмента NewPostFragment
            findNavController().navigate(
                R.id.action_feedFragment_to_newPostFragment,
                Bundle().apply {
                    ConsolePrinter.printText("Draft content for textArg = ${viewModel.getDraftContent()}")
                    //Через вьюмодель
                    viewModel.startEditing(viewModel.draft.value ?: Post.getEmptyPost())
                    //Через аргумент
                    textArg =
                        viewModel.getDraftContent()  // В запускаемый фрагмент передаем содержимое черновика
                    // Эта передача имеет смысл для двух разных активитей, а у нас фрагменты
                    // так что это архаизм, и можно все передать через вьюмодель
                }
            )

        }

        binding.refreshLayout.setOnRefreshListener {
            viewModel.refresh()
        }

        binding.someUnread.setOnClickListener {
            // Показать скрытые посты
            viewModel.setAllVisible()
            // Скрыть кнопку
            it.isVisible = false
            // Прокрутить к верхнему посту можно в наблюдателе за адаптером (подписаться на него)

        }

    }
}
