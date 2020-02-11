package com.protonvpn.android.utils

import androidx.annotation.CallSuper
import androidx.databinding.ViewDataBinding
import com.xwray.groupie.OnItemClickListener
import com.xwray.groupie.OnItemLongClickListener
import com.xwray.groupie.databinding.BindableItem
import com.xwray.groupie.databinding.GroupieViewHolder

abstract class BindableItemEx<T : ViewDataBinding> : BindableItem<T>() {

    private var bindingInternal: T? = null

    protected val binding: T
        get() = bindingInternal ?: throw IllegalStateException("view used after unbind()")

    @CallSuper
    override fun bind(
        viewHolder: GroupieViewHolder<T>,
        position: Int,
        payloads: List<Any?>,
        onItemClickListener: OnItemClickListener?,
        onItemLongClickListener: OnItemLongClickListener?
    ) {
        // When data in groupie adapter is updated some of the items will not unbind, and we won't
        // be able to do any cleanup. Force cleanup in items whose view holder is being taken over
        // by new item.
        val currentItem = viewHolder.item
        if (currentItem != this && currentItem is BindableItemEx<*>)
            currentItem.clear()

        super.bind(viewHolder, position, payloads, onItemClickListener, onItemLongClickListener)
    }

    override fun bind(viewBinding: T, position: Int) {
        // Sometimes we can get 2 binds in a row without unbind in between
        clear()
        bindingInternal = viewBinding
    }

    override fun unbind(viewHolder: GroupieViewHolder<T>) {
        super.unbind(viewHolder)
        clear()
        bindingInternal?.unbind()
        bindingInternal = null
    }

    // Clear anything that can cause memory leak here
    protected abstract fun clear()
}
