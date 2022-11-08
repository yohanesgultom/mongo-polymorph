# mongo-polymorph

MongoDB polymorphism poc with Spring Boot + Kotlin

## Prerequisistes

* JDK 17
* Maven
* Docker (for MongoDB)

## What do we try?
- Polymorphism of `Job` document (can be `FooJob` or `BarJob`)
- Continuous background job using `@Scheduled`
- Spring data MongoDB transaction support using `@Transactional`
- Verification of transaction behaviour using coroutines

## Running

Run mongodb
```bash
docker run --name mongodb \
-e MONGO_INITDB_ROOT_USERNAME=admin \
-e MONGO_INITDB_ROOT_PASSWORD=admin \
-e MONGO_INITDB_DATABASE=test_db \
-p 27017:27017 \
-d mongo:latest
```

Run test
```bash
mvn clean test
```

Run API
```bash
mvn clean spring-boot:run
```