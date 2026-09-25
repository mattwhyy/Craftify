package tech.thatgravyboat.craftify.services

import org.endlesssource.mediainterface.api.MediaSession
import org.endlesssource.mediainterface.api.PlaybackState
import org.endlesssource.mediainterface.api.SystemMediaOptions
import org.endlesssource.mediainterface.windows.WindowsSystemMediaInterface
import tech.thatgravyboat.jukebox.api.service.BaseService
import tech.thatgravyboat.jukebox.api.service.ServiceFunction
import tech.thatgravyboat.jukebox.api.service.ServicePhase
import tech.thatgravyboat.jukebox.api.state.PlayerState
import tech.thatgravyboat.jukebox.api.state.PlayingType
import tech.thatgravyboat.jukebox.api.state.RepeatState
import tech.thatgravyboat.jukebox.api.state.ShuffleState
import tech.thatgravyboat.jukebox.api.state.Song
import tech.thatgravyboat.jukebox.api.state.SongState
import tech.thatgravyboat.jukebox.api.state.State
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Craftify service for the official SoundCloud Windows app.
 *
 * SoundCloud already integrates with Windows System Media Transport Controls (SMTC), so this
 * adapter talks to Windows rather than SoundCloud's web API. That means no SoundCloud token is
 * required, and play/pause/next/previous are sent back to the desktop app through Windows.
 */
class SoundCloudWindowsService : BaseService() {

    @Volatile
    private var phase = ServicePhase.STOPPED

    @Volatile
    private var media: WindowsSystemMediaInterface? = null

    @Volatile
    private var poller: ScheduledExecutorService? = null

    override fun start() {
        if (phase != ServicePhase.STOPPED) return
        phase = ServicePhase.STARTING

        try {
            val interfaceOptions = SystemMediaOptions.defaults()
                .withEventDrivenEnabled(true)
                .withPositionUpdatesEnabled(true)

            media = WindowsSystemMediaInterface(interfaceOptions)
            poller = Executors.newSingleThreadScheduledExecutor { task ->
                Thread(task, "craftify-soundcloud").apply { isDaemon = true }
            }.also {
                it.scheduleWithFixedDelay(::refresh, 0L, 250L, TimeUnit.MILLISECONDS)
            }
            phase = ServicePhase.RUNNING
        } catch (throwable: Throwable) {
            phase = ServicePhase.STOPPED
            media?.close()
            media = null
            onError(throwable.message ?: "Could not initialize Windows media controls")
        }
    }

    override fun stop(): Boolean {
        val wasRunning = phase != ServicePhase.STOPPED
        phase = ServicePhase.STOPPED

        poller?.shutdownNow()
        poller = null

        media?.close()
        media = null
        return wasRunning
    }

    override fun restart() {
        stop()
        start()
    }

    override fun getPhase(): ServicePhase = phase

    override fun setPaused(paused: Boolean): Boolean {
        val controls = findSoundCloudSession()?.controls ?: return false
        return if (paused) controls.pause() else controls.play()
    }

    override fun toggleShuffle(): Boolean = false

    override fun toggleRepeat(): Boolean = false

    override fun setVolume(volume: Int, notify: Boolean): Boolean = false

    override fun move(forward: Boolean): Boolean {
        val controls = findSoundCloudSession()?.controls ?: return false
        return if (forward) controls.next() else controls.previous()
    }

    override fun getFunctions(): Set<ServiceFunction> = setOf(ServiceFunction.MOVE)

    private fun refresh() {
        if (phase == ServicePhase.STOPPED) return

        try {
            val session = findSoundCloudSession() ?: return
            val nowPlaying = session.nowPlaying.orElse(null) ?: return
            val title = nowPlaying.title.orElse("Unknown title")
            val artist = nowPlaying.artist.orElse("").trim()
            val artwork = nowPlaying.artwork.orElse("")
            val duration = nowPlaying.duration.map { it.toSeconds().coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }.orElse(0)
            val progress = nowPlaying.position.map { it.toSeconds().coerceIn(0L, Int.MAX_VALUE.toLong()).toInt() }.orElse(0)
            val isPlaying = session.controls.playbackState == PlaybackState.PLAYING

            onSuccess(
                State(
                    PlayerState(ShuffleState.DISABLED, RepeatState.DISABLED, 100),
                    Song(
                        title,
                        if (artist.isBlank()) emptyList() else listOf(artist),
                        artwork,
                        "",
                        PlayingType.TRACK,
                    ),
                    SongState(progress, duration, isPlaying),
                )
            )
        } catch (throwable: Throwable) {
            onError(throwable.message ?: "Could not read SoundCloud media session")
        }
    }

    private fun findSoundCloudSession(): MediaSession? {
        val windowsMedia = media ?: return null
        return windowsMedia.allSessions.firstOrNull(::isSoundCloudSession)
    }

    private fun isSoundCloudSession(session: MediaSession): Boolean {
        val app = session.applicationName.lowercase(Locale.ROOT)
        val id = session.sessionId.lowercase(Locale.ROOT)
        return "soundcloud" in app || "soundcloud" in id
    }
}
