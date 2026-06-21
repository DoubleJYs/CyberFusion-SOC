FROM node:22-alpine

WORKDIR /app
COPY server.js /app/server.js

ENV NODE_ENV=production \
    HOST=0.0.0.0 \
    PORT=3000 \
    DEMO_PORTAL_MODE=protected \
    DEMO_PORTAL_LOG_FILE=/runtime/logs/demo-target/demo-events.jsonl

EXPOSE 3000

CMD ["node", "/app/server.js"]
