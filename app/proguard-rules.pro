# The launcher's own entry points are named in the manifest, which R8 already reads, so
# this file is about the things that are reached by name from somewhere it cannot see.

# WorkManager builds its workers reflectively from the class name it stored in its
# database. work-runtime ships a rule for this, and it is cheap to be explicit about ours.
-keep class com.maria.homelauncher.RotateWorker { <init>(...); }
-keep class com.maria.homelauncher.UpdateWorker { <init>(...); }

# Kept for the stack traces in a crash report to mean anything.
-keepattributes SourceFile,LineNumberTable
