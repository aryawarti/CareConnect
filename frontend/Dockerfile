# Runtime-only image (same pattern as the backend): build on the host with
# `npm run build`, then serve the static bundle via nginx.
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY dist/careconnect-web/browser /usr/share/nginx/html
