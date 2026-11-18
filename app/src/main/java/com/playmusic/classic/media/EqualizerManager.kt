package com.playmusic.classic.media

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EqualizerBand(
    val index: Short,
    val centerFreqHz: Int,
    val currentLevel: Short,
    val minLevel: Short,
    val maxLevel: Short
)

data class EqualizerState(
    val enabled: Boolean = false,
    val bands: List<EqualizerBand> = emptyList(),
    val presets: List<String> = listOf("Normal", "Klasik", "Dans", "Pop", "Rock"),
    val selectedPreset: String = "Normal",
    val bassStrength: Int = 0
)

object EqualizerManager {
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null

    private val _state = MutableStateFlow(EqualizerState())
    val state: StateFlow<EqualizerState> = _state.asStateFlow()

    fun attachToSession(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        runCatching {
            release()
            equalizer = Equalizer(0, audioSessionId).apply { enabled = _state.value.enabled }
            bassBoost = BassBoost(0, audioSessionId).apply { enabled = _state.value.enabled }
            refreshBandsAndPresets()
        }
    }

    private fun refreshBandsAndPresets() {
        val eq = equalizer ?: return
        val range = eq.bandLevelRange
        val min = range[0]
        val max = range[1]
        val bandList = (0 until eq.numberOfBands).map { i ->
            val bandIdx = i.toShort()
            EqualizerBand(
                index = bandIdx,
                centerFreqHz = eq.getCenterFreq(bandIdx) / 1000,
                currentLevel = eq.getBandLevel(bandIdx),
                minLevel = min,
                maxLevel = max
            )
        }
        val presetNames = (0 until eq.numberOfPresets).map { i ->
            eq.getPresetName(i.toShort())
        }.ifEmpty { listOf("Normal", "Klasik", "Dans", "Pop", "Rock") }

        _state.value = _state.value.copy(
            bands = bandList,
            presets = presetNames
        )
    }

    fun setEnabled(enabled: Boolean) {
        runCatching {
            equalizer?.enabled = enabled
            bassBoost?.enabled = enabled
            _state.value = _state.value.copy(enabled = enabled)
        }
    }

    fun setBandLevel(bandIndex: Short, level: Short) {
        runCatching {
            equalizer?.setBandLevel(bandIndex, level)
            refreshBandsAndPresets()
        }
    }

    fun selectPreset(presetName: String) {
        runCatching {
            val eq = equalizer ?: return
            for (i in 0 until eq.numberOfPresets) {
                if (eq.getPresetName(i.toShort()).equals(presetName, ignoreCase = true)) {
                    eq.usePreset(i.toShort())
                    break
                }
            }
            refreshBandsAndPresets()
            _state.value = _state.value.copy(selectedPreset = presetName)
        }
    }

    fun setBassStrength(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        runCatching {
            bassBoost?.setStrength(clamped.toShort())
            _state.value = _state.value.copy(bassStrength = clamped)
        }
    }

    fun release() {
        runCatching {
            equalizer?.release()
            bassBoost?.release()
        }
        equalizer = null
        bassBoost = null
    }
}
