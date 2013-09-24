export WHILEY_OPTIMISATION_LEVEL=2

export WHILEY_GPGPU_FORCE_CPU=NO
echo "Running GPU"
./runbenchmarks.sh GPU

export WHILEY_GPGPU_FORCE_CPU=YES
echo "Running CPU"
./runbenchmarks.sh CPU