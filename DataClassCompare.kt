// in build gradle:     implementation("org.jetbrains.kotlin:kotlin-reflect")


import kotlin.reflect.KClass
import kotlin.reflect.full.declaredMemberProperties

interface KeyProvider<T> {
    fun key(obj: T): Any
}

fun <T> compareCollections(
    expected: Iterable<T>,
    actual: Iterable<T>,
    keyProvider: KeyProvider<T>,
    path: String = ""
): List<String> {
    val expectedByKey = expected.associateBy(keyProvider::key)
    val actualByKey = actual.associateBy(keyProvider::key)
    val allKeys = (expectedByKey.keys + actualByKey.keys).toSet()

    val differences = mutableListOf<String>()

    for (key in allKeys) {
        val expectedItem = expectedByKey[key]
        val actualItem = actualByKey[key]
        val itemPath = "$path[$key]"
        differences += compareDataClasses(expectedItem, actualItem, itemPath)
    }

    return differences
}

fun compareDataClasses(
    obj1: Any?,
    obj2: Any?,
    path: String = "",
    keyProviders: Map<KClass<*>, KeyProvider<*>> = emptyMap()
): List<String> {
    if (obj1 == obj2) return emptyList()

    if (obj1 == null || obj2 == null) {
        return listOf("$path: $obj1 != $obj2")
    }

    val clazz1 = obj1::class
    val clazz2 = obj2::class

    if (clazz1 != clazz2) {
        return listOf("$path: Class mismatch (${clazz1.simpleName} != ${clazz2.simpleName})")
    }

    if (obj1 is Map<*, *> && obj2 is Map<*, *>) {
        return compareMaps(obj1, obj2, path)
    }

    if (obj1 is Iterable<*> && obj2 is Iterable<*>) {
        val elementType = obj1.firstOrNull()?.let { it::class }
            ?: error("Cannot determine element type for empty collections at $path")
        val keyProvider = keyProviders[elementType] as? KeyProvider<Any>
            ?: error("No KeyProvider for elements of type $elementType at $path")
        @Suppress("UNCHECKED_CAST")
        return compareCollections(
            obj1 as Iterable<Any>,
            obj2 as Iterable<Any>,
            keyProvider,
            path
        )
    }

    if (!clazz1.isData) {
        return listOf("$path: $obj1 != $obj2")
    }

    val differences = mutableListOf<String>()
    val properties = clazz1.declaredMemberProperties

    for (property in properties) {
        val value1 = property.getter.call(obj1)
        val value2 = property.getter.call(obj2)
        val newPath = if (path.isEmpty()) property.name else "$path.${property.name}"
        differences += compareDataClasses(value1, value2, newPath, keyProviders)
    }

    return differences
}
data class Car(val brand: String, val color: String, val engine:Int)
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
        listOf(Car("Ford", "Blue", 1000), Car("BMW", "Orange", 3000)),
        listOf(Car("Ford", "Blue", 1000), Car("BMW", "Orange", 3000)).toSet(),
        listOf(Car("Ford", "Blue", 1000), Car("BMW", "Orange", 3000)),
        sequenceOf(Car("Ford", "Blue", 1000), Car("BMW", "Orange", 3000)),
        listOf(Car("Ford", "Blue", 1000), Car("BMW", "Orange", 3000)).asIterable(),
        listOf(Car("Ford", "Blue", 1000), Car("BMW", "Orange", 3000)).associateBy { it.brand },
    )

    val person2 = Person(
        "Alice",
        31,
        Address("New York", 10002, 330.2),
        listOf(Car("Ford", "Blue", 1100), Car("BMW", "Red", 2750)),
        listOf(Car("Ford", "Blue", 1100), Car("BMW", "Red", 2750)).toSet(),
        listOf(Car("Ford", "Blue", 1100), Car("BMW", "Red", 2750)),
        sequenceOf(Car("Ford", "Blue", 1100), Car("BMW", "Red", 2750)),
        listOf(Car("Ford", "Blue", 1100), Car("BMW", "Red", 2750)).asIterable(),
        listOf(Car("Ford", "Blue", 1100), Car("BMW", "Red", 2750)).associateBy { it.brand },
    )

    val keyProviders: Map<KClass<*>, CarKeyProvider> = mapOf(
        Car::class to CarKeyProvider() // Provide a key for comparing Car objects
    )

    val differences = compareDataClasses(person1, person2, keyProviders = keyProviders)
    if (differences.isEmpty()) {
        println("The objects are identical.")
    } else {
        println("Differences found:")
        differences.forEach { println(it) }
    }
}
fun compareMaps(
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
}

// elements present in one and not the other