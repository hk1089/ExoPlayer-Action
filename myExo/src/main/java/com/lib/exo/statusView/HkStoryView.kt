package com.lib.exo.statusView

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.content.res.TypedArray
import android.graphics.*
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.Nullable
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.upstream.cache.CacheWriter
import com.lib.exo.R
import com.lib.exo.statusView.common.CacheUtils
import com.lib.exo.statusView.common.extension.ImageLoadingListener
import com.lib.exo.statusView.common.extension.dpToPx
import com.lib.exo.statusView.common.extension.loadThumbnailImage
import com.lib.exo.statusView.data.entity.HkStoryModel
import com.lib.exo.statusView.data.entity.HkUserStoryModel
import com.lib.exo.statusView.ui.HkStoryHorizontalProgressView
import com.lib.exo.statusView.ui.activity.HkStoryDisplayActivity
import com.lib.exo.statusView.ui.activity.HkStoryDisplayActivity.Companion.HORIZONTAL_PROGRESS_ATTRIBUTES
import com.lib.exo.statusView.ui.activity.HkStoryDisplayActivity.Companion.INDEX_OF_SELECTED_STORY
import com.lib.exo.statusView.ui.activity.HkStoryDisplayActivity.Companion.HK_FULLSCREEN_GAP_BETWEEN_PROGRESSBAR
import com.lib.exo.statusView.ui.activity.HkStoryDisplayActivity.Companion.HK_FULLSCREEN_PROGRESSBAR_HEIGHT
import com.lib.exo.statusView.ui.activity.HkStoryDisplayActivity.Companion.HK_FULLSCREEN_PROGRESSBAR_PRIMARY_COLOR
import com.lib.exo.statusView.ui.activity.HkStoryDisplayActivity.Companion.HK_FULLSCREEN_PROGRESSBAR_SECONDARY_COLOR
import com.lib.exo.statusView.ui.activity.HkStoryDisplayActivity.Companion.HK_FULLSCREEN_SINGLE_STORY_DISPLAY_TIME
import com.lib.exo.statusView.ui.activity.HkStoryDisplayActivity.Companion.HK_LIST_OF_STORY
import com.lib.exo.statusView.ui.activity.HkStoryDisplayActivity.Companion.PAGE_TRANSFORMER
import kotlinx.parcelize.Parcelize

private const val PRECACHE_VIDEO_WORKER_NAME = "precache_video_worker"

class HkStoryView : View {
    companion object {
        const val HK_STORY_IMAGE_RADIUS_IN_DP = 36
        const val HK_STORY_INDICATOR_WIDTH_IN_DP = 4
        const val SPACE_BETWEEN_IMAGE_AND_INDICATOR = 4
        const val HK_START_ANGLE = 270

        const val HK_PENDING_INDICATOR_COLOR = "#009988"
        const val HK_VISITED_INDICATOR_COLOR = "#33009988"
    }

    private var mAppCompatActivity: AppCompatActivity? = null
    private var resource: Resources? = null
    private var hkPaintIndicator: Paint? = null
    private var hkPageTransformer = PageTransformer.BACKGROUND_TO_FOREGROUND_TRANSFORMER
    private var hkStoryImageRadiusInPx = 0
    private var hkStoryIndicatorWidthInPx = 0
    private var hkSpaceBetweenImageAndIndicator = 0
    private var hkPendingIndicatorColor = 0
    private var hkVisitedIndicatorColor = 0

    private var hkStoryViewHeight = 0
    private var hkStoryViewWidth = 0
    private var hkIndicatorOffset = 0
    private var hkIndicatorImageOffset = 0
    private var hkIndicatorImageRect: Rect? = null

    private var hkStoryImageUrls: ArrayList<HkStoryModel>? = null
    private var hkStoryUrls: ArrayList<HkUserStoryModel>? = null
    private var hkIndicatorCount = 0
    private var hkIndicatorBendAngle = 0
    private var hkAngleOfGap = 15
    private var hkIndicatorImageBitmap: Bitmap? = null
    private var indexOfSelectedStory = -1
    private var launcher: ActivityResultLauncher<Intent>? = null

