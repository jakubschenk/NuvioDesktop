package com.nuvio.app.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class TmdbImageQualityTest {
    @Test
    fun metahubImageSizeIsUpgraded() {
        assertEquals(
            "https://images.metahub.space/poster/large/tt0203259/img",
            "https://images.metahub.space/poster/small/tt0203259/img".withMetahubImageSize("large"),
        )
        assertEquals(
            "https://images.metahub.space/background/large/tt0203259/img",
            "https://images.metahub.space/background/medium/tt0203259/img".withMetahubImageSize("large"),
        )
    }

    @Test
    fun metahubEpisodeImageSizeIsUpgraded() {
        assertEquals(
            "https://episodes.metahub.space/tt0203259/27/21/original.jpg",
            "https://episodes.metahub.space/tt0203259/27/21/w780.jpg".withMetahubEpisodeImageSize("original"),
        )
        assertEquals(
            "https://episodes.metahub.space/tt0203259/27/21/original.jpg?token=abc",
            "https://episodes.metahub.space/tt0203259/27/21/w1280.jpg?token=abc".withMetahubEpisodeImageSize(
                "original",
            ),
        )
    }

    @Test
    fun unrelatedUrlsAreUnchanged() {
        val url = "https://example.com/poster/small/tt0203259/img"

        assertEquals(url, url.withMetahubImageSize("large"))
        assertEquals(url, url.withMetahubEpisodeImageSize("original"))
    }
}
