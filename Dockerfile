FROM adoptopenjdk:11-jre-hotspot

# # Run application as non-root user to help to mitigate some risks
# RUN groupadd -r spring && useradd -r spring -g spring && \
#  usermod -a -G uucp spring && \
#  usermod -a -G dialout spring && \
#  usermod -a -G tty spring
# # usermod -a -G lock spring && \
# <https://stackoverflow.com/questions/60363357/serial-port-access-fails-when-java-app-is-dockerized>
# USER spring:spring

COPY /Java/tempctrl/build/libs/*.jar app.jar

EXPOSE 80

# Override certain properties in the SpringBoot app's application.properties
ENV spring.resources.staticLocations classpath:/static/, file:/staticContent
ENV dataDir /staticContent/data

# To this list will need to be dynamically appended: --pwdhash.guest=... --pwdhash.admin=...
# These can simply be appended after `docker run <image name>`, as described here:
# <https://www.manifold.co/blog/arguments-and-variables-in-docker-94746642f64b>
ENTRYPOINT ["java", "-jar", "/app.jar"]

# End