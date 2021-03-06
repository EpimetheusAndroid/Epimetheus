package tk.hacker1024.epimetheus.fragments

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.findNavController
import androidx.palette.graphics.Palette
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import kotlinx.android.synthetic.main.fragment_media_control.*
import kotlinx.android.synthetic.main.fragment_media_control.view.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import tk.hacker1024.epimetheus.*
import tk.hacker1024.epimetheus.service.INTENT_ID_KEY

class MediaControlFragment : Fragment() {
    internal lateinit var viewModel: EpimetheusViewModel
    private var mediaController: MediaControllerCompat? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProviders.of(requireActivity())[EpimetheusViewModel::class.java]
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_media_control, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.appBarColor.observe(this, Observer {
            if (it != 0) view.setBackgroundColor(it)
        })
        viewModel.titleColor.observe(this, Observer {
            if (it != 0) view.playing_title.setTextColor(it)
        })
        viewModel.subtitleColor.observe(this, Observer {
            if (it != 0) view.playing_subtitle.setTextColor(it)
        })

        view.stop.setOnClickListener {
            mediaController!!.transportControls.stop()
            view.visibility = View.GONE
        }

        view.rewind.setOnClickListener {
            MediaControllerCompat.getMediaController(requireActivity()).transportControls.rewind()
        }

        view.play_pause.setOnClickListener {
            when (mediaController!!.playbackState.state) {
                PlaybackStateCompat.STATE_PAUSED -> {
                    mediaController!!.transportControls.play()
                }
                PlaybackStateCompat.STATE_PLAYING -> {
                    mediaController!!.transportControls.pause()
                }
            }
        }

        view.fast_forward.setOnClickListener {
            mediaController!!.transportControls.fastForward()
        }

        view.skip.setOnClickListener {
            mediaController!!.transportControls.skipToNext()
        }

        view.setOnClickListener {
            findNavController().apply {
                if (currentDestination!!.id != R.id.queueFragment) {
                    popBackStack(R.id.stationListFragment, false)
                    navigate(StationListFragmentDirections.actionStationListFragmentToQueueFragment())
                }
            }
        }

        view.playing_subtitle.isSelected = true
    }

    override fun onStart() {
        super.onStart()

        fun onConnect() {
            mediaController = MediaControllerCompat.getMediaController(requireActivity())
            mediaController!!.registerCallback(controllerCallback)
            updatePlaybackState(mediaController!!.playbackState.state)
            mediaController!!.metadata?.let {
                controllerCallback.onMetadataChanged(it)
            }
            showIfServiceRunning()
        }

        (requireActivity() as MainActivity).connectMediaBrowser {
            onConnect()
        }
    }

    override fun onStop() {
        super.onStop()
        oldUri = null
        mediaController?.unregisterCallback(controllerCallback)
    }

    private fun updatePlaybackState(state: Int) {
        fun changeClickableState(clickable: Boolean) {
            view?.rewind?.isClickable = clickable
            view?.play_pause?.isClickable = clickable
            view?.fast_forward?.isClickable = clickable
        }

        when (state) {
            PlaybackStateCompat.STATE_BUFFERING, PlaybackStateCompat.STATE_CONNECTING ->
                changeClickableState(false)

            PlaybackStateCompat.STATE_PLAYING -> {
                view?.play_pause?.setImageResource(R.drawable.ic_pause_black_24dp)
                changeClickableState(true)
            }

            PlaybackStateCompat.STATE_PAUSED -> {
                view?.play_pause?.setImageResource(R.drawable.ic_play_arrow_black_24dp)
                changeClickableState(true)
            }
        }
    }

    internal fun show() {
        activity?.runOnUiThread {
            view!!.visibility = View.VISIBLE
        }
    }

    internal fun hide() {
        view!!.visibility = View.GONE
    }

    internal fun showIfServiceRunning() {
        if (serviceRunning) show()
    }

    private var oldUri: String? = null
    private val controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat) = updatePlaybackState(state.state)

        @SuppressLint("SetTextI18n")
        override fun onMetadataChanged(metadata: MediaMetadataCompat) {
            if (oldUri != metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI)) {
                view?.let { view ->
                    view.playing_title?.text = metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
                    view.playing_subtitle?.text = "${metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)} (${metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM)})"

                    GlideApp
                        .with(this@MediaControlFragment)
                        .load(metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART))
                        .placeholder(playing_art.drawable)
                        .transform(RoundedCorners(32))
                        .transition(DrawableTransitionOptions.withCrossFade())
                        .into(view.playing_art)
                    oldUri = metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI)

                    GlobalScope.launch {
                        Palette.Builder(metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART))
                            .generate().apply {
                                viewModel.apply {
                                    appBarColor.postValue(getDarkVibrantColor(Color.DKGRAY))
                                    statusBarColor.postValue(getDarkVibrantColor(Color.DKGRAY).darken)
                                    titleColor.postValue(getLightVibrantColor(Color.WHITE))
                                    subtitleColor.postValue(getLightMutedColor(Color.LTGRAY))
                                }
                            }
                    }
                }
            }
        }
    }

    private inline val serviceRunning
        get() = mediaController?.extras?.getInt(INTENT_ID_KEY, -1) ?: -1 >= 0
}
