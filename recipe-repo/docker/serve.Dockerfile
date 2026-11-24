# Generic recipe server: serves YOUR recipes without building them into the image. Published as
# ghcr.io/brunoaclopes/mcc-recipe-server. Either mount a recipe folder at /recipes, or set MCC_GIT_URL and it clones
# your recipe repository and pulls it every MCC_REINDEX_SECONDS (60). Each time it validates recipes/*.json with
# this template's tools and writes index.json; a broken recipe keeps the last good index.
#
#   docker run -d -p 8088:80 -v /path/to/my-recipes:/recipes:ro ghcr.io/brunoaclopes/mcc-recipe-server
#   docker run -d -p 8088:80 -e MCC_GIT_URL=git@github.com:<you>/<recipes>.git -v ./ssh:/ssh:ro ghcr.io/...
#
# Build it yourself from recipe-repo/:  docker build -f docker/serve.Dockerfile -t mcc-recipe-server .
FROM nginx:1.27-alpine
RUN apk add --no-cache python3 git openssh-client
COPY converters/ /opt/mcc/converters/
COPY tools/ /opt/mcc/tools/
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY docker/40-mcc-index.sh /docker-entrypoint.d/40-mcc-index.sh
RUN chmod +x /docker-entrypoint.d/40-mcc-index.sh && rm -f /usr/share/nginx/html/*.html
ENV MCC_RECIPES=/recipes MCC_REINDEX_SECONDS=60
EXPOSE 80
HEALTHCHECK --interval=60s --timeout=5s CMD wget -q -O /dev/null http://127.0.0.1/index.json || exit 1
