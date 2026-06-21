export const buildInfo = {
  appVersion: import.meta.env.VITE_APP_VERSION || 'dev',
  buildTime: import.meta.env.VITE_BUILD_TIME || new Date().toISOString(),
  gitCommit: import.meta.env.VITE_GIT_COMMIT || 'local',
}
