package com.master.exoplayer

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.postDelayed
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.ClippingMediaSource
import com.google.android.exoplayer2.source.LoopingMediaSource
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaExtractor
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.dash.DashMediaSource
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.*
import com.google.android.exoplayer2.upstream.cache.*
import com.google.android.exoplayer2.util.Util
import java.io.File

class ExoPlayerHelper(
    val mContext: Context,
    private val playerView: PlayerView,
    private val configs: Configs = Configs()
) : LifecycleObserver {

    private var mPlayer: ExoPlayer
    var progressRequired: Boolean = false

    companion object {
        private var simpleCache: SimpleCache? = null
        var mLoadControl: DefaultLoadControl? = null
        var mDataSourceFactory: DataSource.Factory? = null
        var mCacheEnabled = false
    }

    init {
        if (mCacheEnabled != configs.enableCache || mDataSourceFactory == null) {

            mDataSourceFactory = null

            val bandwidthMeter = DefaultBandwidthMeter()
            mDataSourceFactory = DefaultDataSourceFactory(
                mContext,
                Util.getUserAgent(mContext, mContext.getString(R.string.app_name)),
                bandwidthMeter
            )

            // LoadControl that controls when the MediaSource buffers more media, and how much media is buffered.
            // LoadControl is injected when the player is created.
            val builder = DefaultLoadControl.Builder()
            builder.setAllocator(DefaultAllocator(true, 2 * 1024 * 1024))
            builder.setBufferDurationsMs(
                configs.minBufferMs,
                configs.maxBufferMs,
                configs.bufferForPlaybackMs,
                configs.bufferForPlaybackAfterRebufferMs
            )
            builder.setPrioritizeTimeOverSizeThresholds(true)
            mLoadControl = builder.createDefaultLoadControl()

            if (configs.enableCache) {
                val evictor = LeastRecentlyUsedCacheEvictor(configs.cacheSizeInMb * 1024 * 1024)
                val file = File(mContext.getCacheDir(), "media")

                if (simpleCache == null)
                    simpleCache = SimpleCache(file, evictor)

                mDataSourceFactory = DefaultDataSourceFactory(
                    mContext,
                    Util.getUserAgent(mContext, mContext.getString(R.string.app_name)),
                    bandwidthMeter
                )
            }
        }
        mCacheEnabled = configs.enableCache

        mPlayer = ExoPlayer.Builder(mContext).build()
        playerView.setShutterBackgroundColor(Color.TRANSPARENT)
        playerView.player = mPlayer

    }

    private var mediaSource: MediaSource? = null
    private var isPreparing = false //This flag is used only for callback

    /**
     * Sets the url to play
     *
     * @param url url to play
     * @param autoPlay whether url will play as soon it Loaded/Prepared
     */
    private var url: String = ""

    fun setUrl(url: String, autoPlay: Boolean = false) {
        if (lifecycle?.currentState == Lifecycle.State.RESUMED) {
            this.url = url
            mediaSource = buildMediaSource(Uri.parse(url))
            loopIfNecessary()
            mPlayer.playWhenReady = autoPlay
            isPreparing = true
            mPlayer.prepare(mediaSource!!)
        }
    }

    var lifecycle: Lifecycle? = null
    fun makeLifeCycleAware(activity: AppCompatActivity) {
        lifecycle = activity.lifecycle
        activity.lifecycle.addObserver(this)
    }

    fun makeLifeCycleAware(fragment: Fragment) {
        lifecycle = fragment.lifecycle
        fragment.lifecycle.addObserver(this)
    }

    /**
     * Trim or clip media to given start and end milliseconds,
     * Ensure you must call this method after [setUrl] method call
     * You Make sure start time < end time ( Something you do :) )
     *
     * @param start starting time in millisecond
     * @param end ending time in millisecond
     */
    fun clip(start: Long, end: Long) {
        if (mediaSource != null) {
            mediaSource = ClippingMediaSource(mediaSource!!, start * 1000, end * 1000)
            loopIfNecessary()
        }
        mediaSource?.let { mPlayer.prepare(it) }
    }

    private fun buildMediaSource(uri: Uri): MediaSource {
        val type = Util.inferContentType(uri)
        when (type) {
            C.TYPE_SS -> return SsMediaSource.Factory(mDataSourceFactory!!).createMediaSource(MediaItem.fromUri(Uri.parse(url)))
            C.TYPE_DASH -> return DashMediaSource.Factory(mDataSourceFactory!!).createMediaSource(MediaItem.fromUri(Uri.parse(url)))
            C.TYPE_HLS -> return HlsMediaSource.Factory(mDataSourceFactory!!).createMediaSource(MediaItem.fromUri(Uri.parse(url)))
            C.TYPE_OTHER -> return ProgressiveMediaSource.Factory(mDataSourceFactory!!)
                .createMediaSource(MediaItem.fromUri(Uri.parse(url)))
            else -> {
                throw IllegalStateException("Unsupported type: $type") as Throwable
            }
        }
    }

    /**
     * Looping if user set if looping necessary
     */
    private fun loopIfNecessary() {
        if (configs.loopVideo) {
            mediaSource = LoopingMediaSource(mediaSource!!, configs.loopCount)
        }
    }

    /**
     * Used to start player
     * Ensure you must call this method after [setUrl] method call
     */
    fun play() {
        mPlayer.playWhenReady = true
    }

    /**
     * Used to pause player
     * Ensure you must call this method after [setUrl] method call
     */
    fun pause() {
        mPlayer.playWhenReady = false
    }

    /**
     * Used to stop player
     * Ensure you must call this method after [setUrl] method call
     */
    fun stop() {
        mPlayer.stop()
    }


    /**
     * Used to seek player to given position(in milliseconds)
     * Ensure you must call this method after [setUrl] method call
     */
    fun seekTo(positionMs: Long) {
        mPlayer.seekTo(positionMs)
    }


    val durationHandler = Handler()
    private var durationRunnable: Runnable? = null

    private fun startTimer() {
        if (progressRequired) {
            if (durationRunnable != null)
                durationHandler.postDelayed(durationRunnable!!, 17)
        }
    }

    private fun stopTimer() {
        if (progressRequired) {
            if (durationRunnable != null)
                durationHandler.removeCallbacks(durationRunnable!!)
        }
    }

    /**
     * Returns SimpleExoPlayer instance you can use it for your own implementation
     */
    fun getPlayer(): ExoPlayer {
        return mPlayer
    }

    /**
     * Used to set different quality url of existing video/audio
     */
    fun setQualityUrl(qualityUrl: String) {
        val currentPosition = mPlayer.currentPosition
        mediaSource = buildMediaSource(Uri.parse(qualityUrl))
        loopIfNecessary()
        mPlayer.prepare(mediaSource!!)
        mPlayer.seekTo(currentPosition)
    }

    /**
     * Normal speed is 1f and double the speed would be 2f.
     */
    fun setSpeed(speed: Float) {
        val param = PlaybackParameters(speed)
        mPlayer.setPlaybackParameters(param)
    }

    /**
     * Returns whether player is playing
     */
    fun isPlaying(): Boolean {
        return mPlayer.playWhenReady
    }

    /**
     * Toggle mute and unmute
     */
    fun toggleMuteUnMute() {
        if (mPlayer.volume == 0f) unMute() else mute()
    }

    /**
     * Mute player
     */
    fun mute() {
        mPlayer.volume = 0f
    }

    /**
     * Unmute player
     */
    fun unMute() {
        mPlayer.volume = 1f
    }


    //Life Cycle
    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    protected fun onPause() {
        mPlayer.playWhenReady = false
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    protected fun onDestroy() {
        simpleCache?.release()
        simpleCache = null
        mPlayer.playWhenReady = false
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    protected fun onResume() {
        mPlayer.playWhenReady = true
    }

    //LISTENERS

    /**
     * Listener that used for most popular callbacks
     */
    fun setListener(progressRequired: Boolean = false, listener: Listener) {
        this.progressRequired = progressRequired
        mPlayer.addListener(object : Player.Listener {



            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {

                //Log.i("EXO", "onPlayerStateChanged $playWhenReady with ${url}")
                if (isPreparing && playbackState == Player.STATE_READY) {
                    isPreparing = false
                    listener.onPlayerReady()
                }
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        listener.onBuffering(true)
                    }
                    Player.STATE_READY -> {
                        listener.onBuffering(false)
                        if (playWhenReady) {
                            startTimer()
                            listener.onStart()
                        } else {
                            stopTimer()
                            listener.onStop()
                        }
                    }
                    Player.STATE_IDLE -> {
                        stopTimer()
                        listener.onBuffering(false)
                        listener.onError(null)
                    }
                    Player.STATE_ENDED -> {
                        listener.onBuffering(false)
                        stopTimer()
                        listener.onStop()
                    }
                }
            }
        })

        playerView.setControllerVisibilityListener { visibility ->
            listener.onToggleControllerVisible(visibility == View.VISIBLE)
        }

        if (progressRequired) {
            durationRunnable = Runnable {
                listener.onProgress(mPlayer.currentPosition)
                if (mPlayer.playWhenReady) {
                    durationRunnable?.let { durationHandler.postDelayed(it, 500) }
                }
            }
        }
    }

    interface Listener {
        fun onPlayerReady() {}
        fun onStart() {}
        fun onStop() {}
        fun onProgress(positionMs: Long) {}
        fun onError(error: ExoPlaybackException?) {}
        fun onBuffering(isBuffering: Boolean) {}
        fun onToggleControllerVisible(isVisible: Boolean) {}

    }
}