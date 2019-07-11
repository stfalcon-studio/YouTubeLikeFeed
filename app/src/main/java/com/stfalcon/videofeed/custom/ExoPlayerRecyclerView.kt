package com.stfalcon.videofeed.custom

import android.content.Context
import android.graphics.Color
import android.graphics.Point
import android.net.Uri
import android.util.AttributeSet
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.view.postDelayed
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.agrawalsuneet.dotsloader.loaders.TashieLoader
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.ExoPlayerFactory
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import com.stfalcon.videofeed.R
import com.stfalcon.videofeed.models.MediaObject
import kotlinx.android.synthetic.main.layout_video_list_item.view.*

class ExoPlayerRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    private enum class VolumeState {
        ON, OFF
    }

    // ui
    private var thumbnail: ImageView? = null
    private var progressBar: TashieLoader? = null
    private var viewHolderParent: View? = null
    private var frameLayout: FrameLayout? = null
    private var playerView: PlayerView? = null
    private var videoPlayer: SimpleExoPlayer? = null

    // vars
    private lateinit var mediaObjects: List<MediaObject>
    private var videoSurfaceDefaultHeight = 0
    private var screenDefaultHeight = 0
    private var playPosition = -1
    private var isVideoViewAdded: Boolean = false

    // controlling playback state
    private var volumeState: VolumeState? = null

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            super.onScrollStateChanged(recyclerView, newState)

            if (newState == SCROLL_STATE_IDLE) {
                if (thumbnail != null) { // show the old thumbnail
                    thumbnail?.visibility = View.VISIBLE
                }

                // There's a special case when the end of the list has been reached.
                // Need to handle that with this bit of logic
                if (!recyclerView.canScrollVertically(1)) {
                    playVideo(true)
                } else {
                    playVideo(false)
                }
            }
        }

    }

    private val attachStateChangeListener = object : OnChildAttachStateChangeListener {
        override fun onChildViewAttachedToWindow(view: View) {}

        override fun onChildViewDetachedFromWindow(view: View) {
            if (viewHolderParent != null && viewHolderParent?.equals(view) == true) {
                resetVideoView()
            }
        }
    }

    private val videoPlayerListener = object : Player.EventListener {
        override fun onTimelineChanged(timeline: Timeline?, manifest: Any?, reason: Int) {}

        override fun onTracksChanged(trackGroups: TrackGroupArray?, trackSelections: TrackSelectionArray?) {}

        override fun onLoadingChanged(isLoading: Boolean) {}

        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            when (playbackState) {

                Player.STATE_BUFFERING -> {
                    if (progressBar != null) {
                        progressBar?.visibility = View.VISIBLE
                    }
                }
                Player.STATE_ENDED -> {
                    videoPlayer?.seekTo(0)
                }
                Player.STATE_IDLE -> {
                }
                Player.STATE_READY -> {
                    if (progressBar != null) {
                        progressBar?.visibility = View.GONE
                    }
                    if (!isVideoViewAdded) {
                        seekVideoToRightPosition()
                    }
                }
                else -> {
                }
            }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {}

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {}

        override fun onPlayerError(error: ExoPlaybackException?) {}

        override fun onPositionDiscontinuity(reason: Int) {}

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters?) {}

        override fun onSeekProcessed() {
            addVideoView()
        }
    }

    init {
        val display = (getContext().getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        val point = Point()
        display.getSize(point)
        videoSurfaceDefaultHeight = point.x
        screenDefaultHeight = point.y

        val videoTrackSelectionFactory = AdaptiveTrackSelection.Factory()
        val trackSelector = DefaultTrackSelector(videoTrackSelectionFactory)

        //Create the player
        videoPlayer = ExoPlayerFactory.newSimpleInstance(context, trackSelector)

        // Bind the player to the view.
        playerView = PlayerView(context)
        playerView?.setKeepContentOnPlayerReset(true)
        playerView?.keepScreenOn = true
        playerView?.setShutterBackgroundColor(Color.TRANSPARENT)
        playerView?.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        playerView?.background = ContextCompat.getDrawable(context, R.drawable.shape_background)
        playerView?.useController = false
        (playerView?.videoSurfaceView as SurfaceView).setZOrderOnTop(false)
        (playerView?.videoSurfaceView as SurfaceView).setZOrderMediaOverlay(false)
        playerView?.player = videoPlayer
        setVolumeControl(VolumeState.OFF)

        //Add listeners
        addOnScrollListener(scrollListener)
        addOnChildAttachStateChangeListener(attachStateChangeListener)
        videoPlayer?.addListener(videoPlayerListener)
    }

    fun playVideo(isEndOfList: Boolean) {

        val itemTargetPosition = getTargetItemPlaying(isEndOfList)

        // something is wrong. return.
        if (itemTargetPosition == -1) {
            return
        }

        // video is already playing so return
        if (itemTargetPosition == playPosition) {
            return
        }

        // set the position of the list-item that is to be played
        playPosition = itemTargetPosition
        if (playerView == null) {
            return
        }

        mediaObjects[playPosition].position = videoPlayer?.contentPosition
        // remove old surface views from previously playing videos
        removeVideoView(playerView)

        val currentPosition = itemTargetPosition - (layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
        val child = getChildAt(currentPosition) ?: return
        val holder = child.tag as? VideoPlayerRecyclerAdapter.VideoPlayerViewHolder

        if (holder == null) {
            playPosition = -1
            return
        }

        thumbnail = holder.thumbnail
        progressBar = holder.progressBar
        viewHolderParent = holder.itemView
        frameLayout = holder.itemView.mediaСontainer

        playerView?.player = videoPlayer

        val dataSourceFactory = DefaultDataSourceFactory(context, Util.getUserAgent(context, context.getString(R.string.app_name)))
        val mediaUrl = mediaObjects[itemTargetPosition].mediaUrl
        val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(Uri.parse(mediaUrl))

        videoPlayer?.prepare(videoSource)
        videoPlayer?.playWhenReady = true
        progressBar?.visibility = View.VISIBLE
    }

    fun setMediaObjects(mediaObjects: ArrayList<MediaObject>) {
        this.mediaObjects = mediaObjects
    }

    fun releasePlayer() {
        if (videoPlayer != null) {
            videoPlayer?.release()
            videoPlayer = null
        }
        viewHolderParent = null
    }

    fun resumeVideo() {
        playVideo(false)
        if (thumbnail != null) {
            thumbnail?.visibility = View.VISIBLE
        }
    }

    fun pausePlayer() {
        if (videoPlayer != null) {
            videoPlayer?.playWhenReady = false
            playPosition = -1
        }
    }

    private fun getTargetItemPlaying(isEndOfList: Boolean): Int {
        return if (!isEndOfList) {
            val startPosition = (layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
            var endPosition = (layoutManager as LinearLayoutManager).findLastVisibleItemPosition()

            // if there is more than 2 list-items on the screen, set the difference to be 1
            if (endPosition - startPosition > 1) {
                endPosition = startPosition + 1
            }

            // something is wrong. return.
            if (startPosition < 0 || endPosition < 0) {
                return -1
            }

            // if there is more than 1 list-item on the screen
            if (startPosition != endPosition) {
                val startPositionVideoHeight = getVisibleVideoSurfaceHeight(startPosition)
                val endPositionVideoHeight = getVisibleVideoSurfaceHeight(endPosition)

                if (startPositionVideoHeight > endPositionVideoHeight) {
                    startPosition
                } else {
                    endPosition
                }
            } else {
                startPosition
            }
        } else {
            mediaObjects.size - 1
        }
    }

    private fun seekVideoToRightPosition() {
        videoPlayer?.seekTo(mediaObjects[playPosition].position ?: 1)
    }

    private fun getVisibleVideoSurfaceHeight(playPosition: Int): Int {
        val at = playPosition - (layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()

        val child = getChildAt(at) ?: return 0

        val location = IntArray(2)
        child.getLocationInWindow(location)

        return if (location[1] < 0) {
            location[1] + videoSurfaceDefaultHeight
        } else {
            screenDefaultHeight - location[1]
        }
    }

    private fun removeVideoView(playerView: PlayerView?) {
        val parent = playerView?.parent as? ViewGroup ?: return

        val index = parent.indexOfChild(playerView)
        if (index >= 0) {
            //parent.removeViewAt(index)

            thumbnail?.visibility = View.VISIBLE
            playerView.visibility = View.INVISIBLE
            frameLayout?.removeView(playerView)
            frameLayout?.requestLayout()

            isVideoViewAdded = false
            progressBar?.visibility = View.GONE
        }
    }

    private fun addVideoView() {
        //videoPlayer?.seekTo(mediaObjects[playPosition].position ?: 1)
        frameLayout?.addView(playerView)
        isVideoViewAdded = true
        playerView?.requestFocus()
        thumbnail?.postDelayed(150) {
            thumbnail?.visibility = View.INVISIBLE
            playerView?.visibility = VISIBLE
        }
    }

    private fun resetVideoView() {
        if (isVideoViewAdded) {
            playPosition = -1
            playerView?.visibility = INVISIBLE
            thumbnail?.visibility = VISIBLE
            progressBar?.visibility = View.GONE
            removeVideoView(playerView)
        }
    }

    private fun setVolumeControl(state: VolumeState) {
        volumeState = state
        if (state == VolumeState.OFF) {
            videoPlayer?.volume = 0f
        } else if (state == VolumeState.ON) {
            videoPlayer?.volume = 1f
        }
    }
}