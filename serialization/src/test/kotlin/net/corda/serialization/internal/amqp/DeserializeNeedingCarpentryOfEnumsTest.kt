/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.serialization.internal.amqp

import net.corda.serialization.internal.AllWhitelist
import net.corda.serialization.internal.amqp.testutils.*
import net.corda.serialization.internal.carpenter.*
import org.junit.Test
import kotlin.test.*

class DeserializeNeedingCarpentryOfEnumsTest : AmqpCarpenterBase(AllWhitelist) {
    companion object {
        /**
         * If you want to see the schema encoded into the envelope after serialisation change this to true
         */
        private const val VERBOSE = false
    }

    @Test
    fun singleEnum() {
        //
        // Setup the test
        //
        val setupFactory = testDefaultFactoryNoEvolution()

        val enumConstants = listOf("AAA", "BBB", "CCC", "DDD", "EEE", "FFF",
                "GGG", "HHH", "III", "JJJ").associateBy({ it }, { EnumField() })

        // create the enum
        val testEnumType = setupFactory.classCarpenter.build(EnumSchema("test.testEnumType", enumConstants))

        // create the class that has that enum as an element
        val testClassType = setupFactory.classCarpenter.build(ClassSchema("test.testClassType",
                mapOf("a" to NonNullableField(testEnumType))))

        // create an instance of the class we can then serialise
        val testInstance = testClassType.constructors[0].newInstance(testEnumType.getMethod(
                "valueOf", String::class.java).invoke(null, "BBB"))

        // serialise the object
        val serialisedBytes = TestSerializationOutput(VERBOSE, setupFactory).serialize(testInstance)

        //
        // Test setup done, now on with the actual test
        //

        // need a second factory to ensure a second carpenter is used and thus the class we're attempting
        // to de-serialise isn't in the factories class loader
        val testFactory = testDefaultFactoryWithWhitelist()

        val deserializedObj = DeserializationInput(testFactory).deserialize(serialisedBytes)

        assertTrue(deserializedObj::class.java.getMethod("getA").invoke(deserializedObj)::class.java.isEnum)
        assertEquals("BBB",
                (deserializedObj::class.java.getMethod("getA").invoke(deserializedObj) as Enum<*>).name)
    }

    @Test
    fun compositeIncludingEnums() {
        //
        // Setup the test
        //
        val setupFactory = testDefaultFactoryNoEvolution()

        val enumConstants = listOf("AAA", "BBB", "CCC", "DDD", "EEE", "FFF",
                "GGG", "HHH", "III", "JJJ").associateBy({ it }, { EnumField() })

        // create the enum
        val testEnumType1 = setupFactory.classCarpenter.build(EnumSchema("test.testEnumType1", enumConstants))
        val testEnumType2 = setupFactory.classCarpenter.build(EnumSchema("test.testEnumType2", enumConstants))

        // create the class that has that enum as an element
        val testClassType = setupFactory.classCarpenter.build(ClassSchema("test.testClassType",
                mapOf(
                        "a" to NonNullableField(testEnumType1),
                        "b" to NonNullableField(testEnumType2),
                        "c" to NullableField(testEnumType1),
                        "d" to NullableField(String::class.java))))

        val vOf1 = testEnumType1.getMethod("valueOf", String::class.java)
        val vOf2 = testEnumType2.getMethod("valueOf", String::class.java)
        val testStr = "so many things [Ø Þ]"

        // create an instance of the class we can then serialise
        val testInstance = testClassType.constructors[0].newInstance(
                vOf1.invoke(null, "CCC"),
                vOf2.invoke(null, "EEE"),
                null,
                testStr)

        // serialise the object
        val serialisedBytes = TestSerializationOutput(VERBOSE, setupFactory).serialize(testInstance)

        //
        // Test setup done, now on with the actual test
        //

        // need a second factory to ensure a second carpenter is used and thus the class we're attempting
        // to de-serialise isn't in the factories class loader
        val testFactory = testDefaultFactoryWithWhitelist()

        val deserializedObj = DeserializationInput(testFactory).deserialize(serialisedBytes)

        assertTrue(deserializedObj::class.java.getMethod("getA").invoke(deserializedObj)::class.java.isEnum)
        assertEquals("CCC",
                (deserializedObj::class.java.getMethod("getA").invoke(deserializedObj) as Enum<*>).name)
        assertTrue(deserializedObj::class.java.getMethod("getB").invoke(deserializedObj)::class.java.isEnum)
        assertEquals("EEE",
                (deserializedObj::class.java.getMethod("getB").invoke(deserializedObj) as Enum<*>).name)
        assertNull(deserializedObj::class.java.getMethod("getC").invoke(deserializedObj))
        assertEquals(testStr, deserializedObj::class.java.getMethod("getD").invoke(deserializedObj))
    }
}