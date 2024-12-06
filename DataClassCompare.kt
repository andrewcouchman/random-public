import kotlin.reflect.KClass
import kotlin.reflect.full.declaredMemberProperties

interface KeyProvider<T> {
    fun key(obj: T): Any
}



fun <R> compareCollections(
    expected: Iterable<Any>,
    actual: Iterable<Any>,
    path: String,
    keyProviders: Map<KClass<*>, KeyProvider<*>>,
    differenceHandler: (String, String) -> R
): List<R> {
    val differences = mutableListOf<R>()

    // Determine the type of elements
    val firstNonNull = (expected.firstOrNull() ?: actual.firstOrNull())
    if (firstNonNull == null) {
        // Both collections are empty; they are equal
        return differences
    }

    val elementType = firstNonNull::class

    if (elementType == String::class || elementType.javaPrimitiveType != null) {
        // Handle primitives and strings
        differences += comparePrimitiveCollections(expected, actual, path, differenceHandler)
    } else {
        // Handle objects with KeyProviders
        val keyProvider = keyProviders[elementType] as? KeyProvider<Any>
            ?: error("No KeyProvider for elements of type $elementType at $path")
        differences += compareKeyedCollections(expected, actual, path, keyProvider, differenceHandler)
    }

    return differences
}

fun <R> comparePrimitiveCollections(
    expected: Iterable<Any>,
    actual: Iterable<Any>,
    path: String,
    differenceHandler: (String, String) -> R
): List<R> {
    val differences = mutableListOf<R>()

    val missingInActual = expected - actual
    val missingInExpected = actual - expected

    for (element in missingInActual) {
        differences += differenceHandler("$path", "Expected element not found: $element")
    }
    for (element in missingInExpected) {
        differences += differenceHandler("$path", "Extra element found: $element")
    }

    return differences
}

fun <T : Any, R> compareKeyedCollections(
    expected: Iterable<T>,
    actual: Iterable<T>,
    path: String,
    keyProvider: KeyProvider<T>,
    differenceHandler: (String, String) -> R
): List<R> {
    val differences = mutableListOf<R>()

    val expectedByKey = expected.associateBy(keyProvider::key)
    val actualByKey = actual.associateBy(keyProvider::key)
    val allKeys = (expectedByKey.keys + actualByKey.keys).toSet()

    for (key in allKeys) {
        val expectedItem = expectedByKey[key]
        val actualItem = actualByKey[key]
        val itemPath = "$path[$key]"

        if (expectedItem == null && actualItem != null) {
            differences += differenceHandler(itemPath, "Expected item not found, found $actualItem")
        } else if (actualItem == null && expectedItem != null) {
            differences += differenceHandler(itemPath, "Item is missing in actual collection, expected $expectedItem")
        } else if (expectedItem != null && actualItem != null) {
            differences += compareDataClasses(expectedItem, actualItem, itemPath, emptyMap(), differenceHandler)
        }
    }

    return differences
}

