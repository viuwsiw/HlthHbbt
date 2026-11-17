FROM golang:1.24-alpine AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY *.go ./
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /hlth-server .

FROM alpine:3.20
RUN adduser -D -H -u 10001 app && mkdir -p /data && chown app /data
USER app
COPY --from=build /hlth-server /hlth-server
ENV ADDR=:8080 DB_PATH=/data/hlth.db
EXPOSE 8080
ENTRYPOINT ["/hlth-server"]
