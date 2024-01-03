## AWS Kotlin Repository Tools

This repository contains shared tooling for AWS Kotlin repositories:

* `awslabs/aws-sdk-kotlin`
* `awslabs/smithy-kotlin`
* `awslabs/aws-crt-kotlin`

It contains shared custom Gradle plugins and build logic used to build and release those repositories.

## Release

The plugins in this repository are not generally applicable and as such are not published to the 
[Gradle plugin portal](https://plugins.gradle.org/). Instead, they are published to an S3 bucket
hosted by the SDK team and fronted by a CloudFront distribution to create a "private" maven repository that
hosts the released versions of these plugins/artifacts.

The release logic is entirely contained in `scripts/release.sh`. By default, it will use the latest git tag for the
version that is being released.

To cut a new release:

1. Create a new tag, e.g. `git tag x.y.z`
2. Push the tag up `git push origin x.y.z`
3. Kick off the release job hosted in the shared tools account (e.g. `publish-aws-kotlin-repo-tools`) specifying the tag as the source version to build.


## Security

See [CONTRIBUTING](CONTRIBUTING.md#security-issue-notifications) for more information.

## License

This project is licensed under the Apache-2.0 License.

