# Room constructs WorkManager's generated database by reflection during app startup.
# R8 full mode must retain its public no-argument constructor even though no
# direct constructor invocation exists in application bytecode.
-keep class androidx.work.impl.WorkDatabase_Impl {
    public <init>();
}
