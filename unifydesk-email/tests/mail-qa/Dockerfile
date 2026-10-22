FROM eclipse-temurin:25-jdk
ENV DEBIAN_FRONTEND=noninteractive
RUN printf '#!/bin/sh\nexit 101\n' > /usr/sbin/policy-rc.d \
    && chmod 755 /usr/sbin/policy-rc.d \
    && apt-get update \
    && apt-get install -y --no-install-recommends clamav-daemon rspamd postfix-pgsql \
       postgresql-client curl python3 openssl age redis-server ca-certificates \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /workspace
COPY backend /workspace/backend
COPY tests /workspace/tests
COPY postfix /workspace/postfix
RUN find /workspace -type f \( -name '*.sh' -o -name mvnw \) -exec sed -i 's/\r$//' {} +
CMD ["bash", "/workspace/tests/mail-qa/run.sh"]
