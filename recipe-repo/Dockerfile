# Recipe server for the Monsieur Cuisine app: validates the recipes, rebuilds index.json and serves
# index.json, recipes/ and images/ over HTTP. Build: docker compose up -d --build
FROM python:3.15-alpine AS build
WORKDIR /repo
COPY converters/ converters/
COPY tools/ tools/
COPY recipes/ recipes/
COPY images/ images/
# fails the build if any recipe is invalid
RUN python3 tools/build_index.py /repo

FROM nginx:1.29-alpine
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /repo/index.json /usr/share/nginx/html/index.json
COPY --from=build /repo/recipes/ /usr/share/nginx/html/recipes/
COPY --from=build /repo/images/ /usr/share/nginx/html/images/
EXPOSE 80
HEALTHCHECK --interval=60s --timeout=5s CMD wget -q -O /dev/null http://127.0.0.1/index.json || exit 1
