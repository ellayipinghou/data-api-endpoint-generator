# Build React app
FROM node:24 AS builder
WORKDIR /app
# copy package files
COPY package*.json ./
RUN npm install
# copy rest of the source code
COPY . .
RUN npm run build

# Serve React app
FROM nginx:alpine
# Vite produces dist/, Nginx's standard Docker image is configured to serve static files from /usr/share/nginx/html
COPY --from=builder /app/dist /usr/share/nginx/html
# Replace Nginx's default configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf
# port that nginx listens on
EXPOSE 80
# nginx's official image already has its own startup command, so it will start Nginx automatically