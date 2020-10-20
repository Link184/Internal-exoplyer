/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2internal;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2internal.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2internal.source.SampleStream;
import com.google.android.exoplayer2internal.util.Assertions;
import com.google.android.exoplayer2internal.util.MediaClock;

import java.io.IOException;

import static java.lang.Math.max;

/**
 * An abstract base class suitable for most {@link Renderer} implementations.
 */
public abstract class BaseRenderer implements Renderer, RendererCapabilities {

  private final int trackType;
  private final FormatHolder formatHolder;

  @Nullable private RendererConfiguration configuration;
  private int index;
  private int state;
  @Nullable private SampleStream stream;
  @Nullable private Format[] streamFormats;
  private long streamOffsetUs;
  private long lastResetPositionUs;
  private long readingPositionUs;
  private boolean streamIsFinal;
  private boolean throwRendererExceptionIsExecuting;

  /**
   * @param trackType The track type that the renderer handles. One of the {@link C}
   * {@code TRACK_TYPE_*} constants.
   */
  public BaseRenderer(int trackType) {
    this.trackType = trackType;
    formatHolder = new FormatHolder();
    readingPositionUs = C.TIME_END_OF_SOURCE;
  }

  @Override
  public final int getTrackType() {
    return trackType;
  }

  @Override
  public final RendererCapabilities getCapabilities() {
    return this;
  }

  @Override
  public final void setIndex(int index) {
    this.index = index;
  }

  @Override
  @Nullable
  public MediaClock getMediaClock() {
    return null;
  }

  @Override
  public final int getState() {
    return state;
  }

  @Override
  public final void enable(
      RendererConfiguration configuration,
      Format[] formats,
      SampleStream stream,
      long positionUs,
      boolean joining,
      boolean mayRenderStartOfStream,
      long startPositionUs,
      long offsetUs)
      throws ExoPlaybackException {
    Assertions.checkState(state == STATE_DISABLED);
    this.configuration = configuration;
    state = STATE_ENABLED;
    lastResetPositionUs = positionUs;
    onEnabled(joining, mayRenderStartOfStream);
    replaceStream(formats, stream, startPositionUs, offsetUs);
    onPositionReset(positionUs, joining);
  }

  @Override
  public final void start() throws ExoPlaybackException {
    Assertions.checkState(state == STATE_ENABLED);
    state = STATE_STARTED;
    onStarted();
  }

  @Override
  public final void replaceStream(
      Format[] formats, SampleStream stream, long startPositionUs, long offsetUs)
      throws ExoPlaybackException {
    Assertions.checkState(!streamIsFinal);
    this.stream = stream;
    readingPositionUs = offsetUs;
    streamFormats = formats;
    streamOffsetUs = offsetUs;
    onStreamChanged(formats, startPositionUs, offsetUs);
  }

  @Override
  @Nullable
  public final SampleStream getStream() {
    return stream;
  }

  @Override
  public final boolean hasReadStreamToEnd() {
    return readingPositionUs == C.TIME_END_OF_SOURCE;
  }

  @Override
  public final long getReadingPositionUs() {
    return readingPositionUs;
  }

  @Override
  public final void setCurrentStreamFinal() {
    streamIsFinal = true;
  }

  @Override
  public final boolean isCurrentStreamFinal() {
    return streamIsFinal;
  }

  @Override
  public final void maybeThrowStreamError() throws IOException {
    Assertions.checkNotNull(stream).maybeThrowError();
  }

  @Override
  public final void resetPosition(long positionUs) throws ExoPlaybackException {
    streamIsFinal = false;
    lastResetPositionUs = positionUs;
    readingPositionUs = positionUs;
    onPositionReset(positionUs, false);
  }

  @Override
  public final void stop() {
    Assertions.checkState(state == STATE_STARTED);
    state = STATE_ENABLED;
    onStopped();
  }

  @Override
  public final void disable() {
    Assertions.checkState(state == STATE_ENABLED);
    formatHolder.clear();
    state = STATE_DISABLED;
    stream = null;
    streamFormats = null;
    streamIsFinal = false;
    onDisabled();
  }

  @Override
  public final void reset() {
    Assertions.checkState(state == STATE_DISABLED);
    formatHolder.clear();
    onReset();
  }

