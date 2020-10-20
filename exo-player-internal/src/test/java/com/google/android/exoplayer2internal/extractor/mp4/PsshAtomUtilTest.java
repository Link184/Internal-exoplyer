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
package com.google.android.exoplayer2internal.extractor.mp4;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.exoplayer2internal.C;
import com.google.android.exoplayer2internal.util.ParsableByteArray;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;

import static com.google.android.exoplayer2internal.C.WIDEVINE_UUID;
import static com.google.common.truth.Truth.assertThat;

/** Tests for {@link PsshAtomUtil}. */
@RunWith(AndroidJUnit4.class)
public final class PsshAtomUtilTest {

  @Test
  public void buildPsshAtom() {
    byte[] schemeData = new byte[]{0, 1, 2, 3, 4, 5};
    byte[] psshAtom = PsshAtomUtil.buildPsshAtom(C.WIDEVINE_UUID, schemeData);
    // Read the PSSH atom back and assert its content is as expected.
    ParsableByteArray parsablePsshAtom = new ParsableByteArray(psshAtom);
    assertThat(parsablePsshAtom.readUnsignedIntToInt()).isEqualTo(psshAtom.length); // length
    assertThat(parsablePsshAtom.readInt()).isEqualTo(Atom.TYPE_pssh); // type
    int fullAtomInt = parsablePsshAtom.readInt(); // version + flags
    assertThat(Atom.parseFullAtomVersion(fullAtomInt)).isEqualTo(0);
    assertThat(Atom.parseFullAtomFlags(fullAtomInt)).isEqualTo(0);
    UUID systemId = new UUID(parsablePsshAtom.readLong(), parsablePsshAtom.readLong());
    assertThat(systemId).isEqualTo(WIDEVINE_UUID);
    assertThat(parsablePsshAtom.readUnsignedIntToInt()).isEqualTo(schemeData.length);
    byte[] psshSchemeData = new byte[schemeData.length];
    parsablePsshAtom.readBytes(psshSchemeData, 0, schemeData.length);
    assertThat(psshSchemeData).isEqualTo(schemeData);
  }

}
