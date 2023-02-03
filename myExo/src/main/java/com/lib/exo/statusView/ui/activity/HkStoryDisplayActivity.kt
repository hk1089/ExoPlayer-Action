package com.lib.exo.statusView.ui.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.widget.ViewPager2
import com.lib.exo.databinding.ActivityHkStoryDisplayBinding
import com.lib.exo.statusView.HkStoryView
import com.lib.exo.statusView.common.INITIAL_STORY_INDEX
import com.lib.exo.statusView.ui.adapter.HkStoryDisplayAdapter
import com.lib.exo.statusView.ui.fragment.HkStoryDisplayFragment
import com.lib.exo.statusView.util.HkPageChangeListener
import com.lib.exo.statusView.util.transformers.*

class HkStoryDisplayActivity : FragmentActivity() {
    companion object {
        const val HK_LIST_OF_STORY = "HkListOfStory"
        const val INDEX_OF_SELECTED_STORY = "IndexOfSelectedStory"
        const val PAGE_TRANSFORMER = "PageTransformer"
        const val FIRST_STORY_ITEM_INDEX = INITIAL_STORY_INDEX
        const val FIRST_STORY_POINT_INDEX = INITIAL_STORY_INDEX

        const val HK_FULLSCREEN_PROGRESSBAR_HEIGHT = "hkFullScreenProgressBarHeight"
        const val HK_FULLSCREEN_GAP_BETWEEN_PROGRESSBAR = "hkFullScreenGapBetweenProgressBar"
        const val HK_FULLSCREEN_PROGRESSBAR_PRIMARY_COLOR = "hkFullScreenProgressBarPrimaryColor"
        const val HK_FULLSCREEN_PROGRESSBAR_SECONDARY_COLOR =
            "hkFullScreenProgressBarSecondaryColor"
        const val HK_FULLSCREEN_SINGLE_STORY_DISPLAY_TIME = "hkFullScreenSingleStoryDisplayTime"
        const val HORIZONTAL_PROGRESS_ATTRIBUTES = "horizontal_progress_attributes"
    }

    private val TAG = javaClass.simpleName
    private lateinit var pagerAdapterHk: HkStoryDisplayAdapter
    private lateinit var mBinding: ActivityHkStoryDisplayBinding
    private var indexOfSelectedStory = -1
    private var pageTransformer: HkStoryView.PageTransformer =
        HkStoryView.PageTransformer.BACKGROUND_TO_FOREGROUND_TRANSFORMER
    private val hkStoryDisplayViewModel: HkStoryDisplayViewModel by lazy {
        ViewModelProvider(this)[HkStoryDisplayViewModel::class.java]
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = ActivityHkStoryDisplayBinding.inflate(layoutInflater)
        setContentView(mBinding.root)

        if (intent != null) {
            if (intent.hasExtra(HK_LIST_OF_STORY)) {
                hkStoryDisplayViewModel.addListOfUserStories(
                    intent.getParcelableArrayListExtra(HK_LIST_OF_STORY)
                )
            }

            if (intent.hasExtra(INDEX_OF_SELECTED_STORY))
                indexOfSelectedStory = intent.getIntExtra(INDEX_OF_SELECTED_STORY, -1)

            if (intent.hasExtra(PAGE_TRANSFORMER))
                pageTransformer =
                    intent.extras?.getParcelable<HkStoryView.PageTransformer>(PAGE_TRANSFORMER) as HkStoryView.PageTransformer

            if (intent.hasExtra(HORIZONTAL_PROGRESS_ATTRIBUTES)) {
                hkStoryDisplayViewModel.setHorizontalProgressViewAttributes(
                    intent.getSerializableExtra(HORIZONTAL_PROGRESS_ATTRIBUTES) as HashMap<String, Any>
                )
            }
        }

        setupViewPager()
    }

    /**
     * Set up viewpager to generate
     * list of story views and move to
     * particular index to preview selected
     * story.
     **/
    private fun setupViewPager() {
        pagerAdapterHk = HkStoryDisplayAdapter(
            this,
            hkStoryDisplayViewModel.listOfUserStory, ::invokeNextStory, ::invokePreviousStory
        )

        mBinding.vpHkStoryViewPager.apply {
            adapter = pagerAdapterHk
            offscreenPageLimit = 1
            setPageTransformer(getPageTransformer())
            registerOnPageChangeCallback(onPageChangeCallback)

            // Move to particular index of story.
            if (indexOfSelectedStory > 0) {
                setCurrentItem(indexOfSelectedStory, false)
            }
            hkStoryDisplayViewModel.setMainStoryIndex(currentItem)
        }
    }

