# Stage 1: Build React application
FROM node:22-alpine AS client-builder

WORKDIR /build

RUN --mount=type=bind,source=client,target=. \
    --mount=type=cache,target=/root/.npm \
    npm install --legacy-peer-deps && \
    npm run build

# Stage 2: Nginx runtime
FROM nginx:alpine

# Remove default nginx configuration
RUN rm /etc/nginx/conf.d/default.conf

# Copy custom nginx configuration
COPY nginx/nginx.conf /etc/nginx/conf.d/winauth.conf

# Copy React build artifacts from builder stage
COPY --from=client-builder /build/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
