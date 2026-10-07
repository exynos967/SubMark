package io.github.submark.core.ui.icon

import com.google.common.truth.Truth.assertThat
import io.github.submark.core.model.IconType
import org.junit.Test

class IconCatalogTest {
    @Test fun `catalogue is large with unique lowercase names`() {
        val names = IconCatalog.all.map { it.name }
        assertThat(names.size).isAtLeast(120)
        assertThat(names.toSet()).hasSize(names.size)
        names.forEach { assertThat(it).matches("[a-z0-9_]+") }
        IconGroup.entries.forEach { assertThat(IconCatalog.group(it)).isNotEmpty() }
    }

    @Test fun `vectors build`() {
        IconCatalog.all.forEach { assertThat(it.vector.name).isNotEmpty() }
    }

    @Test fun `lookup and search`() {
        assertThat(IconCatalog[" Movie "]?.name).isEqualTo("movie")
        assertThat(IconCatalog["nope"]).isNull()
        assertThat(IconCatalog.search("music").map { it.name }).contains("music")
        assertThat(IconCatalog.search("音乐").map { it.name }).contains("music")
        assertThat(IconCatalog.search("")).hasSize(IconCatalog.all.size)
    }

    @Test fun `icon choice round trip`() {
        val c = IconChoice(IconType.URL, "https://x.test/a|b.png")
        assertThat(IconChoice.decode(c.encode())).isEqualTo(c)
        assertThat(IconChoice.decode("BOGUS|x")).isNull()
        assertThat(IconChoice.decode("SYMBOL|")).isNull()
        assertThat(IconChoice.decode(null)).isNull()
    }

    @Test fun `monogram`() {
        assertThat(monogramOf(" netflix")).isEqualTo("N")
        assertThat(monogramOf("")).isEqualTo("?")
        assertThat(monogramOf("😀abc")).isEqualTo("😀")
        assertThat(monogramOf("爱奇艺")).isEqualTo("爱")
    }
}