    private fun fetchCurrentVPItem(): Int {
        return mBinding.vpHkStoryViewPager.currentItem
    }

    /**
     * Move to next story
     * i.e set next story as current viewpager item
     * if exists else exit.
     */
    private fun invokeNextStory(currentStoryIndex: Int) {
        if (hkStoryDisplayViewModel.listOfUserStory[fetchCurrentVPItem()].userStoryList.size == (currentStoryIndex + 1)) {
            if ((hkStoryDisplayViewModel.listOfUserStory.count() - 1) > fetchCurrentVPItem()) {
                mBinding.vpHkStoryViewPager.currentItem += 1 // Move to next story once all story points are visited from particular story.
            } else {
                setResult() // Finish detail activity once all story points are visited
            }
        }
    }

    /**
     * Move to previous story
     * i.e set previous story as current viewpager item
     * if exists else exit.
     */
    private fun invokePreviousStory(currentStoryIndex: Int) {
        if (fetchCurrentVPItem() == FIRST_STORY_ITEM_INDEX
            && currentStoryIndex == FIRST_STORY_POINT_INDEX
        ) {
            setResult()
        } else {
            mBinding.vpHkStoryViewPager.currentItem -= 1
        }
    }

    /**
     * When viewpager page transition stopped/canceled,
     * start progress again else update data of
     * particular story according to index of viewpager.
     */
    private val onPageChangeCallback = object : HkPageChangeListener() {
        override fun onPageScrollCanceled() {
            val fragment = getFragmentByTag()
            if (fragment is HkStoryDisplayFragment) {
                fragment.resumeProgress()
            }
        }

        override fun onPageSelected(position: Int) {
            setCurrentPageIndex(position)
            hkStoryDisplayViewModel.setMainStoryIndex(position)
        }

        override fun onPageScrollStateChanged(state: Int) {
            super.onPageScrollStateChanged(state)
            val fragment = getFragmentByTag()
            if (fragment is HkStoryDisplayFragment) {
                fragment.pauseExoPlayer(state)
            }
        }
    }

    private fun getFragmentByTag(): Fragment? {
        return supportFragmentManager.findFragmentByTag("f" + fetchCurrentVPItem())
    }

    private fun getPageTransformer(): ViewPager2.PageTransformer {
        return when (pageTransformer) {
            HkStoryView.PageTransformer.BACKGROUND_TO_FOREGROUND_TRANSFORMER -> BackgroundToForegroundPageTransformer()
            HkStoryView.PageTransformer.FOREGROUND_TO_BACKGROUND_TRANSFORMER -> ForegroundToBackgroundPageTransformer()
            HkStoryView.PageTransformer.CUBE_OUT_TRANSFORMER -> CubeOutTransformer()
            HkStoryView.PageTransformer.ZOOM_OUT_PAGE_TRANSFORMER -> ZoomOutPageTransformer()

            HkStoryView.PageTransformer.CUBE_IN_TRANSFORMER -> CubeInPageTransformer()
            HkStoryView.PageTransformer.ROTATE_DOWN_PAGE_TRANSFORMER -> RotateDownPageTransformer()
            HkStoryView.PageTransformer.ROTATE_UP_PAGE_TRANSFORMER -> RotateUpPageTransformer()
            HkStoryView.PageTransformer.ZOOM_IN_TRANSFORMER -> ZoomInTransformer()
        }
    }

    private fun setResult() {
        createIntent()
        finish()
    }

    override fun onBackPressed() {
        createIntent()
        super.onBackPressed()
    }

    private fun createIntent() {
        val intent = Intent()
        intent.putParcelableArrayListExtra(
            HK_LIST_OF_STORY, hkStoryDisplayViewModel.listOfUserStory
        )
        intent.putExtra(INDEX_OF_SELECTED_STORY, fetchCurrentVPItem())
        setResult(RESULT_OK, intent)
    }
}