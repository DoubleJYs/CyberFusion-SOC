package core

import (
	"fmt"
	"os"
	"path/filepath"
	"time"
)

type Logger struct {
	path string
}

func NewLogger(runtimeDir string) (Logger, error) {
	logDir := filepath.Join(runtimeDir, "logs")
	if err := os.MkdirAll(logDir, 0o700); err != nil {
		return Logger{}, err
	}
	return Logger{path: filepath.Join(logDir, "agent.log")}, nil
}

func (l Logger) Info(message string, fields ...any) {
	l.write("INFO", message, fields...)
}

func (l Logger) Error(message string, fields ...any) {
	l.write("ERROR", message, fields...)
}

func (l Logger) write(level string, message string, fields ...any) {
	if l.path == "" {
		return
	}
	file, err := os.OpenFile(l.path, os.O_CREATE|os.O_APPEND|os.O_WRONLY, 0o600)
	if err != nil {
		return
	}
	defer file.Close()
	_, _ = fmt.Fprintf(file, "%s level=%s message=%q", time.Now().UTC().Format(time.RFC3339), level, message)
	for i := 0; i+1 < len(fields); i += 2 {
		_, _ = fmt.Fprintf(file, " %v=%q", fields[i], fields[i+1])
	}
	_, _ = fmt.Fprintln(file)
}
