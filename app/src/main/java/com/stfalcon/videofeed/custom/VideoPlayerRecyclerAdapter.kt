package com.stfalcon.videofeed.custom

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.agrawalsuneet.dotsloader.loaders.TashieLoader
import com.bumptech.glide.RequestManager
import com.stfalcon.videofeed.R
import com.stfalcon.videofeed.extensions.safeLet
import com.stfalcon.videofeed.models.MediaObject
import kotlinx.android.extensions.LayoutContainer

class VideoPlayerRecyclerAdapter : RecyclerView.Adapter<VideoPlayerRecyclerAdapter.VideoPlayerViewHolder>() {

    var onItemClickListener: ((Int) -> Unit)? = null
    var mediaObjects: ArrayList<MediaObject>? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }
    var requestManager: RequestManager? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoPlayerViewHolder {
        return VideoPlayerViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.layout_video_list_item, parent, false))
    }

    override fun getItemCount() = mediaObjects?.size ?: 0

    override fun onBindViewHolder(holder: VideoPlayerViewHolder, position: Int) {
        safeLet(mediaObjects?.get(position), requestManager) { mediaObj, manager ->
            holder.onBind(mediaObj, manager)
        }
    }

    inner class VideoPlayerViewHolder(override val containerView: View)
        : RecyclerView.ViewHolder(containerView), LayoutContainer {

        var title: TextView = containerView.findViewById(R.id.title)
        var thumbnail: ImageView = containerView.findViewById(R.id.thumbnail)
        var progressBar: TashieLoader = containerView.findViewById(R.id.progressBar)
        lateinit var requestManager: RequestManager

        fun onBind(mediaObject: MediaObject, requestManager: RequestManager) {
            this.requestManager = requestManager
            containerView.tag = this
            title.text = mediaObject.title
            this.requestManager
                .load(mediaObject.thumbnail)
                .into(thumbnail)

            containerView.setOnClickListener {
                onItemClickListener?.invoke(adapterPosition)
            }
        }
    }
}