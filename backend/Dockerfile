FROM cgr.dev/chainguard/jre:latest
COPY target/ekspertbistand-api/app.jar app.jar
COPY target/ekspertbistand-api/lib lib

ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75"
CMD ["app.jar"]
