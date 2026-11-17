FROM golang:1.24-alpine AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY *.go ./
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /hlth-server .

# zsign — подпись IPA для обновлений iOS (релиз v1.1.3, официальная сборка под Debian).
FROM debian:bookworm-slim AS zsign
RUN apt-get update && apt-get install -y --no-install-recommends git g++ make pkg-config libssl-dev ca-certificates && rm -rf /var/lib/apt/lists/*
RUN git clone https://github.com/zhlynn/zsign.git /zsign && cd /zsign && git checkout v1.1.3
RUN cd /zsign/build/linux && make clean && make -j"$(nproc)" VERSION=1.1.3 && cp /zsign/bin/zsign /usr/local/bin/zsign

FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y --no-install-recommends libssl3 ca-certificates && rm -rf /var/lib/apt/lists/* \
    && useradd -M -u 10001 app && mkdir -p /data && chown app /data
USER app
COPY --from=build /hlth-server /hlth-server
COPY --from=zsign /usr/local/bin/zsign /usr/local/bin/zsign
ENV ADDR=:8080 DB_PATH=/data/hlth.db
EXPOSE 8080
ENTRYPOINT ["/hlth-server"]
