package com.schwab.audit.util;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConstantsTest {

    @Test
    void exposesExpectedCoreConstants() {
        assertEquals("SHA-256", Constants.HASH_ALGORITHM);
        assertEquals("GENESIS_HASH", Constants.GENESIS_HASH);
        assertEquals(64, Constants.HASH_HEX_LENGTH);
    }

    @Test
    void cannotBeInstantiated() throws Exception {
        Constructor<Constants> constructor = Constants.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        InvocationTargetException exception = assertThrows(InvocationTargetException.class, constructor::newInstance);

        assertEquals(UnsupportedOperationException.class, exception.getCause().getClass());
    }
}
