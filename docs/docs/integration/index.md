
# Integration into Mods

## Gradle Setup

Put `guideme_version` into your `gradle.properties` and set it to the version you want to use.

You can find a list of versions on the [GitHub releases page](https://github.com/AppliedEnergistics/GuideME/releases). 

```gradle
repositories {
  maven {
    name = "ModMaven
    url = "https://modmaven.dev/"
    content {
      includeGroupAndSubgroups "appeng"
    }
  }
}

dependencies {
  compileOnly "appeng:guideme:${guideme_version}:api"
  runtimeOnly "appeng:guideme:${guideme_version}"
}
```

## Guide Creation

In your Mod constructor
