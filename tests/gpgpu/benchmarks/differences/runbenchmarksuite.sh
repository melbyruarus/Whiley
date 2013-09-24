export WHILEY_OPTIMISATION_LEVEL=1
export WHILEY_GPGPU_FORCE_CPU=NO
echo "Running GPU & 1"
./runbenchmarks.sh GPU+Optimisations=1

export WHILEY_OPTIMISATION_LEVEL=2
export WHILEY_GPGPU_FORCE_CPU=NO
echo "Running GPU & 2"
./runbenchmarks.sh GPU+Optimisations=2

export WHILEY_OPTIMISATION_LEVEL=1
export WHILEY_GPGPU_FORCE_CPU=YES
echo "Running CPU & 1"
./runbenchmarks.sh CPU+Optimisations=1

export WHILEY_OPTIMISATION_LEVEL=2
export WHILEY_GPGPU_FORCE_CPU=YES
echo "Running CPU & 2"
./runbenchmarks.sh CPU+Optimisations=2