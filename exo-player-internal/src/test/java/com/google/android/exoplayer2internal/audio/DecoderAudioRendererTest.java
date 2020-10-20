/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2internal.audio;

import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.exoplayer2.testutil.FakeSampleStream;
import com.google.android.exoplayer2internal.C;
import com.google.android.exoplayer2internal.Format;
import com.google.android.exoplayer2internal.RendererCapabilities;
import com.google.android.exoplayer2internal.RendererConfiguration;
import com.google.android.exoplayer2internal.decoder.DecoderException;
import com.google.android.exoplayer2internal.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2internal.decoder.SimpleDecoder;
import com.google.android.exoplayer2internal.decoder.SimpleOutputBuffer;
import com.google.android.exoplayer2internal.drm.DrmSessionEventListener;
import com.google.android.exoplayer2internal.drm.DrmSessionManager;
import com.google.android.exoplayer2internal.drm.ExoMediaCrypto;
import com.google.android.exoplayer2internal.util.MimeTypes;
import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.annotation.Config;

import static com.google.android.exoplayer2.testutil.FakeSampleStream.FakeSampleStreamItem.END_OF_STREAM_ITEM;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit test for {@link DecoderAudioRenderer}. */
@RunWith(AndroidJUnit4.class)
public class DecoderAudioRendererTest {

  private static final Format FORMAT =
      new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_RAW).build();

  @Mock private AudioSink mockAudioSink;
  private DecoderAudioRenderer<FakeDecoder> audioRenderer;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    audioRenderer =
        new DecoderAudioRenderer<FakeDecoder>(null, null, mockAudioSink) {
          @Override
          public String getName() {
            return "TestAudioRenderer";
          }

          @Override
          @RendererCapabilities.FormatSupport
          protected int supportsFormatInternal(Format format) {
            return RendererCapabilities.FORMAT_HANDLED;
          }

          @Override
          protected FakeDecoder createDecoder(Format format, @Nullable ExoMediaCrypto mediaCrypto) {
            return new FakeDecoder();
          }

          @Override
          protected Format getOutputFormat(FakeDecoder decoder) {
            return FORMAT;
          }
        };
  }

  @Config(sdk = 19)
  @Test
  public void supportsFormatAtApi19() {
    assertThat(audioRenderer.supportsFormat(FORMAT))
        .isEqualTo(RendererCapabilities.ADAPTIVE_NOT_SEAMLESS | RendererCapabilities.TUNNELING_NOT_SUPPORTED | RendererCapabilities.FORMAT_HANDLED);
  }

  @Config(sdk = 21)
  @Test
  public void supportsFormatAtApi21() {
    // From API 21, tunneling is supported.
    assertThat(audioRenderer.supportsFormat(FORMAT))
        .isEqualTo(RendererCapabilities.ADAPTIVE_NOT_SEAMLESS | RendererCapabilities.TUNNELING_SUPPORTED | RendererCapabilities.FORMAT_HANDLED);
  }

  @Test
  public void immediatelyReadEndOfStreamPlaysAudioSinkToEndOfStream() throws Exception {
    audioRenderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {FORMAT},
        new FakeSampleStream(
            /* mediaSourceEventDispatcher= */ null,
            DrmSessionManager.DUMMY,
            new DrmSessionEventListener.EventDispatcher(),
            FORMAT,
            ImmutableList.of(END_OF_STREAM_ITEM)),
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ true,
        /* startPositionUs= */ 0,
        /* offsetUs= */ 0);
    audioRenderer.setCurrentStreamFinal();
    when(mockAudioSink.isEnded()).thenReturn(true);
    while (!audioRenderer.isEnded()) {
      audioRenderer.render(0, 0);
    }
    verify(mockAudioSink, times(1)).playToEndOfStream();
    audioRenderer.disable();
    audioRenderer.reset();
    verify(mockAudioSink, times(1)).reset();
  }

  private static final class FakeDecoder
      extends SimpleDecoder<DecoderInputBuffer, SimpleOutputBuffer, DecoderException> {

    public FakeDecoder() {
      super(new DecoderInputBuffer[1], new SimpleOutputBuffer[1]);
    }

    @Override
    public String getName() {
      return "FakeDecoder";
    }

    @Override
    protected DecoderInputBuffer createInputBuffer() {
      return new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT);
    }

    @Override
    protected SimpleOutputBuffer createOutputBuffer() {
      return new SimpleOutputBuffer(this::releaseOutputBuffer);
    }

    @Override
    protected DecoderException createUnexpectedDecodeException(Throwable error) {
      return new DecoderException("Unexpected decode error", error);
    }

    @Override
    protected DecoderException decode(
        DecoderInputBuffer inputBuffer, SimpleOutputBuffer outputBuffer, boolean reset) {
      if (inputBuffer.isEndOfStream()) {
        outputBuffer.setFlags(C.BUFFER_FLAG_END_OF_STREAM);
      }
      return null;
    }

  }

}
