package com.marcpg.pillarperil;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocaleParsingTest {

    @Test
    void parseLocaleSupportsUnderscoreAndHyphen() throws Exception {
        Method m = PillarPeril.class.getDeclaredMethod("parseLocale", String.class);
        m.setAccessible(true);

        Locale itUnderscore = (Locale) m.invoke(null, "it_IT");
        Locale itHyphen = (Locale) m.invoke(null, "it-IT");
        Locale enOnly = (Locale) m.invoke(null, "en");

        assertEquals(Locale.of("it", "IT"), itUnderscore);
        assertEquals(Locale.of("it", "IT"), itHyphen);
        assertEquals(Locale.of("en"), enOnly);
    }
}

