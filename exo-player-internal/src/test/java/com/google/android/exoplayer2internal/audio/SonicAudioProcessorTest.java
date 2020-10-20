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

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.exoplayer2internal.C;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

/** Unit test for {@link SonicAudioProcessor}. */
@RunWith(AndroidJUnit4.class)
public final class SonicAudioProcessorTest {

  private static final AudioProcessor.AudioFormat AUDIO_FORMAT_22050_HZ =
      new AudioProcessor.AudioFormat(
          /* sampleRate= */ 22050, /* channelCount= */ 2, /* encoding= */ C.ENCODING_PCM_16BIT);
  private static final AudioProcessor.AudioFormat AUDIO_FORMAT_44100_HZ =
      new AudioProcessor.AudioFormat(
          /* sampleRate= */ 44100, /* channelCount= */ 2, /* encoding= */ C.ENCODING_PCM_16BIT);
  private static final AudioProcessor.AudioFormat AUDIO_FORMAT_48000_HZ =
      new AudioProcessor.AudioFormat(
          /* sampleRate= */ 48000, /* channelCount= */ 2, /* encoding= */ C.ENCODING_PCM_16BIT);

  private SonicAudioProcessor sonicAudioProcessor;

  @Before
  public void setUp() {
    sonicAudioProcessor = new SonicAudioProcessor();
  }

  @Test
  public void reconfigureWithSameSampleRate() throws Exception {
    // When configured for resampling from 44.1 kHz to 48 kHz, the output sample rate is correct.
    sonicAudioProcessor.setOutputSampleRateHz(48000);
    AudioProcessor.AudioFormat outputAudioFormat = sonicAudioProcessor.configure(AUDIO_FORMAT_44100_HZ);
    assertThat(sonicAudioProcessor.isActive()).isTrue();
    assertThat(outputAudioFormat.sampleRate).isEqualTo(48000);
    // When reconfigured with 48 kHz input, there is no resampling.
    outputAudioFormat = sonicAudioProcessor.configure(AUDIO_FORMAT_48000_HZ);
    assertThat(sonicAudioProcessor.isActive()).isFalse();
    assertThat(outputAudioFormat.sampleRate).isEqualTo(48000);
    // When reconfigure with 44.1 kHz input, resampling is enabled again.
    outputAudioFormat = sonicAudioProcessor.configure(AUDIO_FORMAT_44100_HZ);
    assertThat(sonicAudioProcessor.isActive()).isTrue();
    assertThat(outputAudioFormat.sampleRate).isEqualTo(48000);
  }

  @Test
  public void noSampleRateChange() throws Exception {
    // Configure for resampling 44.1 kHz to 48 kHz.
    sonicAudioProcessor.setOutputSampleRateHz(48000);
    sonicAudioProcessor.configure(AUDIO_FORMAT_44100_HZ);
    assertThat(sonicAudioProcessor.isActive()).isTrue();
    // Reconfigure to not modify the sample rate.
    sonicAudioProcessor.setOutputSampleRateHz(SonicAudioProcessor.SAMPLE_RATE_NO_CHANGE);
    sonicAudioProcessor.configure(AUDIO_FORMAT_22050_HZ);
    // The sample rate is unmodified, and the audio processor is not active.
    assertThat(sonicAudioProcessor.isActive()).isFalse();
  }

  @Test
  public void isActiveWithSpeedChange() throws Exception {
    sonicAudioProcessor.setSpeed(1.5f);
    sonicAudioProcessor.configure(AUDIO_FORMAT_44100_HZ);
    sonicAudioProcessor.flush();
    assertThat(sonicAudioProcessor.isActive()).isTrue();
  }

  @Test
  public void isNotActiveWithNoChange() throws Exception {
    sonicAudioProcessor.configure(AUDIO_FORMAT_44100_HZ);
    assertThat(sonicAudioProcessor.isActive()).isFalse();
  }

  @Test
  public void doesNotSupportNon16BitInput() throws Exception {
    try {
      sonicAudioProcessor.configure(
          new AudioProcessor.AudioFormat(
              /* sampleRate= */ 44100, /* channelCount= */ 2, /* encoding= */ C.ENCODING_PCM_8BIT));
      fail();
    } catch (AudioProcessor.UnhandledAudioFormatException e) {
      // Expected.
    }
    try {
      sonicAudioProcessor.configure(
          new AudioProcessor.AudioFormat(
              /* sampleRate= */ 44100,
              /* channelCount= */ 2,
              /* encoding= */ C.ENCODING_PCM_24BIT));
      fail();
    } catch (AudioProcessor.UnhandledAudioFormatException e) {
      // Expected.
    }
    try {
      sonicAudioProcessor.configure(
          new AudioProcessor.AudioFormat(
              /* sampleRate= */ 44100,
              /* channelCount= */ 2,
              /* encoding= */ C.ENCODING_PCM_32BIT));
      fail();
    } catch (AudioProcessor.UnhandledAudioFormatException e) {
      // Expected.
    }
  }

}
