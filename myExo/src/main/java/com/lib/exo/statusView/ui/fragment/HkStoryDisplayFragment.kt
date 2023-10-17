package com.lib.exo.statusView.ui.fragment

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.widget.ViewPager2.SCROLL_STATE_DRAGGING
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.lib.exo.R
import com.lib.exo.databinding.FragmentHkStoryDisplayBinding
import com.lib.exo.statusView.common.INITIAL_STORY_INDEX
import com.lib.exo.statusView.common.extension.*
import com.lib.exo.statusView.common.gesturedetector.GestureListener
import com.lib.exo.statusView.common.gesturedetector.HkGestureDetector
import com.lib.exo.statusView.data.entity.HkStoryModel
import com.lib.exo.statusView.ui.HkStoryHorizontalProgressView
import com.lib.exo.statusView.ui.activity.HkStoryDisplayActivity.Companion.INDEX_OF_SELECTED_STORY
import com.lib.exo.statusView.ui.activity.HkStoryDisplayActivity.Companion.HK_FULLSCREEN_SINGLE_STORY_DISPLAY_TIME
import com.lib.exo.statusView.ui.activity.HkStoryDisplayViewModel

class HkStoryDisplayFragment(
    private val primaryStoryIndex: Int,
    private val invokeNextStory: ((Int) -> Unit)? = null,
    private val invokePreviousStory: ((Int) -> Unit)? = null
) : Fragment(), HkStoryHorizontalProgressView.HkStoryPlayerListener, GestureListener {

    private lateinit var hkGestureDetector: GestureDetector
    private lateinit var mBinding: FragmentHkStoryDisplayBinding
    private var isLongPressEventOccurred = false

    /**
     *  Used as callback argument to invoke whole next story from HkStoryDetailActivity
     */
    private var lastStoryPointIndex = INITIAL_STORY_INDEX
    private val hkStoryDisplayViewModel: HkStoryDisplayViewModel by lazy {
        ViewModelProvider(requireActivity())[HkStoryDisplayViewModel::class.java]
    }
    private var mStories = arrayListOf<HkStoryModel>()
    private var isResourceReady = false

    private var exoPlayer: ExoPlayer? = null
    private var storyDuration = 0L
    private var isCurrentStoryFinished = true
    private var animatedDrawable: GifDrawable? = null

    /**
     * Exoplayer listener
     */
    private val playerListener = object : Player.Listener {
        override fun onIsLoadingChanged(isLoading: Boolean) {
            super.onIsLoadingChanged(isLoading)
            if (isLoading)
                mBinding.dpvProgress.pause()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            super.onPlaybackStateChanged(playbackState)
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    pausePlayer()
                }

                Player.STATE_READY -> {
                    unblockInput()
                    isResourceReady = true
                    resumePlayer()
                    if (!isCurrentStoryFinished) {
                        mBinding.dpvProgress.resume()
                    } else {
                        isCurrentStoryFinished = false
                        if (isResumed && isVisible) {
                            hkStoryDisplayViewModel.updateStoryPoint(lastStoryPointIndex)

                            mBinding.dpvProgress.apply {
                                setSingleStoryDisplayTime(exoPlayer?.duration)
                                startAnimating(lastStoryPointIndex)
                            }
                        }
                    }
                }

                Player.STATE_ENDED -> {
                }

                Player.STATE_IDLE -> {
                }
            }
        }
    }

    companion object {
        fun newInstance(
            primaryStoryIndex: Int,
            invokeNextStory: ((Int) -> Unit)? = null,
            invokePreviousStory: ((Int) -> Unit)? = null
        ): HkStoryDisplayFragment {
            val args = Bundle()
            args.putInt(INDEX_OF_SELECTED_STORY, primaryStoryIndex)

            return HkStoryDisplayFragment(
                primaryStoryIndex, invokeNextStory, invokePreviousStory
            ).apply {
                arguments = args
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mStories = hkStoryDisplayViewModel.listOfUserStory[primaryStoryIndex].userStoryList
        // Fetch story duration of image file type here initially.
        storyDuration =
            hkStoryDisplayViewModel.getHorizontalProgressViewAttributes()[HK_FULLSCREEN_SINGLE_STORY_DISPLAY_TIME] as Long
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        mBinding = FragmentHkStoryDisplayBinding.inflate(layoutInflater)
        return mBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        registerLiveDataObserver()
        hkGestureDetector =
            GestureDetector(requireContext(), HkGestureDetector(this@HkStoryDisplayFragment))
        initStoryDisplayProgressView()
        setTouchListener()
    }

    private fun registerLiveDataObserver() {
        hkStoryDisplayViewModel.startOverStoryLiveData.observe(viewLifecycleOwner) {
            isCurrentStoryFinished = true
        }
    }

    /**
     * Method to check visibility of
     * fragment and based on that value decide
     * to start progress or pause progress
     * while viewpager transition.
     */
    private fun didVisibilityChange() {
        if (mStories.isEmpty()){
            requireActivity().finish()
            return
        }
        if (isResumed && isVisible) {
            // Once resumed and at last
            // our Fragment is really visible to user.
            val hkStoryModel = mStories.findLast {
                it.isStorySeen
            }
            val indexOfSeenStory = if (hkStoryModel != null) {
                mStories.indexOf(hkStoryModel)
            } else {
                INITIAL_STORY_INDEX
            }

            lastStoryPointIndex = indexOfSeenStory

            manageInitialStoryIndex()

            // Pre-fill the progress view for
            // story points those are already seen.
            with(mBinding.dpvProgress) {
                prefillProgressView(lastStoryPointIndex - 1)
                if (mStories[lastStoryPointIndex].isMediaTypeVideo.not()) {
                    mBinding.dpvProgress.setSingleStoryDisplayTime(storyDuration)
                    // Initial entry point for progress animation.
                    startAnimating(lastStoryPointIndex)
                }
            }

            showWithFade(mBinding.dpvProgress, mBinding.tvName, mBinding.tvTime)
        } else {
            mBinding.dpvProgress.pause()
        }
    }

    private fun manageInitialStoryIndex() {
        initMediaPlayer()
        if (mStories.isNotEmpty()) {
            when {
                lastStoryPointIndex == INITIAL_STORY_INDEX && mStories[lastStoryPointIndex].isStorySeen.not() -> {
                    lastStoryPointIndex = INITIAL_STORY_INDEX
                }

                lastStoryPointIndex == INITIAL_STORY_INDEX && mStories[lastStoryPointIndex].isStorySeen && (lastStoryPointIndex + 1) == mStories.count() -> {
                    lastStoryPointIndex = INITIAL_STORY_INDEX
                }

                lastStoryPointIndex >= INITIAL_STORY_INDEX && mStories[lastStoryPointIndex].isStorySeen && (lastStoryPointIndex + 1) < mStories.count() -> {
                    lastStoryPointIndex += 1
                }

                lastStoryPointIndex > INITIAL_STORY_INDEX && mStories[lastStoryPointIndex].isStorySeen && (lastStoryPointIndex + 1) == mStories.count() -> {
                    mBinding.dpvProgress.startOverProgress()
                    lastStoryPointIndex = INITIAL_STORY_INDEX
                }
            }

            if (mStories[lastStoryPointIndex].isMediaTypeVideo)
                prepareMedia(mStories[lastStoryPointIndex])
        }else{
            requireActivity().finish()
        }
    }

    private fun initStoryDisplayProgressView() {
        if (mStories.isNotEmpty()) {
            with(mBinding.dpvProgress) {
                consumeAttrib(
                    hkStoryDisplayViewModel.getHorizontalProgressViewAttributes(),
                    mStories.count()
                )
                setHkStoryPlayerListener(this@HkStoryDisplayFragment)
            }
            loadInitialData()
        }
    }

    /**
     * Load initial data. Image, name of user
     * and timestamp of story.
     */
    private fun loadInitialData() {
        require(mStories.isNotEmpty()) { "Provide list of URLs." }
        if (isResumed && isVisible) {
            mStories[INITIAL_STORY_INDEX].let {
                mBinding.tvName.text = it.name
                mBinding.tvTime.text = it.time
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setTouchListener() {
        with(mBinding) {
            controlView.setOnTouchListener { _, event ->
                // Pass current MotionEvent to gesture listener
                hkGestureDetector.onTouchEvent(event)

                // Pause progress once user Tap/Touch down,
                // also pause the progress when user swipe
                // forth and back between viewpager items.
                if (event.action == MotionEvent.ACTION_DOWN) {
                    mBinding.dpvProgress.pause()
                }

                // Once user release the touch event
                // resume the progress again.
                if (event.action == MotionEvent.ACTION_UP) {
                    // Check if long press event is still on, only then resume progress
                    // and make #isLongPressEventOccurred variable false.
                    if (isLongPressEventOccurred) {
                        // Resume progress bar animation when long press event released.
                        isLongPressEventOccurred = false
                        showWithFade(mBinding.dpvProgress, mBinding.tvName, mBinding.tvTime)
                        mBinding.dpvProgress.resume()
                        if (mStories[lastStoryPointIndex].isMediaTypeVideo)
                            resumePlayer()
                        else
                            resumeGIFAnimation()
                    }
                }
                true
            }
        }
    }

    /**
     * Callback of progress
     *
     * index: indicates the current index
     *        of progress.
     */
    override fun onStartedPlaying(index: Int) {
        require(mStories.isNotEmpty()) { "Provide list of URLs." }
        if (isResumed && isVisible) {
            lastStoryPointIndex = index
            managePlayerVisibility(mStories[index])

            mStories[index].let {
                mBinding.tvName.text = it.name
                mBinding.tvTime.text = it.time
            }
        }
    }

    override fun onStoryFinished(index: Int) {
        pausePlayer()
        isCurrentStoryFinished = true

        // Set story duration for next story instance priorly
        // because once next progress will start automatically,
        // it will consider duration of previous story item but not the next one.
        if (index + 1 < mStories.count()) {
            if (mStories[index + 1].isMediaTypeVideo.not())
                mBinding.dpvProgress.setSingleStoryDisplayTime(storyDuration)
        }
    }

    /**
     * Move to next story if exists or
     * exit detail view once all story points
     * are visited from particular story.
     *
     * isAtLastIndex: Invoke previous or next story
     *                based on this value.
     *
     * Callback to HkStoryDisplayActivity
     */
    override fun onFinishedPlaying(isAtLastIndex: Boolean) {
        if (isAtLastIndex)
            invokeNextStory?.invoke(lastStoryPointIndex)
        else
            invokePreviousStory?.invoke(lastStoryPointIndex)
    }

    private fun loadImage(index: Int) {
        mBinding.dpvProgress.pause()

        mStories.let { stories ->
            mBinding.ivHkStoryImage.loadImage(
                stories[index].mediaUrl, object : ImageLoadingListener {
                    override fun onLoadFailed() {
                        startPostponedEnterTransition()
                        showErrorAlert()
                        mBinding.dpvProgress.pause()
                        unblockInput()
                    }

                    override fun onResourceReady(bitmap: Bitmap, drawable: Drawable?) {
                        isResourceReady = true
                        if (drawable is GifDrawable) {
                            animatedDrawable = drawable
                        }

                        startPostponedEnterTransition()
                        showWithFade(mBinding.dpvProgress, mBinding.tvName, mBinding.tvTime)
                        mBinding.dpvProgress.resume()
                        if (isVisible && isResumed)
                            hkStoryDisplayViewModel.updateStoryPoint(index)
                        unblockInput()
                    }
                })
        }
    }

    /**
     * Pause progress animation when user
     * perform long touch on view.
     */
    override fun onLongPressOccurred(e: MotionEvent?) {
        isLongPressEventOccurred = true
        pausePlayer()
        hideWithFade(mBinding.dpvProgress, mBinding.tvTime, mBinding.tvName)
    }

    /**
     * Perform single tap when user touch on story view.
     *
     * Invoke next story and previous story based on
     * the X value of touch event.
     */
    override fun onSingleTapOccurred(e: MotionEvent?) {
        isCurrentStoryFinished = true
        mBinding.dpvProgress.pause()
        exoPlayer?.stop()
        blockInput()

        e?.x?.let { x ->
            if (x < (mBinding.controlView.width.toFloat() / 3)) {
                // Invoke previous story point/whole story
                mBinding.dpvProgress.moveToPreviousStoryPoint(lastStoryPointIndex)

                if (lastStoryPointIndex > INITIAL_STORY_INDEX && lastStoryPointIndex < mStories.count()) {
                    if (mStories[lastStoryPointIndex - 1].isMediaTypeVideo.not()) {
                        mBinding.dpvProgress.setSingleStoryDisplayTime(storyDuration)
                    }
                    mBinding.dpvProgress.startAnimating(lastStoryPointIndex - 1)
                }
            } else {
                // Invoke next story point/whole story
                mBinding.dpvProgress.moveToNextStoryPoint()
            }
        }
    }

    /**
     * Resume progress once user releases touch
     * of viewpager (in HkStoryDisplayActivity class).
     */
    fun resumeProgress() {
        if (isResourceReady) {
            if (isResumed && isVisible) {
                showWithFade(mBinding.dpvProgress, mBinding.tvName, mBinding.tvTime)
                mBinding.dpvProgress.resume()
                if (mStories[lastStoryPointIndex].isMediaTypeVideo) {
                    resumePlayer()
                } else {
                    resumeGIFAnimation()
                }
            }
        }
    }

    /**
     * Pause video player when user starts dragging.
     * Resume video player when user stops dragging.
     *
     * draggingState: indicates the current state of
     *                viewpager state i.e dragging,
     *                settling or idle.
     */
    fun pauseExoPlayer(draggingState: Int) {
        if (draggingState == SCROLL_STATE_DRAGGING) {
            exoPlayer?.playWhenReady = false
            // Pause GIF if user starts swiping.
            if (animatedDrawable != null)
                animatedDrawable?.stop()
        }
    }

    private fun blockInput() {
        mBinding.controlView.isEnabled = false
    }

    private fun unblockInput() {
        mBinding.controlView.isEnabled = true
    }

    private fun showErrorAlert() {
        Toast.makeText(
            requireContext(),
            getString(R.string.txt_download_error),
            Toast.LENGTH_LONG
        ).show()
    }

    private fun initMediaPlayer() {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(requireContext())
                .setPauseAtEndOfMediaItems(true)
                .build()
        } else {
            exoPlayer?.release()
            exoPlayer = null
            exoPlayer = ExoPlayer.Builder(requireContext())
                .setPauseAtEndOfMediaItems(true)
                .build()
        }

        mBinding.videoPlayerContainer.player = exoPlayer
        exoPlayer?.addListener(playerListener)
    }

    private fun managePlayerVisibility(hkStoryModel: HkStoryModel) {
        if (hkStoryModel.isMediaTypeVideo) {
            prepareMedia(hkStoryModel)
            mBinding.ivHkStoryImage.hide()
            mBinding.videoPlayerContainer.show()
        } else {
            if (exoPlayer != null && exoPlayer?.isPlaying == true) {
                exoPlayer?.playWhenReady = false
            }
            mBinding.ivHkStoryImage.show()
            mBinding.videoPlayerContainer.hide()
            loadImage(lastStoryPointIndex)
        }
    }

    private fun prepareMedia(hkStoryModel: HkStoryModel) {
        val videoUri = Uri.parse(hkStoryModel.mediaUrl)
        val mediaItem = MediaItem.fromUri(videoUri)
        exoPlayer?.setMediaItem(mediaItem)
        exoPlayer?.prepare()
    }

    private fun releasePlayer() {
        exoPlayer?.removeListener(playerListener)
        exoPlayer?.release()
        exoPlayer = null
    }

    private fun pausePlayer() {
        if (!mStories[lastStoryPointIndex].isMediaTypeVideo) {
            if (isResourceReady && animatedDrawable is GifDrawable) {
                animatedDrawable?.stop()
            }
        }
        mBinding.dpvProgress.pause()
        exoPlayer?.playWhenReady = false
    }

    private fun resumeGIFAnimation() {
        if (!mStories[lastStoryPointIndex].isMediaTypeVideo) {
            if (isResourceReady && animatedDrawable != null) {
                animatedDrawable?.start()
            }
        }
    }

    private fun resumePlayer() {
        if (isResumed)
            exoPlayer?.playWhenReady = true
    }

    override fun onResume() {
        super.onResume()
        didVisibilityChange()
    }

    override fun onPause() {
        isResourceReady = false
        didVisibilityChange()
        super.onPause()
    }

    override fun onStop() {
        releasePlayer()
        super.onStop()
    }
}