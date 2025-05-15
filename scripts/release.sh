#!/bin/bash

[ -z "$RELEASE_BUCKET" ] && { echo "RELEASE_BUCKET environment variable not set"; exit 1; }

VERSION=$(git describe --tags --abbrev=0)
HEAD_COMMIT=$(git rev-parse HEAD)
VERSION_COMMIT=$(git rev-parse $VERSION)

if [[ "$HEAD_COMMIT" != "$VERSION_COMMIT" ]]; then
    echo "error must specify a tag to build! expecting $VERSION_COMMIT for tag $VERSION but found HEAD $HEAD_COMMIT"
    exit 1
fi

echo "most recent tag: $VERSION (sha=$VERSION_COMMIT)"

export RELEASE_S3_URL="s3://$RELEASE_BUCKET/releases"

TEST_KEY="releases/aws/sdk/kotlin/gradle/build-support/$VERSION/build-support-$VERSION.jar"

if aws s3api head-object --bucket $RELEASE_BUCKET --key $TEST_KEY; then
    echo "failing release; $VERSION already exists!"
    exit 1
fi

echo "releasing version $VERSION"

./gradlew -Prelease.version=$VERSION publishAllPublicationsToReleaseRepository


