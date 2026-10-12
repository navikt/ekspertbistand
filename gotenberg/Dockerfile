FROM gotenberg/gotenberg:8.37.0

USER root

# Gotenberg-releaser henger etter Debians pakkeoppdateringer.
# Workflowene bygger med no_cache, ellers gjenbrukes laget og nye pakker hentes aldri.
RUN apt-get update -qq \
    && DEBIAN_FRONTEND=noninteractive apt-get upgrade -y -qq --with-new-pkgs \
    && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/* \
    && bash /opt/gotenberg/capture-version.sh "$GOTENBERG_VERSIONS_DIR_PATH" chromium "$CHROMIUM_BIN_PATH" --version \
    && dpkg-query -W chromium chromium-common

USER gotenberg
