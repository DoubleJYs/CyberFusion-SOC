package core

import "time"

type RateLimiter interface {
	Wait()
}

type tickerLimiter struct {
	interval time.Duration
	last     time.Time
}

func NewRateLimiter(perSecond int) RateLimiter {
	if perSecond <= 0 {
		perSecond = 1
	}
	return &tickerLimiter{interval: time.Second / time.Duration(perSecond)}
}

func (l *tickerLimiter) Wait() {
	if l.last.IsZero() {
		l.last = time.Now()
		return
	}
	next := l.last.Add(l.interval)
	if sleep := time.Until(next); sleep > 0 {
		time.Sleep(sleep)
	}
	l.last = time.Now()
}
