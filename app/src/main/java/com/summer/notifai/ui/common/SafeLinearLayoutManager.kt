package com.summer.notifai.ui.common

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * A LinearLayoutManager that handles IndexOutOfBoundsException during layout.
 * 
 * This is a workaround for a known issue where RecyclerView can crash with
 * "Inconsistency detected. Invalid item position" when the adapter's data
 * changes during a layout pass (e.g., during scroll or prefetch).
 * 
 * Key features:
 * - Disables item prefetching to prevent GapWorker crashes
 * - Catches IndexOutOfBoundsException in layout and scroll methods
 */
class SafeLinearLayoutManager : LinearLayoutManager {

    constructor(context: Context) : super(context) {
        isItemPrefetchEnabled = false
    }
    
    constructor(context: Context, orientation: Int, reverseLayout: Boolean) : 
        super(context, orientation, reverseLayout) {
        isItemPrefetchEnabled = false
    }
    
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : 
        super(context, attrs, defStyleAttr, defStyleRes) {
        isItemPrefetchEnabled = false
    }

    override fun onLayoutChildren(recycler: RecyclerView.Recycler?, state: RecyclerView.State?) {
        try {
            super.onLayoutChildren(recycler, state)
        } catch (e: IndexOutOfBoundsException) {
            e.printStackTrace()
        }
    }

    override fun scrollVerticallyBy(
        dy: Int,
        recycler: RecyclerView.Recycler?,
        state: RecyclerView.State?
    ): Int {
        return try {
            super.scrollVerticallyBy(dy, recycler, state)
        } catch (e: IndexOutOfBoundsException) {
            Log.w("SafeLinearLayoutManager", "IndexOutOfBoundsException during vertical scroll", e)
            dy
        }
    }

    override fun scrollHorizontallyBy(
        dx: Int,
        recycler: RecyclerView.Recycler?,
        state: RecyclerView.State?
    ): Int {
        return try {
            super.scrollHorizontallyBy(dx, recycler, state)
        } catch (e: IndexOutOfBoundsException) {
            Log.w("SafeLinearLayoutManager", "IndexOutOfBoundsException during horizontal scroll", e)
            dx
        }
    }
    
    override fun supportsPredictiveItemAnimations(): Boolean {
        return false
    }
}
