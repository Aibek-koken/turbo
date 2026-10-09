FROM python:3.13-alpine

RUN addgroup -S app \
    && adduser -S app -G app

WORKDIR /app
COPY server.py /app/server.py

USER app:app
EXPOSE 8089

CMD ["python", "/app/server.py"]
