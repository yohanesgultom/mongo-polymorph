package id.gultom.mongopolymorph

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.assertj.core.internal.bytebuddy.utility.RandomString
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.mongodb.core.MongoTemplate
import kotlin.random.Random

@SpringBootTest
class ApplicationTest {

    @Autowired
    private lateinit var jobScheduler: JobScheduler

    @Autowired
    private lateinit var mongoTemplate: MongoTemplate

    @Autowired
    private lateinit var logger: Logger

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @AfterEach
    fun after() {
        mongoTemplate.dropCollection("jobs")
    }

    @Test
    fun testFetchMarkJobs() {
        repeat(10) {
            if (Random.nextBoolean()) {
                mongoTemplate.insert(FooJob(
                    name = "foo-" + RandomString(10).nextString(),
                    foo = Random.nextInt()
                ))
            } else {
                mongoTemplate.insert(BarJob(
                    name = "bar-" + RandomString(10).nextString(),
                    bar = Random.nextInt()
                ))
            }
        }

        val fetchResultSet = runBlocking {
            val results: List<Deferred<List<Job>>> = (1..2).map {
                async {
                    val jobs = jobScheduler.fetchMarkJobs()
                    jobs.forEach {
                        logger.info(objectMapper.writeValueAsString(it))
                    }
                    jobs
                }
            }
            val resultSet = mutableSetOf<String>()
            results.forEach { deferred ->
                deferred.await()
                    // verify createdAt & updatedAt is populated
                    .filter { it.createdAt != null && it.updatedAt != null }
                    // verify no duplicate id
                    .forEach {
                        if (it.id != null) {
                            resultSet.add(it.id.toString())
                        }
                    }
            }
            resultSet
        }
        assertEquals(10, fetchResultSet.size)
    }
}