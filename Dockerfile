FROM anapsix/alpine-java:8_server-jre

# Copy java files from the repository
COPY . .

# Default environment variables
ENV DROPBOX_FOLDER_LOCAL="/dropbox"
ENV DROPBOX_FOLDER_REMOTE=""
ENV DROPBOX_OAUTH_TOKEN="null"

# Run the dropbox sync java program
CMD exec java -d64 -jar target/dropbox-sync-1.0-SNAPSHOT.jar "$DROPBOX_OAUTH_TOKEN" "$DROPBOX_FOLDER_LOCAL" "$DROPBOX_FOLDER_REMOTE"
