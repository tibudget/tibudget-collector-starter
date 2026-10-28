# Preserve the public entry point, input fields, and names used through reflection.
-dontoptimize
-dontobfuscate
-dontshrink

# These optional annotations are not needed when the collector runs.
-dontwarn com.google.errorprone.annotations.*
