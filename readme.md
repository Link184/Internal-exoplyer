Because exoplayer can be incompatible even between minor versions [link] (https://github.com/google/ExoPlayer/issues/3680#issuecomment-357618805) we are forced to bundle it to our apps.
Flutter sdk also depend on ancient exo player version which will make impossible to include new versions of exoplayer on flutter app.
This repo is an work around to avoid dependency conflict.

```gradle
repositories {
    ...
    maven { url "https://oss.sonatype.org/content/repositories/snapshots/" }
}

dependencies {
    ...
    implementation "com.link184:common-mvvm:1.0.1-SNAPSHOT"
}
```