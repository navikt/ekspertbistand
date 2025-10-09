FROM cgr.dev/chainguard/jre:latest
COPY target/ekspertbistand-backend/app.jar app.jar
COPY target/ekspertbistand-backend/lib lib

ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75"
CMD ["app.jar"]