  // RendererCapabilities implementation.

  @Override
  @AdaptiveSupport
  public int supportsMixedMimeTypeAdaptation() throws ExoPlaybackException {
    return ADAPTIVE_NOT_SUPPORTED;
  }

  // PlayerMessage.Target implementation.

  @Override
  public void handleMessage(int what, @Nullable Object object) throws ExoPlaybackException {
    // Do nothing.
  }

  // Methods to be overridden by subclasses.

  /**
   * Called when the renderer is enabled.
   *
   * <p>The default implementation is a no-op.
   *
   * @param joining Whether this renderer is being enabled to join an ongoing playback.
   * @param mayRenderStartOfStream Whether this renderer is allowed to render the start of the
   *     stream even if the state is not {@link #STATE_STARTED} yet.
   * @throws ExoPlaybackException If an error occurs.
   */
  protected void onEnabled(boolean joining, boolean mayRenderStartOfStream)
      throws ExoPlaybackException {
    // Do nothing.
  }

  /**
   * Called when the renderer's stream has changed. This occurs when the renderer is enabled after
   * {@link #onEnabled(boolean, boolean)} has been called, and also when the stream has been
   * replaced whilst the renderer is enabled or started.
   *
   * <p>The default implementation is a no-op.
   *
   * @param formats The enabled formats.
   * @param startPositionUs The start position of the new stream in renderer time (microseconds).
   * @param offsetUs The offset that will be added to the timestamps of buffers read via {@link
   *     #readSource(FormatHolder, DecoderInputBuffer, boolean)} so that decoder input buffers have
   *     monotonically increasing timestamps.
   * @throws ExoPlaybackException If an error occurs.
   */
  protected void onStreamChanged(Format[] formats, long startPositionUs, long offsetUs)
      throws ExoPlaybackException {
    // Do nothing.
  }

  /**
   * Called when the position is reset. This occurs when the renderer is enabled after {@link
   * #onStreamChanged(Format[], long, long)} has been called, and also when a position discontinuity
   * is encountered.
   *
   * <p>After a position reset, the renderer's {@link SampleStream} is guaranteed to provide samples
   * starting from a key frame.
   *
   * <p>The default implementation is a no-op.
   *
   * @param positionUs The new playback position in microseconds.
   * @param joining Whether this renderer is being enabled to join an ongoing playback.
   * @throws ExoPlaybackException If an error occurs.
   */
  protected void onPositionReset(long positionUs, boolean joining) throws ExoPlaybackException {
    // Do nothing.
  }

  /**
   * Called when the renderer is started.
   * <p>
   * The default implementation is a no-op.
   *
   * @throws ExoPlaybackException If an error occurs.
   */
  protected void onStarted() throws ExoPlaybackException {
    // Do nothing.
  }

  /**
   * Called when the renderer is stopped.
   *
   * <p>The default implementation is a no-op.
   */
  protected void onStopped() {
    // Do nothing.
  }

  /**
   * Called when the renderer is disabled.
   * <p>
   * The default implementation is a no-op.
   */
  protected void onDisabled() {
    // Do nothing.
  }

  /**
   * Called when the renderer is reset.
   *
   * <p>The default implementation is a no-op.
   */
  protected void onReset() {
    // Do nothing.
  }

  // Methods to be called by subclasses.

  /**
   * Returns the position passed to the most recent call to {@link #enable} or {@link
   * #resetPosition}.
   */
  protected final long getLastResetPositionUs() {
    return lastResetPositionUs;
  }

  /** Returns a clear {@link FormatHolder}. */
  protected final FormatHolder getFormatHolder() {
    formatHolder.clear();
    return formatHolder;
  }

  /**
   * Returns the formats of the currently enabled stream.
   *
   * <p>This method may be called when the renderer is in the following states: {@link
   * #STATE_ENABLED}, {@link #STATE_STARTED}.
   */
  protected final Format[] getStreamFormats() {
    return Assertions.checkNotNull(streamFormats);
  }

  /**
   * Returns the configuration set when the renderer was most recently enabled.
   *
   * <p>This method may be called when the renderer is in the following states: {@link
   * #STATE_ENABLED}, {@link #STATE_STARTED}.
   */
  protected final RendererConfiguration getConfiguration() {
    return Assertions.checkNotNull(configuration);
  }

