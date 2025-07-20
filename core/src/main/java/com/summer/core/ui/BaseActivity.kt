package com.summer.core.ui

import android.os.Bundle
import android.view.MenuItem
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding

abstract class BaseActivity<B : ViewDataBinding> : AppCompatActivity() {

    @get:LayoutRes
    protected abstract val layoutResId: Int
    
    protected open val isFullScreen: Boolean = false

    private var binding: B? = null
    protected val mBinding: B
        get() = binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        onPreCreated()
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, layoutResId)
        if (!isFullScreen) handleWindowInsets(true)
        onActivityReady(savedInstanceState)
    }

    /**
     * Toggle window insets handling for status bar
     * @param applyInsets If true, adds padding for status bar. If false, removes padding
     */
    protected fun handleWindowInsets(applyInsets: Boolean) {
        if (applyInsets) {
            ViewCompat.setOnApplyWindowInsetsListener(mBinding.root) { view, insets ->
                val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
                view.setPadding(0, statusBarHeight, 0, 0)
                insets
            }
        } else {
            ViewCompat.setOnApplyWindowInsetsListener(mBinding.root) { _, insets ->
                mBinding.root.setPadding(0, 0, 0, 0)
                insets
            }
        }
    }

    protected fun setupActionBar(toolbar: Toolbar) {
        setSupportActionBar(toolbar)
    }

    protected open fun onPreCreated() {}

    protected abstract fun onActivityReady(savedInstanceState: Bundle?)

    fun enableBack() {
        supportActionBar?.let {
            it.setDisplayHomeAsUpEnabled(true)
            it.setDisplayShowHomeEnabled(true)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            showExitDialog()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    protected open fun showExitDialog() {}

    override fun onDestroy() {
        binding = null
        super.onDestroy()
    }
}