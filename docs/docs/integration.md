# Integration into Mods

## Gradle Setup

Put `guideme_version` into your `gradle.properties` and set it to the version you want to use.

You can find a list of versions on the [GitHub releases page](https://github.com/AppliedEnergistics/GuideME/releases).

```gradle
repositories {
  maven {
    name = "ModMaven"
    url = "https://modmaven.dev/"
    content {
      includeGroupAndSubgroups "appeng"
    }
  }
}

dependencies {
  compileOnly "org.appliedenergistics:guideme:${guideme_version}:api"
  runtimeOnly "org.appliedenergistics:guideme:${guideme_version}"
}
```

## Guide Creation

You can simply build guides in your Mod constructor using
the [GuideBuilder](https://guideme.appliedenergistics.org/javadoc/guideme/GuideBuilder.html) class.

```java
var guide = Guide.builder(ResourceLocation.parse("mymod:guide")).build();
```

## Guide Item

We recommend that you implement your own guide item to have it registered within your own mod
namespace. If you only want to depend on GuideME optionally, you can also use GuideMEs generic
guide item.
The [Guides class](https://guideme.appliedenergistics.org/javadoc/guideme/Guides.html#createGuideItem(net.minecraft.resources.ResourceLocation))
offers a utility method to construct an ItemStack for any given guide id.

You can customize the appearance of the generic guide item for your guide
using [GuideBuilder#itemSettings](https://guideme.appliedenergistics.org/javadoc/guideme/GuideBuilder.html#itemSettings(guideme.GuideItemSettings)).
