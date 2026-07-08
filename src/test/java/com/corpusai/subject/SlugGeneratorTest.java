package com.corpusai.subject;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlugGeneratorTest {

    @Test
    void slugifiesPlainText() {
        assertThat(SlugGenerator.slugify("Softverski Paterni"))
                .isEqualTo("softverski-paterni");
    }

    @Test
    void stripsSerbianLatinDiacritics() {
        assertThat(SlugGenerator.slugify("Đorđe Ćirić Žabić Šumadija Čačak"))
                .isEqualTo("dorde-ciric-zabic-sumadija-cacak");
    }

    @Test
    void collapsesPunctuationAndMultipleSpacesIntoASingleDash() {
        assertThat(SlugGenerator.slugify("  Multiple   Spaces & Punctuation!!  "))
                .isEqualTo("multiple-spaces-punctuation");
    }

    @Test
    void throwsOnBlankInput() {
        assertThatThrownBy(() -> SlugGenerator.slugify("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsOnNullInput() {
        assertThatThrownBy(() -> SlugGenerator.slugify(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenInputHasNoSlugCharacters() {
        assertThatThrownBy(() -> SlugGenerator.slugify("!!!"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
