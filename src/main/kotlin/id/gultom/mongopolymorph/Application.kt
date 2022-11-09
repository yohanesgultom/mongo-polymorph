package id.gultom.mongopolymorph

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InjectionPoint
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Scope
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.config.EnableMongoAuditing
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Query.query
import org.springframework.data.mongodb.core.query.Update.update
import org.springframework.data.support.PageableExecutionUtils
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.time.Instant

@Suppress("unused")
@EnableMongoAuditing
@EnableScheduling
@SpringBootApplication
class Application {

    @Bean
    @Scope("prototype")
    fun logger(injectionPoint: InjectionPoint): Logger {
        return LoggerFactory.getLogger(
            injectionPoint.methodParameter?.containingClass
                ?: injectionPoint.field?.declaringClass
        )
    }
}

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}

@Suppress("unused")
@RequestMapping("jobs")
@RestController
class JobController(private val mongoTemplate: MongoTemplate) {

    @GetMapping
    fun index(pageable: Pageable): Page<Job> {
        val query = Query().with(pageable)
        val documents =
            mongoTemplate.find(query, Job::class.java) // the magic is this query will include all subclasses!
        return PageableExecutionUtils.getPage(documents, pageable) {
            mongoTemplate.count(Query.of(query).limit(-1).skip(-1), Job::class.java)
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody jobRequest: JobRequest): Job {
        val job = when (jobRequest.type) {
            JobType.Foo -> FooJob(name = jobRequest.name, foo = jobRequest.value)
            JobType.Bar -> BarJob(name = jobRequest.name, bar = jobRequest.value)
        }
        return mongoTemplate.insert(job)
    }
}

@Suppress("unused")
@Component
class JobScheduler(
    private val logger: Logger,
    private val mongoTemplate: MongoTemplate
) {

    @Transactional
    suspend fun fetchMarkJobs(): List<Job> {
        val jobs = mongoTemplate.find(query(where("status").`is`(JobStatus.Pending)).limit(5), Job::class.java)
        mongoTemplate.updateMulti(
            query(where("_id").`in`(jobs.map { it.id })),
            update("status", JobStatus.Processing),
            Job::class.java
        )
        return jobs
    }

    suspend fun processJob(job: Job) {
        // do some process
        when (job) {
            is FooJob -> logger.info("Processing ${job.id} ${job.name} is a Foo Job with foo ${job.foo}")
            is BarJob -> logger.info("Processing ${job.id} ${job.name} is a Bar Job with bar ${job.bar}")
        }
        // update status
        mongoTemplate.updateFirst(
            query(where("_id").`is`(job.id)),
            update("status", listOf(JobStatus.Success, JobStatus.Failed).random()),
            Job::class.java
        )
    }

    @Scheduled(cron = "\${app.job.scheduled.cron:-}")
    fun runJobs() {
        logger.info("Running jobs..")
        runBlocking {
            launch {
                fetchMarkJobs().forEach {
                    processJob(it)
                }
            }
        }
    }
}

data class JobRequest(val name: String, val value: Int, val type: JobType)

enum class JobType {
    Foo,
    Bar
}

enum class JobStatus {
    Pending,
    Processing,
    Success,
    Failed
}

@Suppress("unused")
@Document(collection = "jobs")
open class Job(
    @Id var id: String? = null,
    val name: String,
    val status: JobStatus = JobStatus.Pending,
    @CreatedDate var createdAt: Instant? = null,
    @LastModifiedDate var updatedAt: Instant? = null,
)

@Document(collection = "jobs")
class FooJob(
    id: String? = null,
    name: String,
    status: JobStatus = JobStatus.Pending,
    val foo: Int
) : Job(id, name, status)

@Document(collection = "jobs")
class BarJob(
    id: String? = null,
    name: String,
    status: JobStatus = JobStatus.Pending,
    val bar: Int
) : Job(id, name, status)