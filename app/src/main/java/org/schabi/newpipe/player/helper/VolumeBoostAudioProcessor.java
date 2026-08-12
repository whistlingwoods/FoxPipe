package org.schabi.newpipe.player.helper;

import androidx.core.math.MathUtils;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.audio.BaseAudioProcessor;

import java.nio.ByteBuffer;

/**
 * Amplifies decoded PCM before the audio track. Each frame's channels share the same gain.
 * The processor stays active at unity so the listener can change gain during playback.
 */
public final class VolumeBoostAudioProcessor extends BaseAudioProcessor {

    public static final float MINIMUM_VOLUME_BOOST = 1.0f;
    public static final float MAXIMUM_VOLUME_BOOST = 3.0f;

    // RMS of a normalized signal at roughly -16 dBFS.
    private static final float TARGET_LOUDNESS = 0.15f;
    private static final float SILENCE_THRESHOLD = 0.001f;
    private static final float PEAK_CEILING = 0.98f;

    // Settings are written by the player and sampled once per buffer on the audio thread.
    private volatile float volumeBoost = MINIMUM_VOLUME_BOOST;
    private volatile boolean automaticVolumeBoost;

    private double automaticGain;
    private double manualGainLimit;
    private double attackCoefficient;
    private double releaseCoefficient;
    private double limiterReleaseCoefficient;

    public float getVolumeBoost() {
        return volumeBoost;
    }

    public void setVolumeBoost(final float newVolumeBoost) {
        volumeBoost = clampGain(newVolumeBoost);
    }

    static float clampGain(final float newVolumeBoost) {
        return Float.isNaN(newVolumeBoost) ? MINIMUM_VOLUME_BOOST
                : MathUtils.clamp(newVolumeBoost, MINIMUM_VOLUME_BOOST, MAXIMUM_VOLUME_BOOST);
    }

    public void setAutomaticVolumeBoost(final boolean newAutomaticVolumeBoost) {
        automaticVolumeBoost = newAutomaticVolumeBoost;
    }

    @Override
    protected AudioFormat onConfigure(final AudioFormat format)
            throws UnhandledAudioFormatException {
        if (format.encoding != C.ENCODING_PCM_16BIT && format.encoding != C.ENCODING_PCM_FLOAT) {
            throw new UnhandledAudioFormatException(format);
        }
        return format;
    }

    @Override
    protected void onFlush() {
        automaticGain = MINIMUM_VOLUME_BOOST;
        manualGainLimit = MAXIMUM_VOLUME_BOOST;
        if (inputAudioFormat != AudioFormat.NOT_SET) {
            final int sampleRate = inputAudioFormat.sampleRate;
            attackCoefficient = smoothingCoefficient(0.05, sampleRate);
            releaseCoefficient = smoothingCoefficient(2.0, sampleRate);
            limiterReleaseCoefficient = smoothingCoefficient(0.15, sampleRate);
        }
    }

    private static double smoothingCoefficient(final double seconds, final int sampleRate) {
        return 1.0 - Math.exp(-1.0 / (seconds * sampleRate));
    }

    @Override
    public void queueInput(final ByteBuffer inputBuffer) {
        if (!inputBuffer.hasRemaining()) {
            return;
        }

        final ByteBuffer output = replaceOutputBuffer(inputBuffer.remaining());
        final boolean automatic = automaticVolumeBoost;
        final float requestedGain = volumeBoost;
        if (!automatic && requestedGain == MINIMUM_VOLUME_BOOST) {
            output.put(inputBuffer);
            automaticGain = MINIMUM_VOLUME_BOOST;
            manualGainLimit = MAXIMUM_VOLUME_BOOST;
        } else {
            amplify(inputBuffer, output, automatic, requestedGain);
        }
        output.flip();
    }

    private void amplify(final ByteBuffer input, final ByteBuffer output,
                         final boolean automatic, final float requestedGain) {
        final boolean pcm16 = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT;
        final int sampleSize = pcm16 ? Short.BYTES : Float.BYTES;
        final int sampleCount = input.remaining() / sampleSize;
        double sumOfSquares = 0.0;
        float peak = 0.0f;
        for (int i = input.position(); i < input.limit(); i += sampleSize) {
            final float sample = pcm16 ? input.getShort(i) / 32768.0f : input.getFloat(i);
            peak = Math.max(peak, Math.abs(sample));
            if (automatic) {
                sumOfSquares += (double) sample * sample;
            }
        }

        // Look ahead within this buffer. Limit gain before a large peak reaches the output:
        // waiting for the smoothed gain to catch up here would flatten bass and transients.
        final float peakLimit = peak <= PEAK_CEILING / MAXIMUM_VOLUME_BOOST
                ? MAXIMUM_VOLUME_BOOST : Math.max(MINIMUM_VOLUME_BOOST, PEAK_CEILING / peak);
        double envelope = Math.min(automatic ? automaticGain : manualGainLimit, peakLimit);
        final double target;
        final double coefficient;
        if (automatic) {
            final double rms = Math.sqrt(sumOfSquares / sampleCount);
            target = rms < SILENCE_THRESHOLD ? envelope
                    : MathUtils.clamp(TARGET_LOUDNESS / rms, MINIMUM_VOLUME_BOOST, peakLimit);
            coefficient = target < envelope ? attackCoefficient : releaseCoefficient;
        } else {
            target = peakLimit;
            coefficient = limiterReleaseCoefficient;
        }

        while (input.hasRemaining()) {
            envelope += (target - envelope) * coefficient;
            final float gain = automatic ? (float) envelope
                    : Math.min(requestedGain, (float) envelope);
            for (int ch = 0; ch < inputAudioFormat.channelCount && input.hasRemaining(); ch++) {
                if (pcm16) {
                    output.putShort((short) Math.round(input.getShort() * gain));
                } else {
                    output.putFloat(MathUtils.clamp(input.getFloat() * gain, -1.0f, 1.0f));
                }
            }
        }
        automaticGain = automatic ? envelope : MINIMUM_VOLUME_BOOST;
        manualGainLimit = automatic ? MAXIMUM_VOLUME_BOOST : envelope;
    }
}