fun <R> compareDataClasses(
    obj1: Any?,
    obj2: Any?,
    path: String = "",
    keyProviders: Map<KClass<*>, KeyProvider<*>>,
    differenceHandler: (String, String) -> R
): List<R> {
    val differences = mutableListOf<R>()

    if (obj1 == null || obj2 == null) {
        if (obj1 != obj2) {
            differences += differenceHandler(path, "$obj1 != $obj2")
        }
    } else {
        val clazz1 = obj1::class
        val clazz2 = obj2::class

        if (clazz1 != clazz2) {
            differences += differenceHandler(path, "Class mismatch (${clazz1.simpleName} != ${clazz2.simpleName})")
        } else if (obj1 is Map<*, *> && obj2 is Map<*, *>) {
            // Convert maps to sets of pairs
            val expectedPairs = obj1.map { Pair(it.key, it.value) }
            val actualPairs = obj2.map { Pair(it.key, it.value) }

            @Suppress("UNCHECKED_CAST")
            differences += compareCollections(
                expectedPairs as List<Pair<Any, Any>>,
                actualPairs as List<Pair<Any, Any>>,
                path,
                keyProviders,
                differenceHandler
            )
        } else if (obj1 is Iterable<*> && obj2 is Iterable<*>) {
            @Suppress("UNCHECKED_CAST")
            differences += compareCollections(
                obj1 as Iterable<Any>,
                obj2 as Iterable<Any>,
                path,
                keyProviders,
                differenceHandler
            )
        } else if (clazz1.isData) {
            val properties = clazz1.declaredMemberProperties
            for (property in properties) {
                val value1 = property.getter.call(obj1)
                val value2 = property.getter.call(obj2)
                val newPath = if (path.isEmpty()) property.name else "$path.${property.name}"
                differences += compareDataClasses(value1, value2, newPath, keyProviders, differenceHandler)
            }
        } else if (obj1 != obj2) {
            differences += differenceHandler(path, "$obj1 != $obj2")
        }
    }

    return differences
}
data class Car(val brand: String, val color: String, val engine:Int, val price: List<Int>)
data class Address(val city: String, val zip: Int, val size: Double)
data class Person(val name: String, val age: Int, val address: Address, val carList: List<Car>,val carSet: Set<Car>,val carCollection: Collection<Car>,val carSequence: Sequence<Car>,val carIterable: Iterable<Car>, val carMap:Map<String, Car>)
class CarKeyProvider : KeyProvider<Car> {
    override fun key(obj: Car): Any = obj.brand // Use brand as the key for cars
}
fun main() {
    val person1 = Person(
        "Alice",
        30,
        Address("New York", 10001, 330.2),
        listOf(Car("Ford", "Blue", 1000, listOf(789)), Car("BMW", "Orange", 3000, listOf(123,345))),
        listOf(Car("Ford", "Blue", 1000, listOf(789)), Car("BMW", "Orange", 3000, listOf(123,345))).toSet(),
        listOf(Car("Ford", "Blue", 1000, listOf(789)), Car("BMW", "Orange", 3000, listOf(123,345))),
        sequenceOf(Car("Ford", "Blue", 1000, listOf(789)), Car("BMW", "Orange", 3000, listOf(123,345))),
        listOf(Car("Ford", "Blue", 1000, listOf(789)), Car("BMW", "Orange", 3000, listOf(123,345))).asIterable(),
        listOf(Car("Ford", "Blue", 1000, listOf(789)), Car("BMW", "Orange", 3000, listOf(123,345))).associateBy { it.brand },
    )

    val person2 = Person(
        "Alice",
        31,
        Address("New York", 10002, 330.2),
        listOf(Car("Ford", "Blue", 1100, listOf(999)), Car("BMW", "Red", 2750, listOf(123,345,678))),
        listOf(Car("Ford", "Blue", 1100, listOf(999)), Car("BMW", "Red", 2750, listOf(123,345,678))).toSet(),
        listOf(Car("Ford", "Blue", 1100, listOf(999)), Car("BMW", "Red", 2750, listOf(123,345,678))),
        sequenceOf(Car("Ford", "Blue", 1100, listOf(999)), Car("BMW", "Red", 2750, listOf(123,345,678))),
        listOf(Car("Ford", "Blue", 1100, listOf(999)), Car("BMW", "Red", 2750, listOf(123,345,678))).asIterable(),
        listOf(Car("Ford", "Blue", 1100, listOf(999)), Car("BMW", "Red", 2750, listOf(123,345,678))).associateBy { it.brand },
    )

    val keyProviders = mapOf(
        Car::class to object : KeyProvider<Car> {
            override fun key(obj: Car): Any = obj.brand
        },
        Pair::class to object : KeyProvider<Pair<String, Car>> {
            override fun key(obj: Pair<String, Car>): Any = obj.first
        }
    )


    val differences = compareDataClasses(
        person1,
        person2,
        keyProviders = keyProviders,
        differenceHandler = { path, message -> "$path $message" }
    )
    if (differences.isEmpty()) {
        println("The objects are identical.")
    } else {
        println("Differences found:")
        differences.forEach { println(it) }
    }
}
/*fun compareMaps(
    expected: Map<*, *>,
    actual: Map<*, *>,
    path: String = ""
): List<String> {
    val differences = mutableListOf<String>()

    // Collect all keys from both maps
    val allKeys = (expected.keys + actual.keys).toSet()

    for (key in allKeys) {
        val expectedValue = expected[key]
        val actualValue = actual[key]
        val keyPath = "$path[$key]"
        if (expectedValue == null && actualValue != null) {
            differences += "$keyPath: Expected key not found, found $actualValue"
        } else if (actualValue == null && expectedValue != null) {
            differences += "$keyPath: Key is missing in actual map, expected $expectedValue"
        } else {
            differences += compareDataClasses(expectedValue, actualValue, keyPath)
        }
    }

    return differences
}*/

// elements present in one and not the other