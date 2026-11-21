package com.astraflow.frostshade

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.theme.CouiTheme

@Composable
internal fun OriginalPreview(modifier: Modifier = Modifier) {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    AndroidView(
        factory = ::PreviewVideoView,
        update = { it.setPlaying(lifecycle.isAtLeast(Lifecycle.State.RESUMED)) },
        onRelease = { it.release() },
        modifier = modifier.fillMaxWidth().aspectRatio(1164f / 1100f)
            .clip(CouiPageDefaults.shape).background(CouiTheme.colorScheme.card)
            .semantics { contentDescription = "旧版液态玻璃堆叠通知原版视频" },
    )
}

@SuppressLint("ViewConstructor")
private class PreviewVideoView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var prepared = false
    private var playing = false

    init {
        surfaceTextureListener = this
    }

    fun setPlaying(playing: Boolean) {
        this.playing = playing
        val media = player ?: return
        if (!prepared) return
        if (playing && !media.isPlaying) media.start()
        if (!playing && media.isPlaying) media.pause()
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        val output = Surface(texture)
        surface = output
        val media = MediaPlayer()
        player = media
        runCatching {
            context.resources.openRawResourceFd(R.raw.preview_sys_liquid_glass).use { source ->
                media.setDataSource(source.fileDescriptor, source.startOffset, source.length)
            }
            media.setSurface(output)
            media.isLooping = true
            media.setVolume(0f, 0f)
            media.setOnPreparedListener {
                prepared = true
                setPlaying(playing)
            }
            media.setOnErrorListener { _, what, extra ->
                Log.w("FrostShade", "Original preview playback failed: $what/$extra")
                release()
                true
            }
            media.prepareAsync()
        }.onFailure {
            Log.w("FrostShade", "Original preview could not be opened", it)
            release()
        }
    }

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        release()
        return true
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

    fun release() {
        player?.release()
        player = null
        prepared = false
        surface?.release()
        surface = null
    }
}
