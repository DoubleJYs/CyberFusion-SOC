FROM python:3.11-slim
WORKDIR /app
COPY . /app
ENV PYTHONDONTWRITEBYTECODE=1
ENV MISP_RUNTIME_ENV_ROOT=/Environment
CMD ["sh", "-lc", "python3 tools/ioc-demo/validate_ioc_extension.py && python3 tools/ioc-demo/simulate_ioc_closed_loop.py"]
