package ru.netology.nmedia.activity

import android.view.Gravity
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.google.android.material.snackbar.Snackbar
import ru.netology.nmedia.R
import androidx.fragment.app.findFragment
import androidx.fragment.app.viewModels
//import androidx.appcompat.app.AppCompatActivity
//import ru.netology.nmedia.databinding.FragmentFeedBinding
//import ru.netology.nmedia.databinding.FragmentPostBinding
import ru.netology.nmedia.dto.Post
import ru.netology.nmedia.enumeration.PostActionType
import ru.netology.nmedia.viewmodel.PostViewModel

// Сейчас это уже не функция расширения, но она все равно вызывается из фрагментов - оставим в этом файле
fun whenPostActionFailed(
    bindingRoot: ViewGroup, viewModel: PostViewModel, postActionType: PostActionType
) {
    // При подписке на однократную ошибку
    val thisViewModel = viewModel
    //val thisFragment = bindingRoot.findFragment<Fragment>() // Потом может быть разница
    val thisFragmentActivity = bindingRoot.findFragment<Fragment>().activity
    if (thisFragmentActivity != null) {
        val msgInfo = when (postActionType) {
            PostActionType.ACTION_POST_SAVING -> thisFragmentActivity.getString(R.string.error_post_saving)
            PostActionType.ACTION_POST_LIKE_CHANGE -> thisFragmentActivity.getString(R.string.error_post_like_change)
            PostActionType.ACTION_POST_DELETION -> thisFragmentActivity.getString(R.string.error_post_deletion)
        }

        val thisAction = { thisViewModel.refresh() }
        // Поскольку все намерения записаны в локальной БД, то требуется только синхронизация с сервером
        // (на данный момент она осуществляется методом refresh() при загрузке постов в репозиторий)
        // А если мы сделаем только синхронизацию одного поста, то остальное подвисшее не обновится
        Snackbar.make(bindingRoot, msgInfo, Snackbar.LENGTH_LONG)
            .setAction(R.string.retry_loading) { thisAction() }  // { thisViewModel.refresh() }
            .show()
    }  // Без else - Если нет активити, то и не делаем ничего
}


/*
fun PostViewModel.whenPostActionFailed(
    bindingRoot: ViewGroup, postActionType: PostActionType
) {
    // При подписке на однократную ошибку
    val thisViewModel = this
    val thisFragmentActivity = bindingRoot.findFragment<Fragment>().activity
    if (thisFragmentActivity != null) {
        val msgInfo = when (postActionType) {
            PostActionType.ACTION_POST_SAVING -> thisFragmentActivity.getString(R.string.error_post_saving)
            PostActionType.ACTION_POST_LIKE_CHANGE -> thisFragmentActivity.getString(R.string.error_post_like_change)
            PostActionType.ACTION_POST_DELETION -> thisFragmentActivity.getString(R.string.error_post_deletion)
        }

        // Поскольку все намерения записаны в локальной БД, то требуется только синхронизация
        // (на данный момент она осуществляется методом refresh() при загрузке постов в репозиторий)
        Snackbar.make(bindingRoot, msgInfo, Snackbar.LENGTH_LONG)
            .setAction(R.string.retry_loading) { thisViewModel.refresh() }
            .show()
    }  // Без else - Если нет активити, то и не делаем ничего
}
*/


/*fun Fragment.whenPostActionSucceed(viewModel: PostViewModel, postActionType: PostActionType) {
    // При подписке на однократное успешное действие
    val fragmentInput = this
    val toastInfo = when (postActionType) {
        PostActionType.ACTION_POST_SAVING -> fragmentInput.getString(R.string.succeed_post_saving)
        PostActionType.ACTION_POST_LIKE_CHANGE -> fragmentInput.getString(R.string.succeed_post_like_change)
        PostActionType.ACTION_POST_DELETION -> fragmentInput.getString(R.string.succeed_post_deletion)
    }
    showToast(this, toastInfo)  // Всплывающее сообщение
}*/

private fun showToast(fragmentInput: Fragment, toastInfo: String) {
    // Всплывающее сообщение
    val warnToast = Toast.makeText(
        fragmentInput.activity,
        toastInfo,
        Toast.LENGTH_SHORT
    )
    warnToast.setGravity(Gravity.CENTER_VERTICAL, 0, 0)
    warnToast.show()
}

/*
fun FragmentActivity.textPostActionFailed(postActionType: PostActionType): String {
    // При подписке на однократную ошибку
    val activityInput = this
    return when (postActionType) {
        PostActionType.ACTION_POST_SAVING -> activityInput.getString(R.string.error_post_saving)
        PostActionType.ACTION_POST_LIKE_CHANGE -> activityInput.getString(R.string.error_post_like_change)
        PostActionType.ACTION_POST_DELETION -> activityInput.getString(R.string.error_post_deletion)
    }
}
*/




