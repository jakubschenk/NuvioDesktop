package com.nuvio.app.features.details

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MetaDetailsParserTest {

    @Test
    fun `parse rejects null meta object without json object cast crash`() {
        assertFailsWith<IllegalStateException> {
            MetaDetailsParser.parse("""{"meta":null}""")
        }
    }

    @Test
    fun `parse accepts bare meta object response`() {
        val result = MetaDetailsParser.parse(
            """
            {
              "id": "mal:62516",
              "type": "series",
              "name": "The Fragrant Flower Blooms with Dignity"
            }
            """.trimIndent(),
        )

        assertEquals("mal:62516", result.id)
        assertEquals("series", result.type)
        assertEquals("The Fragrant Flower Blooms with Dignity", result.name)
    }

    @Test
    fun `parse accepts app extras cast profile fields`() {
        val result = MetaDetailsParser.parse(
            """
            {
              "meta": {
                "id": "tt123",
                "type": "movie",
                "name": "Example",
                "app_extras": {
                  "cast": [
                    {
                      "id": "42",
                      "name": "Example Actor",
                      "character": "Example Role",
                      "profile_path": "/actor.jpg"
                    }
                  ]
                }
              }
            }
            """.trimIndent(),
        )

        assertEquals("Example Actor", result.cast.single().name)
        assertEquals("Example Role", result.cast.single().role)
        assertEquals("/actor.jpg", result.cast.single().photo)
        assertEquals(42, result.cast.single().tmdbId)
    }
}
