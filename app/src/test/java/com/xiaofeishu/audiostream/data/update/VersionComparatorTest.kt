package com.xiaofeishu.audiostream.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparatorTest {
    @Test
    fun mirrorUrlKeepsOriginalGithubUrl() {
        val githubUrl = "https://github.com/example/app/releases/download/v1.2.0/app.apk"

        assertEquals(
            "https://ghproxy.net/$githubUrl",
            buildGithubMirrorUrl(githubUrl)
        )
    }

    @Test
    fun newerMinorVersionIsGreater() {
        assertTrue(VersionComparator.compare("1.1", "1.0") > 0)
    }

    @Test
    fun missingSegmentsAreTreatedAsZero() {
        assertEquals(0, VersionComparator.compare("v1.0.0", "1.0"))
    }

    @Test
    fun stableVersionIsNewerThanPreRelease() {
        assertTrue(VersionComparator.compare("2.0", "2.0-beta.2") > 0)
    }

    @Test
    fun numericPreReleaseIdentifiersUseNumericOrdering() {
        assertTrue(VersionComparator.compare("2.0-beta.10", "2.0-beta.2") > 0)
    }
}
