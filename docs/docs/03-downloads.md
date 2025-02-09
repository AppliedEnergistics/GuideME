import DownloadCurseforge from '@site/src/components/DownloadCurseforge';
import DownloadModrinth from '@site/src/components/DownloadModrinth';

# Downloads

## For Players

The mod can be downloaded from both Curseforge and Modrinth.

<p>
    <DownloadCurseforge />
</p>

<p>
    <DownloadModrinth />
</p>

## For Developers

### Releases

GuideME publishes Maven artifacts on both [Github Packages](https://github.com/AppliedEnergistics/GuideME/packages/2384035) 
and [Maven Central](https://central.sonatype.com/artifact/org.appliedenergistics/guideme).

![GuideME](https://img.shields.io/maven-central/v/org.appliedenergistics/guideme)

How you can use GuideME in your build script will depend on which build tool you use.

If you use ModDevGradle, this snippet will include GuideMEs API during compile-time, and its full package during runtime:

```gradle
repositories {
    mavenCentral() // You might already have this
}

dependencies {
    compileOnly 'org.appliedenergistics:guideme:<version>:api'
    runtimeOnly 'org.appliedenergistics:guideme:<version>'
}
```

### Snapshots

We publish every commit on the main branch to the Maven Central Snapshot repository.

You can see the [list of versions](https://central.sonatype.com/service/rest/repository/browse/maven-snapshots/org/appliedenergistics/guideme/).

To use these versions, you can use this repository in Gradle:

```gradle
maven {
    name = 'GuideME Snapshots'
    url = 'https://central.sonatype.com/repository/maven-snapshots/'
    content {
        includeModule('org.appliedenergistics', 'guideme')
    }
}
```