    //HkStoryHorizontalProgressView attributes
    private var hkFullScreenProgressBarHeight = 0
    private var hkFullScreenGapBetweenProgressBar = 0
    private var hkFullScreenProgressBarPrimaryColor = 0
    private var hkFullScreenProgressBarSecondaryColor = 0
    private var hkFullScreenSingleStoryDisplayTime: Long = 0

    constructor(context: Context) : super(context) {
//        CacheUtils.initializeCache(context)
        initView(context)
        setDefaults()
    }

    constructor(context: Context, @Nullable attrs: AttributeSet?) : super(context, attrs) {
        initView(context)
        val typedArray: TypedArray =
            context.obtainStyledAttributes(attrs, R.styleable.HkStoryView, 0, 0)
        try {
            resource?.let {
                hkPageTransformer = PageTransformer.values()[typedArray.getInt(
                    R.styleable.HkStoryView_hkPageTransformer, 0
                )]

                hkStoryImageRadiusInPx = typedArray.getDimension(
                    R.styleable.HkStoryView_hkStoryImageRadius,
                    HK_STORY_IMAGE_RADIUS_IN_DP.toFloat()
                ).toInt().dpToPx(it)

                hkStoryIndicatorWidthInPx = typedArray.getDimension(
                    R.styleable.HkStoryView_hkStoryItemIndicatorWidth,
                    HK_STORY_INDICATOR_WIDTH_IN_DP.toFloat()
                ).toInt().dpToPx(it)

                hkSpaceBetweenImageAndIndicator = typedArray.getDimension(
                    R.styleable.HkStoryView_hkSpaceBetweenImageAndIndicator,
                    SPACE_BETWEEN_IMAGE_AND_INDICATOR.toFloat()
                ).toInt().dpToPx(it)

                hkPendingIndicatorColor = typedArray.getColor(
                    R.styleable.HkStoryView_hkPendingIndicatorColor,
                    Color.parseColor(HK_PENDING_INDICATOR_COLOR)
                )

                hkVisitedIndicatorColor = typedArray.getColor(
                    R.styleable.HkStoryView_hkVisitedIndicatorColor,
                    Color.parseColor(HK_VISITED_INDICATOR_COLOR)
                )

                // Configure HkStoryHorizontalProgressView attributes here
                hkFullScreenProgressBarHeight = typedArray.getDimension(
                    R.styleable.HkStoryView_hkFullScreenProgressBarHeight,
                    HkStoryHorizontalProgressView.HK_PROGRESS_BAR_HEIGHT.toFloat()
                ).toInt().dpToPx(it)

                hkFullScreenGapBetweenProgressBar = typedArray.getDimension(
                    R.styleable.HkStoryView_hkFullScreenGapBetweenProgressBar,
                    HkStoryHorizontalProgressView.HK_GAP_BETWEEN_PROGRESS_BARS.toFloat()
                ).toInt().dpToPx(it)

                hkFullScreenProgressBarPrimaryColor = typedArray.getColor(
                    R.styleable.HkStoryView_hkFullScreenProgressBarPrimaryColor,
                    Color.parseColor(HkStoryHorizontalProgressView.HK_PROGRESS_PRIMARY_COLOR)
                )

                hkFullScreenProgressBarSecondaryColor = typedArray.getColor(
                    R.styleable.HkStoryView_hkFullScreenProgressBarSecondaryColor,
                    Color.parseColor(HkStoryHorizontalProgressView.HK_PROGRESS_SECONDARY_COLOR)
                )

                hkFullScreenSingleStoryDisplayTime = typedArray.getInt(
                    R.styleable.HkStoryView_hkFullScreenSingleStoryDisplayTime,
                    HkStoryHorizontalProgressView.HK_SINGLE_STORY_DISPLAY_TIME
                ).toLong()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            typedArray.recycle()
        }
        prepareValue()
    }

    fun setActivity(activity: AppCompatActivity) {
        mAppCompatActivity = activity
    }

    fun setLauncher(launcher: ActivityResultLauncher<Intent>) {
        this.launcher = launcher
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        hkPaintIndicator?.apply {
            color = hkPendingIndicatorColor
            strokeWidth = hkStoryIndicatorWidthInPx.toFloat()
            var startAngle = HK_START_ANGLE + hkAngleOfGap / 2
            for (i in 0 until hkIndicatorCount) {
                color = getIndicatorColor(i)
                canvas.drawArc(
                    hkIndicatorOffset.toFloat(), hkIndicatorOffset.toFloat(),
                    (hkStoryViewWidth - hkIndicatorOffset).toFloat(),
                    (hkStoryViewHeight - hkIndicatorOffset).toFloat(),
                    startAngle.toFloat(),
                    (hkIndicatorBendAngle - hkAngleOfGap / 2).toFloat(),
                    false,
                    this
                )
                startAngle += hkIndicatorBendAngle + hkAngleOfGap / 2
            }
            hkIndicatorImageBitmap?.let { bitmap ->
                hkIndicatorImageRect?.let { rect ->
                    canvas.drawBitmap(bitmap, null, rect, null)
                }
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = paddingStart + paddingEnd + hkStoryViewWidth
        val height = paddingTop + paddingBottom + hkStoryViewHeight
        val widthMeasure = resolveSizeAndState(width, widthMeasureSpec, 0)
        val heightMeasure = resolveSizeAndState(height, heightMeasureSpec, 0)
        setMeasuredDimension(widthMeasure, heightMeasure)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_UP) {
            navigationToStoryDisplay()
        }
        return true
    }

    private fun navigationToStoryDisplay() {
        if (mAppCompatActivity == null) {
            throw RuntimeException("Context must not be null.")
        } else {
            val bundle = Bundle()
            bundle.putParcelable(PAGE_TRANSFORMER, hkPageTransformer)

            val intent = Intent(mAppCompatActivity, HkStoryDisplayActivity::class.java)
            intent.putParcelableArrayListExtra(HK_LIST_OF_STORY, hkStoryUrls)
            intent.putExtra(INDEX_OF_SELECTED_STORY, indexOfSelectedStory)
            intent.putExtra(
                HORIZONTAL_PROGRESS_ATTRIBUTES,
                retrieveHorizontalProgressViewAttributes()
            )
            intent.putExtras(bundle)
            launcher?.launch(intent)
        }
    }

    private fun retrieveHorizontalProgressViewAttributes(): HashMap<String, Any> {
        val horizontalProgressValuesHashMap = hashMapOf<String, Any>()

        horizontalProgressValuesHashMap[HK_FULLSCREEN_PROGRESSBAR_HEIGHT] =
            hkFullScreenProgressBarHeight
        horizontalProgressValuesHashMap[HK_FULLSCREEN_GAP_BETWEEN_PROGRESSBAR] =
            hkFullScreenGapBetweenProgressBar
        horizontalProgressValuesHashMap[HK_FULLSCREEN_PROGRESSBAR_PRIMARY_COLOR] =
            hkFullScreenProgressBarPrimaryColor
        horizontalProgressValuesHashMap[HK_FULLSCREEN_PROGRESSBAR_SECONDARY_COLOR] =
            hkFullScreenProgressBarSecondaryColor
        horizontalProgressValuesHashMap[HK_FULLSCREEN_SINGLE_STORY_DISPLAY_TIME] =
            hkFullScreenSingleStoryDisplayTime

        return horizontalProgressValuesHashMap
    }

    private fun initView(context: Context) {
        resource = context.resources
        hkPaintIndicator = Paint()
        hkPaintIndicator?.apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
    }

    private fun setDefaults() {
        resource?.let {
            hkStoryImageRadiusInPx = HK_STORY_IMAGE_RADIUS_IN_DP.dpToPx(it)
            hkStoryIndicatorWidthInPx = HK_STORY_INDICATOR_WIDTH_IN_DP.dpToPx(it)
            hkSpaceBetweenImageAndIndicator = SPACE_BETWEEN_IMAGE_AND_INDICATOR.dpToPx(it)
            hkPendingIndicatorColor = Color.parseColor(HK_PENDING_INDICATOR_COLOR)
            hkVisitedIndicatorColor = Color.parseColor(HK_VISITED_INDICATOR_COLOR)
        }
        prepareValue()
    }

    private fun prepareValue() {
        hkStoryViewHeight =
            2 * (hkStoryIndicatorWidthInPx + hkSpaceBetweenImageAndIndicator + hkStoryImageRadiusInPx)
        hkStoryViewWidth = hkStoryViewHeight
        hkIndicatorOffset = hkStoryIndicatorWidthInPx / 2
        hkIndicatorImageOffset = hkStoryIndicatorWidthInPx + hkSpaceBetweenImageAndIndicator
        hkIndicatorImageRect = Rect(
            hkIndicatorImageOffset,
            hkIndicatorImageOffset,
            hkStoryViewWidth - hkIndicatorImageOffset,
            hkStoryViewHeight - hkIndicatorImageOffset
        )
    }

    fun setImageUrls(listOfStory: ArrayList<HkUserStoryModel>, indexOfSelectedStory: Int = 0) {
        hkStoryUrls = listOfStory
        this.indexOfSelectedStory = indexOfSelectedStory
        hkStoryImageUrls = listOfStory[indexOfSelectedStory].userStoryList
        hkIndicatorCount = listOfStory[indexOfSelectedStory].userStoryList.size
        calculateBendAngle(hkIndicatorCount)
        invalidate()
        loadLastImageBitmap()

        // precache videos if there are any
//        preloadVideos()
    }

    private fun preloadVideos() {
        hkStoryImageUrls?.map { data ->
            Thread().run {
                val dataUri = Uri.parse(data.mediaUrl)
                val dataSpec = DataSpec(dataUri)

                val mHttpDataSourceFactory = DefaultHttpDataSource.Factory()
                    .setAllowCrossProtocolRedirects(true)

                val mCacheDataSource = CacheDataSource.Factory()
                    .setCache(CacheUtils.getSimpleCache())
                    .setUpstreamDataSourceFactory(mHttpDataSourceFactory)
                    .createDataSource()

                val listener =
                    CacheWriter.ProgressListener { requestLength: Long, bytesCached: Long, _: Long ->
                        val downloadPercentage = (bytesCached * 100.0 / requestLength)
                        Log.e(javaClass.simpleName, "downloadPercentage: $downloadPercentage")
                    }

                try {
                    CacheWriter(
                        mCacheDataSource,
                        dataSpec,
                        null,
                        listener,
                    ).cache()

                    /*CacheUtil.cache(
                        dataSpec,
                        StoryApp.simpleCache,
                        CacheUtil.DEFAULT_CACHE_KEY_FACTORY,
                        dataSource,
                        listener,
                        null
                    )*/
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }


        /*hkStoryImageUrls?.map {
            if (it.mediaType == HkMediaType.VIDEO) {
                val workManager = WorkManager.getInstance(context.applicationContext)
                val videoPreloadWorker = VideoPreloadWorker.buildWorkRequest(it.mediaUrl)
                workManager.enqueueUniqueWork(
                    PRECACHE_VIDEO_WORKER_NAME, ExistingWorkPolicy.KEEP, videoPreloadWorker
                )
            }
        }*/
    }

    private fun loadLastImageBitmap() {
        loadThumbnailImage(
            context,
            hkStoryImageUrls?.first()?.mediaUrl,
            object : ImageLoadingListener {
                override fun onResourceReady(bitmap: Bitmap, drawable: Drawable?) {
                    hkIndicatorImageBitmap = bitmap
                    invalidate()
                }
            })
    }

    private fun calculateBendAngle(indicatorCount: Int) {
        hkAngleOfGap = if (indicatorCount == 1) {
            0
        } else {
            15
        }
        hkIndicatorBendAngle = 360 / indicatorCount - hkAngleOfGap / 2
    }

    private fun getIndicatorColor(index: Int): Int {
        return if (hkStoryImageUrls?.get(index)?.isStorySeen == true)
            hkVisitedIndicatorColor
        else
            hkPendingIndicatorColor
    }

    @Parcelize
    enum class PageTransformer : Parcelable {
        BACKGROUND_TO_FOREGROUND_TRANSFORMER,
        CUBE_OUT_TRANSFORMER,
        FOREGROUND_TO_BACKGROUND_TRANSFORMER,
        ZOOM_OUT_PAGE_TRANSFORMER,
        CUBE_IN_TRANSFORMER,
        ROTATE_DOWN_PAGE_TRANSFORMER,
        ROTATE_UP_PAGE_TRANSFORMER,
        ZOOM_IN_TRANSFORMER
    }
}