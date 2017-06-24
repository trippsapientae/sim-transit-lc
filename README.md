# sim-transit-lc

This is a set of command-line tools that can produce synthetic transit light curves and 
transit videos. 

The optimization tool, lc-opt, can produce transit models by fitting
light curves. It can be configured using built-in methods, including
an experimental neural network, or by writing code.

This software was inspired by the remarkable light curves of KIC 8462852 (or Boyajian's star.)

#### Requirements

You must have [JavaSE 1.8+](http://www.oracle.com/technetwork/java/javase/downloads) in your PATH.

#### Installation

If JAVA_HOME is set, make sure it's pointing to the right version.

Every time you pull down an update, run:

    ./gradlew build

The `bin` directory should be added to your PATH.

#### Simulation tool

    lc-sim -o <output>.csv -video <output-video>.mov <sim-spec>.json

Examples of simulation specification JSON files can be found under `examples/sim`.

#### Optimization tool

    lc-opt -i <input>.csv -o <output>.csv -oi <output-image>.png <opt-spec>.json

Examples of optimization specification JSON files can be found under `examples/opt`.

The input CSV file must have two headers: `Timestamp` and `Flux`. `Time` and `n_flux` are
also accepted. Flux values must be normalized (between 0 and 1.)

#### Running the examples

Scripts are provided under `examples/bin` that execute a number of example runs.

#### Tutorials

None yet.