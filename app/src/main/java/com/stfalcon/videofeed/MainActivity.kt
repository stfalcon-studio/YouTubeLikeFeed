package com.stfalcon.videofeed

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.postDelayed
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import com.bumptech.glide.request.RequestOptions
import com.stfalcon.videofeed.custom.VerticalSpacingItemDecorator
import com.stfalcon.videofeed.custom.VideoPlayerRecyclerAdapter
import com.stfalcon.videofeed.models.MediaObject
import com.stfalcon.videofeed.utils.FixtureUtil
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initRecyclerView()
    }

    override fun onPause() {
        videoRecyclerView?.pausePlayer()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        videoRecyclerView?.postDelayed(100) {
            videoRecyclerView?.resumeVideo()
        }
    }

    override fun onDestroy() {
        videoRecyclerView?.releasePlayer()
        super.onDestroy()
    }

    private fun initRecyclerView() {
        val layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        videoRecyclerView.layoutManager = layoutManager
        val itemDecorator = VerticalSpacingItemDecorator(10)
        videoRecyclerView.addItemDecoration(itemDecorator)

        val mediaObjects = ArrayList<MediaObject>()
        mediaObjects.addAll(FixtureUtil.generateMediaList())
        videoRecyclerView.setMediaObjects(mediaObjects)

        val adapter = VideoPlayerRecyclerAdapter()
        adapter.mediaObjects = mediaObjects
        adapter.requestManager = initGlide()
        adapter.onItemClickListener = { position ->
            Toast.makeText(this, "Clicked at position ${position + 1}", Toast.LENGTH_SHORT).show()

            //FIXME Not working
            videoRecyclerView?.postDelayed(50) {
                layoutManager.scrollToPosition(position)
            }
        }

        videoRecyclerView.adapter = adapter
    }

    private fun initGlide(): RequestManager {
        val options = RequestOptions()
            .placeholder(R.drawable.shape_background)
            .error(R.drawable.shape_background)

        return Glide.with(this)
            .setDefaultRequestOptions(options)
    }
}
