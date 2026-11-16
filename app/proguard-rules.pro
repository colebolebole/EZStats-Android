# The app's own code is kept exactly as written: it is small, and nothing in it is worth the risk of R8
# renaming or removing something that is used indirectly. R8 still shrinks and optimizes every library,
# which is where the size is (the extended icon set alone is megabytes).
-keep class com.ezstats.android.** { *; }
