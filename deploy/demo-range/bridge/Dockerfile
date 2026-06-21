FROM python:3.12-alpine

WORKDIR /app
COPY demo_event_bridge.py /app/demo_event_bridge.py

USER nobody
ENTRYPOINT ["python", "/app/demo_event_bridge.py"]
