plugins {
    id("com.aniyomi.extension")
}
apply(plugin = "com.aniyomi.extension")

ext {
    extName = "WebDAV"
    extClass = ".WebDAV"
    extVersionCode = 1
}

dependencies {
    implementation("com.github.thegrizzlylabs:sardine-android:0.9")
}
