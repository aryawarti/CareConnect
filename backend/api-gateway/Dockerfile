# Runtime-only image: the jar is built on the host (or in CI) with
# 'mvn package', using the local repository cache — image builds are
# seconds and never touch the network for dependencies.
# (A multi-stage in-image build is the CI-server pattern; for local dev it
# re-downloaded the full dependency tree on every parent-pom change and was
# hostage to Docker Desktop DNS — see docs/operations/troubleshooting.md.)
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app
COPY api-gateway/target/*.jar app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]
