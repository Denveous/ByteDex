FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
ARG NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
ENV NEXT_PUBLIC_API_BASE_URL=${NEXT_PUBLIC_API_BASE_URL}
COPY . .
RUN chmod +x gradlew \
 && ./gradlew --no-daemon --console=plain :frontend:nextBuild

FROM node:22-slim
WORKDIR /app
ENV NODE_ENV=production
COPY --from=build /src/frontend/build/next ./build/next
COPY --from=build /src/frontend/node_modules ./node_modules
COPY --from=build /src/frontend/package.json /src/frontend/next.config.mjs ./
EXPOSE 3000
CMD ["node_modules/.bin/next", "start", "-H", "0.0.0.0", "-p", "3000"]
