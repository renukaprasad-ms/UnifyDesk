FROM golang:1.27-alpine AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/monitor ./cmd/server
FROM alpine:3.24
RUN apk add --no-cache ca-certificates && addgroup -g 10001 monitor && adduser -D -u 10001 -G monitor monitor && mkdir -p /var/lib/unifydesk-monitor && chown monitor:monitor /var/lib/unifydesk-monitor
COPY --from=build /out/monitor /usr/local/bin/monitor
USER 10001:10001
EXPOSE 8080
STOPSIGNAL SIGTERM
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s CMD wget -q -O /dev/null http://127.0.0.1:8080/health || exit 1
ENTRYPOINT ["/usr/local/bin/monitor"]
