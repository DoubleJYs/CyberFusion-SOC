FROM node:22-bookworm
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends python3 && rm -rf /var/lib/apt/lists/*
COPY . /app
ENV PYTHONDONTWRITEBYTECODE=1
ENV CYBERCHEF_ENV_ROOT=/Environment
EXPOSE 18080
CMD ["sh", "-lc", "npm install && npm run build && python3 -m http.server 18080 -d build/prod --bind 0.0.0.0"]
