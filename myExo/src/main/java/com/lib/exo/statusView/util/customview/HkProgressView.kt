package com.lib.exo.statusView.util.customview

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.Animation
import android.view.animation.Animation.AnimationListener
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import com.lib.exo.R

class HkProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var frontProgressView: View? = null
    private var maxProgressView: View? = null
    private var hkScaleAnimation: HkScaleAnimation? = null
    private var duration = DEFAULT_PROGRESS_DURATION
    private var callback: Callback? = null
    private var isStarted = false

    init {
        LayoutInflater.from(context).inflate(R.layout.pausable_progress, this)
        frontProgressView = findViewById(R.id.front_progress)
        maxProgressView = findViewById(R.id.max_progress)
    }

    fun setDuration(duration: Long) {
        this.duration = duration
        if (hkScaleAnimation != null){
            hkScaleAnimation = null
            startProgress()
        }
    }

    fun setCallback(callback: Callback) {
        this.callback = callback
    }

    fun setMax() {
        finishProgress(true)
    }

    fun setMin() {
        finishProgress(false)
    }

    fun setMinWithoutCallback() {
        maxProgressView!!.setBackgroundResource(R.color.progress_secondary)
        maxProgressView!!.visibility = View.VISIBLE
        if (hkScaleAnimation != null) {
            hkScaleAnimation!!.setAnimationListener(null)
            hkScaleAnimation!!.cancel()
        }
    }

    fun setMaxWithoutCallback() {
        maxProgressView!!.setBackgroundResource(R.color.progress_max_active)
        maxProgressView!!.visibility = View.VISIBLE
        if (hkScaleAnimation != null) {
            hkScaleAnimation!!.setAnimationListener(null)
            hkScaleAnimation!!.cancel()
        }
    }

    private fun finishProgress(isMax: Boolean) {
        if (isMax) maxProgressView!!.setBackgroundResource(R.color.progress_max_active)
        maxProgressView!!.visibility = if (isMax) View.VISIBLE else View.GONE
        if (hkScaleAnimation != null) {
            hkScaleAnimation!!.setAnimationListener(null)
            hkScaleAnimation!!.cancel()
            if (callback != null) {
                callback!!.onFinishProgress()
            }
        }
    }

    fun startProgress() {
        maxProgressView!!.visibility = View.GONE
        if (duration <= 0) duration = DEFAULT_PROGRESS_DURATION
        hkScaleAnimation =
            HkScaleAnimation(
                0f,
                1f,
                1f,
                1f,
                Animation.ABSOLUTE,
                0f,
                Animation.RELATIVE_TO_SELF,
                0f
            )
        hkScaleAnimation!!.duration = duration
        hkScaleAnimation!!.interpolator = LinearInterpolator()
        hkScaleAnimation!!.setAnimationListener(object : AnimationListener {
            override fun onAnimationStart(animation: Animation) {
                if (isStarted) {
                    return
                }
                isStarted = true
                frontProgressView!!.visibility = View.VISIBLE
                if (callback != null) callback!!.onStartProgress()
            }

            override fun onAnimationEnd(animation: Animation) {
                isStarted = false
                if (callback != null) callback!!.onFinishProgress()
            }

            override fun onAnimationRepeat(animation: Animation) {
                //NO-OP
            }
        })
        hkScaleAnimation!!.fillAfter = true
        frontProgressView!!.startAnimation(hkScaleAnimation)
    }

    fun pauseProgress() {
        if (hkScaleAnimation != null) {
            hkScaleAnimation!!.pause()
        }
    }

    fun resumeProgress() {
        if (hkScaleAnimation != null) {
            hkScaleAnimation!!.resume()
        }
    }

    fun clear() {
        if (hkScaleAnimation != null) {
            hkScaleAnimation!!.setAnimationListener(null)
            hkScaleAnimation!!.cancel()
            hkScaleAnimation = null
        }
    }

    interface Callback {
        fun onStartProgress()
        fun onFinishProgress()
    }

    companion object {
        private const val DEFAULT_PROGRESS_DURATION = 4000L
    }
}