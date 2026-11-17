FROM golang:1.24-alpine AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY *.go ./
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /hlth-server .

# zsign — подпись IPA для обновлений iOS (версия закреплена коммитом).
FROM alpine:3.20 AS zsign
RUN apk add --no-cache g++ make openssl-dev pkgconf git
RUN git clone https://github.com/zhlynn/zsign.git /zsign && cd /zsign && git checkout 9299685bab7ef99827e06e5dfda93af747386590
RUN cd /zsign/build/linux && make -j"$(nproc)" VERSION=pinned && cp /zsign/bin/zsign /usr/local/bin/zsign

FROM alpine:3.20
RUN apk add --no-cache libstdc++ libssl3 libcrypto3 ca-certificates \
    && adduser -D -H -u 10001 app && mkdir -p /data && chown app /data
USER app
COPY --from=build /hlth-server /hlth-server
COPY --from=zsign /usr/local/bin/zsign /usr/local/bin/zsign
ENV ADDR=:8080 DB_PATH=/data/hlth.db
EXPOSE 8080
ENTRYPOINT ["/hlth-server"]