  /**
   * Returns the index of the renderer within the player.
   */
  protected final int getIndex() {
    return index;
  }

  /**
   * Creates an {@link ExoPlaybackException} of type {@link ExoPlaybackException#TYPE_RENDERER} for
   * this renderer.
   *
   * @param cause The cause of the exception.
   * @param format The current format used by the renderer. May be null.
   */
  protected final ExoPlaybackException createRendererException(
      Exception cause, @Nullable Format format) {
    @FormatSupport int formatSupport = FORMAT_HANDLED;
    if (format != null && !throwRendererExceptionIsExecuting) {
      // Prevent recursive re-entry from subclass supportsFormat implementations.
      throwRendererExceptionIsExecuting = true;
      try {
        formatSupport = RendererCapabilities.getFormatSupport(supportsFormat(format));
      } catch (ExoPlaybackException e) {
        // Ignore, we are already failing.
      } finally {
        throwRendererExceptionIsExecuting = false;
      }
    }
    return ExoPlaybackException.createForRenderer(
        cause, getName(), getIndex(), format, formatSupport);
  }

  /**
   * Reads from the enabled upstream source. If the upstream source has been read to the end then
   * {@link C#RESULT_BUFFER_READ} is only returned if {@link #setCurrentStreamFinal()} has been
   * called. {@link C#RESULT_NOTHING_READ} is returned otherwise.
   *
   * <p>This method may be called when the renderer is in the following states: {@link
   * #STATE_ENABLED}, {@link #STATE_STARTED}.
   *
   * @param formatHolder A {@link FormatHolder} to populate in the case of reading a format.
   * @param buffer A {@link DecoderInputBuffer} to populate in the case of reading a sample or the
   *     end of the stream. If the end of the stream has been reached, the {@link
   *     C#BUFFER_FLAG_END_OF_STREAM} flag will be set on the buffer.
   * @param formatRequired Whether the caller requires that the format of the stream be read even if
   *     it's not changing. A sample will never be read if set to true, however it is still possible
   *     for the end of stream or nothing to be read.
   * @return The status of read, one of {@link SampleStream.ReadDataResult}.
   */
  @SampleStream.ReadDataResult
  protected final int readSource(
      FormatHolder formatHolder, DecoderInputBuffer buffer, boolean formatRequired) {
    @SampleStream.ReadDataResult
    int result = Assertions.checkNotNull(stream).readData(formatHolder, buffer, formatRequired);
    if (result == C.RESULT_BUFFER_READ) {
      if (buffer.isEndOfStream()) {
        readingPositionUs = C.TIME_END_OF_SOURCE;
        return streamIsFinal ? C.RESULT_BUFFER_READ : C.RESULT_NOTHING_READ;
      }
      buffer.timeUs += streamOffsetUs;
      readingPositionUs = max(readingPositionUs, buffer.timeUs);
    } else if (result == C.RESULT_FORMAT_READ) {
      Format format = Assertions.checkNotNull(formatHolder.format);
      if (format.subsampleOffsetUs != Format.OFFSET_SAMPLE_RELATIVE) {
        format =
            format
                .buildUpon()
                .setSubsampleOffsetUs(format.subsampleOffsetUs + streamOffsetUs)
                .build();
        formatHolder.format = format;
      }
    }
    return result;
  }

  /**
   * Attempts to skip to the keyframe before the specified position, or to the end of the stream if
   * {@code positionUs} is beyond it.
   *
   * <p>This method may be called when the renderer is in the following states: {@link
   * #STATE_ENABLED}, {@link #STATE_STARTED}.
   *
   * @param positionUs The position in microseconds.
   * @return The number of samples that were skipped.
   */
  protected int skipSource(long positionUs) {
    return Assertions.checkNotNull(stream).skipData(positionUs - streamOffsetUs);
  }

  /**
   * Returns whether the upstream source is ready.
   *
   * <p>This method may be called when the renderer is in the following states: {@link
   * #STATE_ENABLED}, {@link #STATE_STARTED}.
   */
  protected final boolean isSourceReady() {
    return hasReadStreamToEnd() ? streamIsFinal : Assertions.checkNotNull(stream).isReady();
  }
}
