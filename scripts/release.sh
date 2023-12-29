#!/bin/bash

[ -z "$RELEASE_BUCKET" ] && { echo "RELEASE_BUCKET environment variable not set"; exit 1; }
[ -z "$PUBLISHING_ROLE_ARN" ] && { echo "PUBLISHING_ROLE_ARN environment variable not set"; exit 1; }


if [ -z "$VERSION" ]; then
    echo "no explicit version set, using latest reachable git tag"
    VERSION=$(git describe --tags --abbrev=0)
fi

TEST_KEY="releases/aws/sdk/kotlin/build-plugins/$VERSION/build-plugins-$VERSION.jar"

if aws s3api head-object --bucket $RELEASE_BUCKET --key $TEST_KEY; then
    echo "failing release; $VERSION already exists!"
    exit 1
fi

echo "releasing version $VERSION"

SESSION_CREDS=$(aws sts assume-role --role-arn $PUBLISHING_ROLE_ARN --role-session-name publish-aws-kotlin-repo-tools)
export AWS_ACCESS_KEY_ID=$(echo "${SESSION_CREDS}" | jq -r '.Credentials.AccessKeyId')
export AWS_SECRET_ACCESS_KEY=$(echo "${SESSION_CREDS}" | jq -r '.Credentials.SecretAccessKey')
export AWS_SESSION_TOKEN=$(echo "${SESSION_CREDS}" | jq -r '.Credentials.SessionToken')
export RELEASE_S3_URL="s3://$RELEASE_BUCKET/releases"

./gradlew -Prelease.version=$VERSION publishAllPublicationsToReleaseRepository


