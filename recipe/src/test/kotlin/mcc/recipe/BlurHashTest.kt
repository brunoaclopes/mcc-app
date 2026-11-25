package mcc.recipe

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlurHashTest {
    private fun rgb(p: Int) = Triple((p shr 16) and 255, (p shr 8) and 255, p and 255)

    @Test fun decodesWhatTheIndexBuilderEncodes() {
        // recipe-repo/tests/test_blurhash.py: a 64 x 48 orange image (255, 128, 0)
        val px = BlurHash.decode("L6TNoS^2fQ^2}Vj@fQj@fQfQfQfQ", 64, 48)!!
        val (r, g, b) = rgb(px[24 * 64 + 32])
        assertTrue(r >= 249 && g in 122..134 && b <= 6, "($r, $g, $b)")
        assertEquals(0xFF, px[0] ushr 24)
    }

    @Test fun rejectsBadInput() {
        assertNull(BlurHash.decode("", 4, 4))
        assertNull(BlurHash.decode("L6TNoS^2fQ", 4, 4))           // wrong length for 4 x 3
        assertNull(BlurHash.decode("L6TNoS^2fQ^2}Vj@fQj@fQfQfQ\"Q", 4, 4)) // not a BlurHash character
    }

    @Test fun indexEntryReadsTheField() {
        val idx = RecipeIndex.parse("""{"format":"mcc-index/1","recipes":[{"id":"a-pt","title":"A","file":"recipes/a-pt.json","blurhash":"L6TNoS^2fQ^2}Vj@fQj@fQfQfQfQ"},{"id":"b-pt","title":"B","file":"recipes/b-pt.json"}]}""")
        assertEquals("L6TNoS^2fQ^2}Vj@fQj@fQfQfQfQ", idx.recipes[0].blurhash)
        assertNull(idx.recipes[1].blurhash)
    }
}
