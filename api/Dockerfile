FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
RUN apt-get update \
 && apt-get install -y --no-install-recommends postgresql \
 && rm -rf /var/lib/apt/lists/*
COPY . .
RUN chmod +x gradlew \
 && service postgresql start \
 && su postgres -c "psql --command \"CREATE ROLE bytedex LOGIN SUPERUSER PASSWORD 'bytedex_local_dev';\"" \
 && su postgres -c "createdb --owner bytedex bytedex" \
 && ./gradlew --no-daemon --console=plain :api:jooqCodegen :api:installDist

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/api/build/install/api ./
EXPOSE 8080
CMD ["/app/bin/api"]
