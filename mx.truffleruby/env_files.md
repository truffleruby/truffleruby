Here is how the various env files relate to each other:
* `jvm`: standalone with all tools
  * `jvm-ce`: + GraalVM Community Compiler
    * `jvm-ce-libgraal`: + libgraal
    * `native`: + librubyvm + `Truffle SVM Macro`
      * `native-host-inlining`: + `TruffleHostInliningPrintExplored`
      * `native-profiling`: + `-H:-DeleteLocalSymbols`
  * `jvm-ee`: + Oracle GraalVM Compiler + libgraal
    * `native-ee`: + librubyvm + Native Image G1
      * `native-ee-host-inlining`: + `TruffleHostInliningPrintExplored`
      * `native-ee-aux`: + `AuxiliaryEngineCache`, - Native Image G1 (currently incompatible)
